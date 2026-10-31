package com.shiyu.hccr;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.TextView;
import android.widget.Toast;

import com.shiyu.handwritten.runtime.HCCRRecognizer;

import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * 写读板：离线全屏手写练字 + 语音朗读工具。
 *
 * 通过 Intent extra "mode" 进入：
 *   - "chinese"：仅识别中文汉字（写英文会提示去英文学习，不会误读成汉字）
 *   - "english"：仅识别英文字母 A–Z（写汉字会提示去汉字学习）
 * 两个模式各自独立推理，不再像旧版那样中英文级联猜字。
 *
 * 识别后按「XX的XX」句式组词并朗读（汉字模式）或朗读「字母 X」（英文模式）。
 * 文本转语音的语音可在设置页为「中文朗读」「英文朗读」分别指定；不指定则跟随系统 TTS。
 */
public class MainActivity extends Activity implements HandwritingView.StrokeListener {

    private static final String TAG = "WriteRead";
    private static final String NCNN_PARAM = "mbv2_aug.int8.ncnn.param";
    private static final String NCNN_BIN   = "mbv2_aug.int8.ncnn.bin";
    private static final String CHARSET    = "charset.json";
    private static final String WORDS_JSON = "words.json";
    private static final String NCNN_PARAM_EN = "english.ncnn.param";
    private static final String NCNN_BIN_EN   = "english.ncnn.bin";
    private static final String CHARSET_EN    = "english_charset.json";
    private static final float CONFIDENCE_THRESHOLD = 0.45f;
    private static final int TOPK = 1;                        // 只取 top-1 用于朗读拼句
    // 停笔静默期：连续书写时不在每次抬手识别，而是等用户停笔 SETTLE_MS 毫秒无新笔画后再识别整字
    private static final long SETTLE_MS = 1500;
    private static final long CLEAR_MS = 2500;   // 识别后兜底清空延时：不依赖 TTS onDone 回调（部分机型不可靠）

    private String mode = HomeActivity.MODE_CHINESE;

    private HandwritingView handwriting;
    private TextView statusOverlay;
    private HCCRRecognizer recognizer;
    private HCCRRecognizer englishRecognizer;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable pendingRecognize;

    // 组词词典：汉字 → 以该字开头的常用词，用于拼句 “W的X”（assets/words.json）
    private Map<String, String> wordMap = new HashMap<>();

    // 统一的本地 TTS 管理（单例，使用手机自带语音库，不联网）
    private TtsManager ttsMgr;

    // 朗读完成自动清空：记录本次识别时的笔画数，防止误清用户新写的笔画
    private int recognizedStrokeCount = -1;
    private Runnable pendingClear;   // 兜底清空任务（onCreate 中绑定 clearCanvasIfUnchanged）

    private final Runnable hideOverlay = () ->
            statusOverlay.animate().alpha(0f).setDuration(400)
                    .withEndAction(() -> statusOverlay.setVisibility(View.INVISIBLE)).start();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        handwriting = findViewById(R.id.handwriting);
        statusOverlay = findViewById(R.id.status_overlay);
        handwriting.setStrokeListener(this);
        pendingClear = this::clearCanvasIfUnchanged;

        String m = getIntent().getStringExtra(HomeActivity.EXTRA_MODE);
        if (m != null) mode = m;

        loadWordsFromAssets();
        initTts();

        showStatus(HomeActivity.MODE_CHINESE.equals(mode)
                ? getString(R.string.mode_ready_chinese)
                : getString(R.string.mode_ready_english));

