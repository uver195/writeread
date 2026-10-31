"""Patch MainActivity.java and build.gradle.kts for English letter support.

Run from project root: python tools/patch_android_english.py
"""
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
MAIN_ACTIVITY = ROOT / "android" / "app" / "src" / "main" / "java" / "com" / "shiyu" / "hccr" / "MainActivity.java"
BUILD_GRADLE = ROOT / "android" / "app" / "build.gradle.kts"

def patch_file(path, replacements, label):
    text = path.read_text(encoding="utf-8")
    for old, new in replacements:
        if old not in text:
            print(f"WARNING [{label}]: pattern not found:\n  {old[:80]}...")
            continue
        count = text.count(old)
        if count > 1:
            print(f"WARNING [{label}]: pattern found {count} times, replacing all")
        text = text.replace(old, new, 1)
        print(f"OK [{label}]: replaced ({len(new)-len(old)} chars diff)")
    path.write_text(text, encoding="utf-8")
    print(f"saved: {path}")


# --- MainActivity.java ---
main_patches = []

# 1. Add English model constants after WORDS_JSON
main_patches.append((
    '    private static final String WORDS_JSON = "words.json";',
    '    private static final String WORDS_JSON = "words.json";\n'
    '    private static final String NCNN_PARAM_EN = "english.ncnn.param";\n'
    '    private static final String NCNN_BIN_EN   = "english.ncnn.bin";\n'
    '    private static final String CHARSET_EN    = "english_charset.json";\n'
    '    private static final float CONFIDENCE_THRESHOLD = 0.35f;'
))

# 2. Add englishRecognizer field
main_patches.append((
    '    private HCCRRecognizer recognizer;',
    '    private HCCRRecognizer recognizer;\n'
    '    private HCCRRecognizer englishRecognizer;'
))

# 3. Load English model after Chinese model in onCreate
main_patches.append((
    '            Log.i(TAG, "offline recognizer ready");',
    '            Log.i(TAG, "offline recognizer ready");\n'
    '            // Load English letter model (optional, non-fatal if missing)\n'
    '            try {\n'
    '                englishRecognizer = new HCCRRecognizer(getAssets(),\n'
    '                        NCNN_PARAM_EN, NCNN_BIN_EN, CHARSET_EN);\n'
    '                Log.i(TAG, "english recognizer ready");\n'
    '            } catch (IOException | RuntimeException ex) {\n'
    '                Log.w(TAG, "english model not loaded (non-fatal)", ex);\n'
    '            }'
))

# 4. Close English recognizer in onDestroy
main_patches.append((
    '        if (recognizer != null) recognizer.close();',
    '        if (recognizer != null) recognizer.close();\n'
    '        if (englishRecognizer != null) englishRecognizer.close();'
))

# 5. Replace recognize() method with dual-model cascade
old_recognize = '''    private void recognize() {
        pendingRecognize = null;
        if (recognizer == null || handwriting.isEmpty()) return;
        try {
            Bitmap bmp = handwriting.renderToBitmap();
            float[] input = preprocessToFloat64x64(bmp);
            long t0 = System.nanoTime();
            HCCRRecognizer.Result[] r = recognizer.predict(input, TOPK);
            long ms = (System.nanoTime() - t0) / 1_000_000;
            if (r.length == 0) {
                Log.w(TAG, "recognize returned no result");
                return;
            }
            String ch = r[0].text;
            String phrase = buildPhrase(ch);
            recognizedStrokeCount = handwriting.getStrokeCount();
            Log.i(TAG, "top1=" + ch + " prob=" + r[0].probability + " ms=" + ms
                    + " speak=" + phrase);
            showStatus(phrase);
            speak(phrase);
            scheduleClear();
        } catch (Throwable e) {
            Log.e(TAG, "recognize failed", e);
            showStatus("识别失败: " + e.getMessage(), 2000);
        }
    }'''

new_recognize = '''    private void recognize() {
        pendingRecognize = null;
        if (recognizer == null || handwriting.isEmpty()) return;
        try {
            Bitmap bmp = handwriting.renderToBitmap();
            float[] input = preprocessToFloat64x64(bmp);
            long t0 = System.nanoTime();
            HCCRRecognizer.Result[] cn = recognizer.predict(input, TOPK);
            long ms = (System.nanoTime() - t0) / 1_000_000;
            if (cn.length == 0) {
                Log.w(TAG, "recognize returned no result");
                return;
            }
            float cnProb = cn[0].probability;
            String cnChar = cn[0].text;

            // English model cascade: run if available
            float enProb = 0f;
            String enChar = null;
            if (englishRecognizer != null) {
                HCCRRecognizer.Result[] en = englishRecognizer.predict(input, TOPK);
                if (en.length > 0) {
                    enProb = en[0].probability;
                    enChar = en[0].text;
                }
            }

            // Decision: pick higher confidence, with slight bias toward Chinese
            boolean useEnglish = enChar != null
                    && enProb > CONFIDENCE_THRESHOLD
                    && enProb > cnProb + 0.1f;
            String phrase;
            if (useEnglish) {
                phrase = "字母 " + enChar;
                Log.i(TAG, "EN top1=" + enChar + " prob=" + enProb
                        + " (CN=" + cnChar + "/" + cnProb + ") ms=" + ms);
            } else if (cnProb < CONFIDENCE_THRESHOLD && enProb < CONFIDENCE_THRESHOLD) {
                phrase = "没看清，请重写";
                Log.i(TAG, "low confidence: CN=" + cnProb + " EN=" + enProb);
                showStatus(phrase, 2000);
                return;
            } else {
                phrase = buildPhrase(cnChar);
                Log.i(TAG, "CN top1=" + cnChar + " prob=" + cnProb
                        + " EN=" + (enChar != null ? enChar + "/" + enProb : "n/a")
                        + " ms=" + ms + " speak=" + phrase);
            }
            recognizedStrokeCount = handwriting.getStrokeCount();
            showStatus(phrase);
            speak(phrase);
            scheduleClear();
        } catch (Throwable e) {
            Log.e(TAG, "recognize failed", e);
            showStatus("识别失败: " + e.getMessage(), 2000);
        }
    }'''

main_patches.append((old_recognize, new_recognize))

patch_file(MAIN_ACTIVITY, main_patches, "MainActivity")

# --- build.gradle.kts ---
gradle_patches = [
    ('versionCode = 5', 'versionCode = 6'),
    ('versionName = "1.2.0"', 'versionName = "1.3.0"'),
]
patch_file(BUILD_GRADLE, gradle_patches, "build.gradle.kts")

print("\nAll patches applied.")