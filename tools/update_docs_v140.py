from pathlib import Path

DOCS = Path("docs")

# 1. CHANGELOG.md
cl = (DOCS / "CHANGELOG.md").read_text(encoding="utf-8")
v140 = (
    "## v1.4.0 (2026-10-28) Fix low-confidence prompt + auto-clear\n\n"
    "> Fixed: low-confidence recognition had no voice prompt and did not auto-clear canvas.\n"
    "> Now speaks \"please rewrite\" and auto-clears so user can immediately write again.\n\n"
    "### Bug fixes\n\n"
    "| Issue | Fix |\n"
    "| --- | --- |\n"
    "| No voice on low-confidence | Low-confidence branch now calls speak() to read \"please rewrite\" aloud |\n"
    "| Canvas not clearing on low-confidence | Removed early return; now falls through to scheduleClear() |\n"
    "| False recognition on uncertain input | Raised CONFIDENCE_THRESHOLD from 0.35 to 0.45 |\n\n"
    "### Checksums\n\n"
    "| File | SHA-256 |\n"
    "| --- | --- |\n"
    "| v1.4.0-release.apk | 9375734A1E7AC10CB1B332F0D6780902132B9C3F52A7F4E7B5C894510E60B679 |\n"
    "| v1.4.0-debug.apk | F6060A69DB506F59BE4C693E1CCEF7EF6CCD7047B149445EDC270FEF39C2FF70 |\n\n"
    "---\n\n"
)
marker = "## v1.3.1"
if marker in cl:
    idx = cl.index(marker)
    cl = cl[:idx] + v140 + cl[idx:]
else:
    cl = v140 + cl
(DOCS / "CHANGELOG.md").write_text(cl, encoding="utf-8")
print("CHANGELOG.md updated")

# 2. HANDOVER.md
ho = (DOCS / "HANDOVER.md").read_text(encoding="utf-8")
ho = ho.replace("1.3.1", "1.4.0").replace("versionCode = 7", "versionCode = 8")
ho += (
    "\n### v1.4.0 Bug fix (2026-10-28)\n"
    "- Fix: low-confidence branch in recognize() had showStatus+return, skipping speak() and scheduleClear()\n"
    "- Removed showStatus(phrase,2000) and return from low-confidence branch\n"
    "- Now falls through to speak(phrase) + scheduleClear() like normal recognition\n"
    "- Raised CONFIDENCE_THRESHOLD from 0.35f to 0.45f\n"
    "- build.gradle.kts: versionName 1.4.0, versionCode 8\n"
    "- User impact: low-confidence now says \"please rewrite\" aloud and auto-clears canvas\n"
)
(DOCS / "HANDOVER.md").write_text(ho, encoding="utf-8")
print("HANDOVER.md updated")

# 3. RELEASE_v1.4.0.md
release = (
    "# v1.4.0\n\n"
    "Bug fix: low-confidence recognition now speaks prompt and auto-clears canvas.\n\n"
    "## What was fixed\n\n"
    "In v1.3.0-v1.3.1, when the app could not confidently recognize what you wrote:\n"
    "- The \"please rewrite\" message only showed on screen, no voice\n"
    "- The canvas did not auto-clear, you had to write over old strokes\n\n"
    "Now in v1.4.0:\n"
    "- The app speaks \"please rewrite\" aloud via TTS\n"
    "- The canvas auto-clears after the prompt, so you can immediately write again\n"
    "- Confidence threshold raised from 0.35 to 0.45, reducing false recognitions\n\n"
    "## Download\n\n"
    "| File | Size | SHA-256 |\n"
    "| --- | --- | --- |\n"
    "| v1.4.0-release.apk | 31.9MB | 9375734A1E7AC10CB1B332F0D6780902132B9C3F52A7F4E7B5C894510E60B679 |\n"
    "| v1.4.0-debug.apk | 32.5MB | F6060A69DB506F59BE4C693E1CCEF7EF6CCD7047B149445EDC270FEF39C2FF70 |\n\n"
    "## Install\n\n"
    "Copy APK to phone and install, or: adb install v1.4.0-release.apk\n"
)
(DOCS / "RELEASE_v1.4.0.md").write_text(release, encoding="utf-8")
print("RELEASE_v1.4.0.md created")
print("All docs updated for v1.4.0")