        // 加载本地 NCNN 模型（assets 内的 INT8 量化模型，纯本地推理）
        try {
            HCCRRecognizer.loadNativeLibrary("hccr_jni");
            recognizer = new HCCRRecognizer(getAssets(), NCNN_PARAM, NCNN_BIN, CHARSET);
            Log.i(TAG, "chinese recognizer ready");
            // 英文模型同样加载，仅用于「误入」提示（不用于主识别），缺失时不影响主功能
            try {
                englishRecognizer = new HCCRRecognizer(getAssets(),
                        NCNN_PARAM_EN, NCNN_BIN_EN, CHARSET_EN);
                Log.i(TAG, "english recognizer ready");
            } catch (IOException | RuntimeException ex) {
                Log.w(TAG, "english model not loaded (non-fatal)", ex);
            }
        } catch (IOException | RuntimeException e) {
            Log.e(TAG, "model load failed", e);
            showStatus(getString(R.string.status_model_failed) + ": " + e.getMessage(), 0);
            Toast.makeText(this, getString(R.string.status_model_failed), Toast.LENGTH_LONG).show();
        }
    }

    // ---------------- 本地 TTS ----------------

    /** 初始化统一 TTS 管理：应用设置页选择的语音偏好；引擎不可用时给出提示。 */
    private void initTts() {
        ttsMgr = TtsManager.get(this);
        ttsMgr.setOnUtteranceFinished(this::onSpeechFinished);
        ttsMgr.setOnUnavailable(() -> runOnUiThread(
                () -> showStatus(getString(R.string.tts_unavailable), 0)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 从设置页返回后，恢复本页的「语音不可用」提示（设置页期间会临时改为 Toast）
        if (ttsMgr != null) {
            ttsMgr.setOnUnavailable(() -> runOnUiThread(
                    () -> showStatus(getString(R.string.tts_unavailable), 0)));
        }
    }

    private void speak(String text) {
        if (text == null || text.isEmpty()) return;
        if (ttsMgr == null) return;
        // 由 TtsManager 统一处理：应用保存的语音偏好并朗读
        // （含引擎异步切换、引擎不可用兜底；画布清空由 onSpeechFinished 或 scheduleClear 兜底）
        ttsMgr.speak(text, mode);
    }

    /** 朗读结束（或失败）回调：若画布未被用户改动，则自动清空，准备写下一个字。 */
    private void onSpeechFinished() {
        runOnUiThread(this::clearCanvasIfUnchanged);
    }

    @Override
    protected void onDestroy() {
        if (pendingRecognize != null) handler.removeCallbacks(pendingRecognize);
        handler.removeCallbacks(hideOverlay);
        handler.removeCallbacksAndMessages(null);
        // 单例 TTS 随进程常驻，不在此 shutdown，避免反复创建实例
        if (ttsMgr != null) ttsMgr.setOnUtteranceFinished(null);
        if (recognizer != null) recognizer.close();
        if (englishRecognizer != null) englishRecognizer.close();
        super.onDestroy();
    }

    // ---------------- 全屏（沉浸式）与横竖屏自适应 ----------------

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) applyFullscreen();
    }

    @Override
    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        applyFullscreen();
    }

    /** 隐藏状态栏与导航栏：Android 11+ 用 WindowInsetsController，旧系统用 systemUiVisibility。 */
    private void applyFullscreen() {
        View decor = getWindow().getDecorView();
        if (Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            decor.setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }
    }

    // ---------------- 手写回调：抬手后自动识别 + 朗读 ----------------

    @Override
    public void onStrokeStart() {
        // 用户又开始写：取消待识别计时器与兜底清空，隐藏“等待停笔”提示，避免边写边识别干扰
        if (pendingRecognize != null) {
            handler.removeCallbacks(pendingRecognize);
            pendingRecognize = null;
        }
        handler.removeCallbacks(pendingClear);
        hideStatusNow();
    }

    @Override
    public void onStrokeEnd() {
        scheduleRecognize();
    }

    private void scheduleRecognize() {
        if (pendingRecognize != null) handler.removeCallbacks(pendingRecognize);
        pendingRecognize = this::recognize;
        // 提示用户：停笔静默后才会识别整字，便于多笔画字一次性写完
        showStatus(getString(R.string.status_waiting), 0);
        handler.postDelayed(pendingRecognize, SETTLE_MS);
    }

    /**
     * 分模式识别：汉字模式只用中文模型；英文模式只用英文模型；对方模型仅作「误入」提示，
     * 不再像旧版那样中英文级联猜字，避免把汉字读成字母或反之。
     */
    private void recognize() {
        pendingRecognize = null;
        if (handwriting.isEmpty()) return;
        try {
            Bitmap bmp = handwriting.renderToBitmap();
            float[] input = preprocessToFloat64x64(bmp);
            long t0 = System.nanoTime();
            HCCRRecognizer.Result[] cn = recognizer.predict(input, TOPK);
            HCCRRecognizer.Result[] en = (englishRecognizer != null)
                    ? englishRecognizer.predict(input, TOPK) : new HCCRRecognizer.Result[0];
            long ms = (System.nanoTime() - t0) / 1_000_000;

            boolean chineseMode = HomeActivity.MODE_CHINESE.equals(mode);
            float cnProb = cn.length > 0 ? cn[0].probability : 0f;
            float enProb = en.length > 0 ? en[0].probability : 0f;
            String cnChar = cn.length > 0 ? cn[0].text : "";
            String enChar = en.length > 0 ? en[0].text : "";

            String phrase;
            if (chineseMode) {
                if (cnProb >= CONFIDENCE_THRESHOLD) {
                    phrase = buildPhrase(cnChar);
                    Log.i(TAG, "CN mode top1=" + cnChar + " prob=" + cnProb + " ms=" + ms);
                } else if (enProb >= CONFIDENCE_THRESHOLD) {
                    phrase = getString(R.string.hint_write_english);
                    Log.i(TAG, "CN mode but letter detected: " + enChar + "/" + enProb);
                } else {
                    phrase = getString(R.string.hint_rewrite);
                    Log.i(TAG, "CN mode low conf: CN=" + cnProb + " EN=" + enProb);
                }
            } else {
                if (enProb >= CONFIDENCE_THRESHOLD) {
                    phrase = getString(R.string.letter_prefix) + enChar;
                    Log.i(TAG, "EN mode top1=" + enChar + " prob=" + enProb + " ms=" + ms);
                } else if (cnProb >= CONFIDENCE_THRESHOLD) {
                    phrase = getString(R.string.hint_write_chinese);
                    Log.i(TAG, "EN mode but hanzi detected: " + cnChar + "/" + cnProb);
                } else {
                    phrase = getString(R.string.hint_rewrite);
                    Log.i(TAG, "EN mode low conf: EN=" + enProb + " CN=" + cnProb);
                }
            }
            recognizedStrokeCount = handwriting.getStrokeCount();
            showStatus(phrase);
            speak(phrase);
            scheduleClear();
        } catch (Throwable e) {
            Log.e(TAG, "recognize failed", e);
            showStatus("识别失败: " + e.getMessage(), 2000);
        }
    }

    /** 拼句：有常用词的用 “常用词W的X”，无词的用 “X字的X” 回退，保证句式完整可读。 */
    private String buildPhrase(String ch) {
        String word = wordMap.get(ch);
        if (word == null || word.isEmpty()) {
            return ch + "字的" + ch;
        }
        return word + "的" + ch;
    }

    // ---------------- 极简状态浮层 ----------------

    /** 立即淡出隐藏状态浮层（用于恢复书写时清除“等待停笔”提示）。 */
    private void hideStatusNow() {
        handler.removeCallbacks(hideOverlay);
        statusOverlay.animate().cancel();
        statusOverlay.animate().alpha(0f).setDuration(150)
                .withEndAction(() -> statusOverlay.setVisibility(View.INVISIBLE)).start();
    }

    private void showStatus(CharSequence text) {
        showStatus(text, 1600);
    }

    private void showStatus(CharSequence text, long keepMs) {
        handler.removeCallbacks(hideOverlay);
        statusOverlay.setText(text);
        statusOverlay.setVisibility(View.VISIBLE);
        statusOverlay.animate().cancel();
        statusOverlay.setAlpha(0f);
        statusOverlay.animate().alpha(1f).setDuration(200).start();
        if (keepMs > 0) {
            handler.postDelayed(hideOverlay, keepMs);
        }
    }

    // ---------------- 识别输入：与原版一致的共享 C 预处理 ----------------

    /**
     * Bitmap (RGBA) → float[64*64]。Java 只做 ARGB→灰度，
     * bbox / bilinear / 翻转 / 归一化全部走 src/cpp/preprocess.c（与 Python 同一份源码，字节级一致）。
     */
    private float[] preprocessToFloat64x64(Bitmap src) {
        int w = src.getWidth();
        int h = src.getHeight();
        int[] argb = new int[w * h];
        src.getPixels(argb, 0, w, 0, 0, w, h);
        byte[] gray = new byte[w * h];
        for (int i = 0; i < argb.length; i++) {
            int c = argb[i];
            int r = (c >> 16) & 0xff;
            int g = (c >> 8) & 0xff;
            int b = c & 0xff;
            gray[i] = (byte) ((299 * r + 587 * g + 114 * b) / 1000);
        }
        float[] out = new float[64 * 64];
        recognizer.preprocess(gray, w, h, out);
        return out;
    }

    /** InputStream → byte[]：兼容 Android 7~12（readAllBytes 仅 API 33+ 才有，需自行实现）。 */
    private static byte[] readAllBytes(InputStream in) throws IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) != -1) {
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }

    /** 加载组词词典 assets/words.json（由 tools/gen_words_json.py 生成）。 */
    private void loadWordsFromAssets() {
        try (InputStream in = getAssets().open(WORDS_JSON)) {
            JSONObject root = new JSONObject(new String(readAllBytes(in), StandardCharsets.UTF_8));
            Iterator<String> it = root.keys();
            while (it.hasNext()) {
                String ch = it.next();
                wordMap.put(ch, root.getString(ch));
            }
            Log.i(TAG, "words loaded: " + wordMap.size());
        } catch (Exception e) {
            Log.e(TAG, "words.json load failed", e);
        }
    }

    /** 朗读结束（或失败）回调：若画布未被用户改动，则自动清空，准备写下一个字。 */
    private void clearCanvasIfUnchanged() {
        if (recognizedStrokeCount < 0) return;
        if (handwriting.getStrokeCount() == recognizedStrokeCount) {
            handwriting.clear();
        }
        recognizedStrokeCount = -1;
        handler.removeCallbacks(pendingClear);
    }

    /** 兜底自动清空：识别后固定延时清空，不依赖 TTS onDone 回调（部分机型不可靠）。 */
    private void scheduleClear() {
        handler.removeCallbacks(pendingClear);
        handler.postDelayed(pendingClear, CLEAR_MS);
    }
}
