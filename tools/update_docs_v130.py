"""Update CHANGELOG.md, HELP.md, HANDOVER.md for v1.3.0 English letter support."""
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DOCS = ROOT / "docs"

# --- CHANGELOG.md ---
changelog = (DOCS / "CHANGELOG.md").read_text(encoding="utf-8")
v130 = (
    "## v1.3.0 (2026-10-28) English letter recognition + accuracy improvement\n\n"
    "> Added 26 English letter (A-Z) offline recognition and TTS; dual-model cascade inference, Chinese recognition unaffected.\n\n"
    "### Changes\n\n"
    "| Category | Details |\n"
    "| --- | --- |\n"
    "| English letter recognition | New independent English model (PlainCNN, 26 classes, EMNIST Letters trained, NCNN FP16 deployed), supports handwritten A-Z recognition and TTS |\n"
    "| Dual-model cascade | Chinese and English models run simultaneously, compare top-1 confidence: use English when clearly higher, otherwise Chinese (+0.1 bias), Chinese accuracy unaffected |\n"
    "| English TTS | Recognized letter read as \"letter X\" (e.g. write A -> \"letter A\"), same system TTS engine |\n"
    "| Low-confidence handling | When both models below threshold (0.35), prompts \"please rewrite\" instead of wrong read, reducing misrecognition |\n"
    "| Accuracy improvement | Low-confidence results no longer force-read; confusing pairs (O/mouth, I/stroke) resolved via dual-model comparison |\n"
    "| Offline guarantee | English model also local NCNN inference, no network, no permissions |\n\n"
    "### Technical details\n\n"
    "- English model: PlainCNN (5-stage CNN + GAP + Linear), input [1,64,64], output 26 classes, ~1.1M params\n"
    "- Training data: EMNIST Letters (~140k train + ~15k test)\n"
    "- Preprocessing: same C pipeline as Chinese (bbox crop -> resize 56 -> center 64x64 -> invert+normalize)\n"
    "- Deployment: NCNN FP16, ~1.1MB, ~1ms inference\n"
    "- Cascade: dual-model parallel inference (~3-5ms total), English used when confidence > 0.35 and > Chinese + 0.1\n\n"
    "---\n\n"
)
marker = "## v1.2.0"
idx = changelog.index(marker)
changelog = changelog[:idx] + v130 + changelog[idx:]
(DOCS / "CHANGELOG.md").write_text(changelog, encoding="utf-8")
print("CHANGELOG.md updated")

# --- HELP.md ---
help_path = DOCS / "HELP.md"
help_text = help_path.read_text(encoding="utf-8")

# Add English section after the FAQ section
en_section = (
    "\n## English letter recognition\n\n"
    "Starting from v1.3.0, the app also recognizes 26 English letters (A-Z):\n\n"
    "- Write an uppercase letter on the canvas;\n"
    "- The app runs both Chinese and English models, picks the higher confidence result;\n"
    "- If it is an English letter, it reads \"letter X\" (e.g. write A -> reads \"letter A\");\n"
    "- If both models have low confidence, it prompts \"please rewrite\" instead of guessing wrong.\n\n"
    "The Chinese recognition accuracy is not affected. The English model is a small independent\n"
    "network (~1.1MB) that runs alongside the Chinese model (~1ms extra).\n"
)

# Insert before "## Privacy" section
priv_marker = "## \u9690\u79c1\u8bf4\u660e"
if priv_marker in help_text:
    idx = help_text.index(priv_marker)
    help_text = help_text[:idx] + en_section + "\n" + help_text[idx:]
else:
    help_text += "\n" + en_section
help_path.write_text(help_text, encoding="utf-8")
print("HELP.md updated")

# --- HANDOVER.md ---
handover_path = DOCS / "HANDOVER.md"
handover_text = handover_path.read_text(encoding="utf-8")

# Update version info
handover_text = handover_text.replace(
    "\u5199\u8bfb\u677f v1.2.0 \u6b63\u5f0f\u7248",
    "\u5199\u8bfb\u677f v1.3.0 \u6b63\u5f0f\u7248"
)
handover_text = handover_text.replace(
    "versionName = \"1.2.0\"",
    "versionName = \"1.3.0\""
)
handover_text = handover_text.replace(
    "versionCode = \"5\"",
    "versionCode = \"6\""
)
handover_text = handover_text.replace(
    "versionCode `5`",
    "versionCode `6`"
)

# Add English model section after section 8 (code changes)
en_handover = (
    "\n### v1.3.0 English letter support (2026-10-28)\n\n"
    "- New assets: `english.ncnn.param`, `english.ncnn.bin`, `english_charset.json` (26 letters A-Z)\n"
    "- `MainActivity.java`: added `englishRecognizer` field, dual-model cascade in `recognize()`\n"
    "- Cascade logic: both models predict in parallel, English used when confidence > 0.35 and > Chinese + 0.1\n"
    "- Low-confidence (< 0.35 both): prompts \"please rewrite\" instead of wrong read\n"
    "- English TTS: reads \"letter X\" (e.g. A -> \"letter A\")\n"
    "- `build.gradle.kts`: versionName 1.3.0, versionCode 6\n"
    "- English model: PlainCNN(26), trained on EMNIST Letters, exported via pnnx to NCNN FP16\n"
    "- Training scripts: `tools/train_english.py`, `tools/export_english_ncnn.py`\n"
    "- Unchanged: Chinese model, JNI runtime, preprocessing pipeline, TTS engine\n"
)

# Insert after section 8
sec9_marker = "## 9."
if sec9_marker in handover_text:
    idx = handover_text.index(sec9_marker)
    handover_text = handover_text[:idx] + en_handover + "\n" + handover_text[idx:]
else:
    handover_text += "\n" + en_handover
handover_path.write_text(handover_text, encoding="utf-8")
print("HANDOVER.md updated")
print("\nAll docs updated for v1.3.0")