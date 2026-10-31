# v1.4.0

Bug fix: low-confidence recognition now speaks prompt and auto-clears canvas.

## What was fixed

In v1.3.0-v1.3.1, when the app could not confidently recognize what you wrote:
- The "please rewrite" message only showed on screen, no voice
- The canvas did not auto-clear, you had to write over old strokes

Now in v1.4.0:
- The app speaks "please rewrite" aloud via TTS
- The canvas auto-clears after the prompt, so you can immediately write again
- Confidence threshold raised from 0.35 to 0.45, reducing false recognitions

## Download

| File | Size | SHA-256 |
| --- | --- | --- |
| v1.4.0-release.apk | 31.9MB | 9375734A1E7AC10CB1B332F0D6780902132B9C3F52A7F4E7B5C894510E60B679 |
| v1.4.0-debug.apk | 32.5MB | F6060A69DB506F59BE4C693E1CCEF7EF6CCD7047B149445EDC270FEF39C2FF70 |

## Install

Copy APK to phone and install, or: adb install v1.4.0-release.apk
