from pathlib import Path

DOCS = Path("docs")

# 1. CHANGELOG.md
cl = (DOCS / "CHANGELOG.md").read_text(encoding="utf-8")
v131 = (
    "## v1.3.1 (2026-10-28) English model upgraded to EMNIST real handwriting\n\n"
    "> English letter model retrained with EMNIST Letters (124,800 real handwritten samples).\n"
    "> Accuracy on real handwriting: 93.88%, much better generalization than v1.3.0 font-based model.\n\n"
    "### Changes\n\n"
    "| Category | Details |\n"
    "| --- | --- |\n"
    "| English model upgrade | Retrained on EMNIST Letters (124,800 train + 20,800 test real handwritten samples) |\n"
    "| Accuracy | 93.88% on real handwritten test set |\n"
    "| Generalization | Significantly better for finger-written letters on phone screen |\n"
    "| Model size | Unchanged: NCNN FP16, ~804KB |\n"
    "| Other features | Unchanged: dual-model cascade, low-confidence prompt, Chinese unaffected |\n\n"
    "### Checksums\n\n"
    "| File | SHA-256 |\n"
    "| --- | --- |\n"
    "| v1.3.1-release.apk | D842E181E430FFC12F211D673E7BB58AB4A99CC98705DAE434AF6798231EDA0F |\n"
    "| v1.3.1-debug.apk | E0F2898A2E6936A98F00AA248DF42B74B3FFC92B3FB3C19F0B19C9B2F60D9FA9 |\n\n"
    "---\n\n"
)
marker = "## v1.3.0"
idx = cl.index(marker)
cl = cl[:idx] + v131 + cl[idx:]
(DOCS / "CHANGELOG.md").write_text(cl, encoding="utf-8")
print("CHANGELOG.md updated")

# 2. HANDOVER.md
ho = (DOCS / "HANDOVER.md").read_text(encoding="utf-8")
ho = ho.replace("1.3.0", "1.3.1").replace("versionCode = 6", "versionCode = 7")
ho += (
    "\n### v1.3.1 EMNIST model upgrade (2026-10-28)\n"
    "- English model retrained on EMNIST Letters (124,800 real handwritten samples)\n"
    "- Accuracy: 93.88% on real handwritten test set\n"
    "- Training: tools/train_english_emnist.py (optimized pre-process)\n"
    "- Export: tools/export_english_ncnn.py (unchanged)\n"
    "- build.gradle.kts: versionName 1.3.1, versionCode 7\n"
)
(DOCS / "HANDOVER.md").write_text(ho, encoding="utf-8")
print("HANDOVER.md updated")

# 3. RELEASE_v1.3.1.md
release = (
    "# v1.3.1\n\n"
    "English model upgraded: retrained on EMNIST Letters (124,800 real handwritten samples).\n"
    "Accuracy on real handwriting: 93.88%. Same features as v1.3.0, better English recognition.\n\n"
    "## Download\n\n"
    "| File | Size | SHA-256 |\n"
    "| --- | --- | --- |\n"
    "| v1.3.1-release.apk | 31.9MB | D842E181E430FFC12F211D673E7BB58AB4A99CC98705DAE434AF6798231EDA0F |\n"
    "| v1.3.1-debug.apk | 32.5MB | E0F2898A2E6936A98F00AA248DF42B74B3FFC92B3FB3C19F0B19C9B2F60D9FA9 |\n\n"
    "## What changed from v1.3.0\n\n"
    "- English letter model retrained with EMNIST real handwritten data (124,800 samples)\n"
    "- 93.88% accuracy on real handwriting test set\n"
    "- Better generalization for finger-written letters on phone screen\n"
    "- Model size unchanged (~804KB), Chinese recognition unchanged\n\n"
    "## All features\n\n"
    "- Chinese handwriting recognition (3755 GB2312 chars, ~95% accuracy)\n"
    "- English letter recognition (26 letters A-Z, ~94% accuracy on real handwriting)\n"
    "- Dual-model cascade: picks higher confidence result\n"
    "- Auto word formation and TTS\n"
    "- Low-confidence prompt: please rewrite\n"
    "- Fully offline, no network, no ads, no data collection\n"
)
(DOCS / "RELEASE_v1.3.1.md").write_text(release, encoding="utf-8")
print("RELEASE_v1.3.1.md created")
print("All docs updated for v1.3.1")