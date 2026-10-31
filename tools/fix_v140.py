from pathlib import Path

# Fix MainActivity.java
p = Path("android/app/src/main/java/com/shiyu/hccr/MainActivity.java")
t = p.read_text(encoding="utf-8")

# 1. Raise threshold 0.35 -> 0.45
t = t.replace("CONFIDENCE_THRESHOLD = 0.35f", "CONFIDENCE_THRESHOLD = 0.45f")
print("threshold: 0.35 -> 0.45")

# 2. Fix low-confidence branch: remove showStatus+return so it falls through to speak+scheduleClear
old_block = '                showStatus(phrase, 2000);\n                return;\n            } else {'
new_block = '            } else {'
if old_block in t:
    t = t.replace(old_block, new_block, 1)
    print("low-confidence branch: removed showStatus+return")
else:
    print("WARNING: low-confidence block not found!")

p.write_text(t, encoding="utf-8")

# Update version to 1.4.0
g = Path("android/app/build.gradle.kts")
gt = g.read_text(encoding="utf-8")
gt = gt.replace("versionCode = 7", "versionCode = 8")
gt = gt.replace('versionName = "1.3.1"', 'versionName = "1.4.0"')
g.write_text(gt, encoding="utf-8")
print("version: 1.3.1 -> 1.4.0")