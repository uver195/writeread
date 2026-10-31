# 写读板 · 交接文档（开发者文档）

> 文档版本：v1.0.0（2026-10-22）
> 适用对象：接手本项目继续开发、构建、打包的开发者

---

## 1. 项目概述

**写读板** 是一款 **离线全屏手写练字 + 语音朗读** 安卓工具：

- 在手机屏幕上用手指书写汉字；
- 抬手后由设备本地 NCNN INT8 模型离线识别（不调用任何云端接口）；
- 识别成功后自动按“XX的XX”句式组词并朗读（例：识别出“中”，朗读“中国的中”），帮助练字者掌握读音与组词；
- 朗读完成后自动清空画布，可连续书写下一个字。

## 2. 来源与版本

- 上游仓库：https://github.com/Ismantic/Handwritten （本仓库为本地私有副本，**不与上游同步**）
- 上游基线：`v0.1.1`（commit `0e83ffe` 附近）
- 本交付版本：**写读板 v1.4.0 正式版**（packageName `com.writeread.handwritten`，versionName `1.2.0`，versionCode `6`）
- 本地工作目录：`C:\workspace\WriteRead\Handwritten`

## 3. 目录结构与关键文件

```text
Handwritten/
├─ android/                         # Android 工程（本交付的主要工作目录）
│  ├─ app/                          # 应用模块
│  │  ├─ src/main/
│  │  │  ├─ AndroidManifest.xml     # 已改：sensor 横竖屏、configChanges、无联网权限
│  │  │  ├─ java/com/shiyu/hccr/
│  │  │  │  ├─ MainActivity.java    # 已重写：全屏/TTS/拼句/自动清空
│  │  │  │  └─ HandwritingView.java # 已加：getStrokeCount()（自动清空判定用）
│  │  │  ├─ jni/CMakeLists.txt      # 未改：链接 third_party/ncnn-android + src/cpp/preprocess.c
│  │  │  ├─ res/layout/activity_main.xml  # 已重写：全屏画布 + 极简状态浮层
│  │  │  ├─ res/values/strings.xml / styles.xml # 已改：应用名“写读板”、全屏主题
│  │  │  └─ assets/                 # 打包进 APK 的离线资源
│  │  │     ├─ mbv2_aug.int8.ncnn.param / .bin   # NCNN INT8 模型（约 4.1MB）
│  │  │     ├─ charset.json         # 3755 字表（GB2312 一级）
│  │  │     └─ words.json           # 组词词典（本次新增，由 tools/gen_words_json.py 生成）
│  │  ├─ build.gradle.kts           # 已改：applicationId、versionName、UTF-8 编码
│  │  └─ keystore.properties        # 签名配置（gitignored，仅本机存在）
│  ├─ runtime/src/main/             # 离线 HCCR runtime（JNI 绑定 + HCCRRecognizer，未改）
│  ├─ third_party/ncnn-android/     # ncnn-20240820-android-vulkan 预编译库（gitignored）
│  └─ gradle/wrapper/               # Gradle 8.11.1 wrapper
├─ src/cpp/preprocess.c             # 共享 C 预处理（Java 与 Python 共用，未改）
├─ models/Handwritten/ncnn/         # 从 HuggingFace 下载的模型源文件（gitignored）
├─ tools/gen_words_json.py          # 组词词典生成脚本（本次新增）
└─ dist/                            # 交付 APK（当前：写读板-v1.0.0-*.apk）
```

## 4. 本机构建环境（已配置好）

| 组件 | 版本/路径 |
| --- | --- |
| JDK | Android Studio JBR 21 — `C:\Program Files\Android\Android Studio\jbr` |
| Android SDK | `C:\Users\llhh\AppData\Local\Android\Sdk`（platforms 35、build-tools 34/35） |
| NDK | `30.0.14904198`（即 r30-beta1）— `%LOCALAPPDATA%\Android\Sdk\ndk\30.0.14904198` |
| CMake | `3.22.1` — `%LOCALAPPDATA%\Android\Sdk\cmake\3.22.1` |
| NCNN | `ncnn-20240820-android-vulkan`（arm64-v8a 与 x86_64）— `android/third_party/ncnn-android/` |
| Gradle | 8.11.1（wrapper 已锁定，下载源为腾讯云镜像） |
| 本地代理 | `127.0.0.1:7897`（网络受限时，Gradle 代理写在 `%USERPROFILE%\.gradle\gradle.properties`） |

> 说明：构建所需 NDK/CMake/NCNN/模型均按 `docs/DEPLOY.md` 与 `android/BUILD.md` 约定
> 手工安装完成，不使用 Android Studio 自动下载（自动下载在无 cmdline-tools 环境会因许可校验失败）。

## 5. 构建命令

仓库只有 bash 版 `gradlew`（无 `gradlew.bat`），Windows 下有两种等价方式：

**方式 A（推荐，复用 wrapper jar）：**
```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
cd android
& "$env:JAVA_HOME\bin\java.exe" -cp "gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain --no-daemon assembleRelease
```

**方式 B（Git Bash 环境）：**
```bash
cd android
./gradlew assembleDebug      # 调试包
./gradlew assembleRelease     # 签名发布包
```

产物位置：
- 调试包：`android/app/build/outputs/apk/debug/app-debug.apk`
- 发布包：`android/app/build/outputs/apk/release/app-release.apk`
- 交付副本：`dist/写读板-v1.2.0-{release,debug}.apk`

## 6. 签名信息

| 项 | 值 |
| --- | --- |
| 密钥库 | `android/writeread-release.jks`（本机文件） |
| 密钥别名 | `writeread` |
| 密钥库/密钥口令 | `WriteRead2026` |
| 证书 | CN=WriteRead, OU=WriteRead, O=WriteRead, C=CN，RSA 2048，有效期 10 年 |
| 配置 | `android/keystore.properties`（gitignored，不入库） |

换机续签/升级时请保留 `writeread-release.jks`、口令与 `keystore.properties`，否则无法用同一证书升级安装。

## 7. 需求落实情况对照

| 需求 | 实现位置 | 状态 |
| --- | --- | --- |
| 页面自动跟随设备姿态横竖屏切换 | Manifest `android:screenOrientation="sensor"` + `configChanges` | 完成 |
| 隐藏状态栏、导航栏 | `MainActivity.applyFullscreen()`（API 24~35 全兼容沉浸式） | 完成 |
| 手写画布撑满整个屏幕 | `activity_main.xml`：HandwritingView `match_parent` 全屏 | 完成 |
| 移除输入法候选词条 | 删除候选 TextView 行、`updateCandidates/onPickCandidate` 等 | 完成 |
| 移除多余控制按钮 | 删除 清空/撤销 Button 及其逻辑 | 完成 |
| 离线识别成功回调接入系统 TTS | `MainActivity.recognize()` → `buildPhrase()` → `speak()` | 完成 |
| 自动拼接句式“XX的XX” | `buildPhrase()` + `assets/words.json`（示例：中→“中国的中”） | 完成 |
| 使用本地语音包、不联网 | `initTts()` 优先 `Voice.isNetworkConnectionRequired()==false` 的中文语音 | 完成 |
| 停笔静默后自动识别并朗读 | `onStrokeEnd()` → 重置停笔计时器（`SETTLE_MS=1500ms`）→ 静默到期识别整字 → 朗读 | 完成 |
| 全程离线 NCNN 推理 | JNI/`runtime` 与 `src/cpp/preprocess.c` 完全保留；Manifest 无 INTERNET 权限 | 完成 |

## 8. 代码修改明细

- `MainActivity.java`（重写）：全屏沉浸、sensor 旋转适配、TTS（离线 Voice 优先 + setLanguage 兜底）、
  `words.json` 加载、`buildPhrase()` 拼句、朗读完成自动清空画布、极简状态浮层；
  删除候选词条/清空/撤销的全部逻辑与 DEBUG 预览图写入。
- `HandwritingView.java`：仅新增 `getStrokeCount()`。
- `activity_main.xml`（重写）：`FrameLayout` + 全屏 `HandwritingView` + 顶部自动淡出 `status_overlay`。
- `strings.xml`：应用名改“写读板”，新增状态/TTS 文案，删除原候选/按钮文案。
- `styles.xml`：主题改为 `windowFullscreen` + 刘海屏 `shortEdges`。
- `AndroidManifest.xml`：`screenOrientation="sensor"`、`configChanges`、`launchMode`、应用名；**不声明任何权限**。
- uild.gradle.kts：pplicationId=com.writeread.handwritten、ersionName=1.0.1、ersionCode=2、compileOptions.encoding=UTF-8。
- v1.0.1 崩溃修复（2026-10-23）：InputStream.readAllBytes() 仅 Android 13+ 存在，Android 10 上启动即闪退（NoSuchMethodError）；已在 MainActivity 与 
untime/HCCRRecognizer 中改为自实现的兼容读取方法（8KB 缓冲循环），Android 7（API 24）起全兼容。
- v1.1.1 识别时机优化（2026-10-23）：把“抬手即识别”改为“停笔静默 `SETTLE_MS`（默认 1500ms）后识别整字”，连续书写时每笔重置计时器，避免“张=弓+长”等多笔画字在只写完“弓”时被误识别；书写中提示“停笔后将识别整字…”，恢复书写自动隐藏。
- v1.1.1 自动清空兜底（2026-10-23）：原仅靠 TTS `onDone` 清空画布，部分机型（含华为 EMUI）不回调导致不清空；改为识别后额外调度 `CLEAR_MS`（默认 2500ms）定时清空作为兜底，TTS 提前回调则即时清空，均带“笔画数未变”校验，避免误删新笔画。
- v1.2.0 正式版（2026-10-23）：①组词词库升级：重做生成器排除人名(POS nr/nrt)、优先双字常用词、设最低词频门槛(MIN_FREQ=30)，覆盖 87.6%，朗读更自然（中→中国、天→天下、卜→卜筮、长→长江）；②TTS 多厂商兼容：重写 initTts 为 pickOfflineChineseVoice()+setChineseLanguage()，优先选离线中文语音，按 zh-CN/zh/zh-TW 顺序回退，适配华为/小米/红米/三星/OPPO/vivo/魅族，引擎不可用时给清晰引导。
- `tools/gen_words_json.py`（新增）：从 jieba 常用词频表为本项目 3755 字生成组词词典。
- `assets/words.json`（新增）：汉字→以该字开头的最常用词（覆盖 3291/3755，约 87.6%（v2：排除人名、优先双字常用词、设最低词频门槛，质量更高）），约 52.5 KB。
- 未改：`runtime/`（JNI + HCCRRecognizer）、`src/cpp/preprocess.c`、NCNN 推理链路。


### v1.4.0 English letter support (2026-10-28)

- New assets: `english.ncnn.param`, `english.ncnn.bin`, `english_charset.json` (26 letters A-Z)
- `MainActivity.java`: added `englishRecognizer` field, dual-model cascade in `recognize()`
- Cascade logic: both models predict in parallel, English used when confidence > 0.35 and > Chinese + 0.1
- Low-confidence (< 0.35 both): prompts "please rewrite" instead of wrong read
- English TTS: reads "letter X" (e.g. A -> "letter A")
- `build.gradle.kts`: versionName 1.4.0, versionCode 6
- English model: PlainCNN(26), trained on EMNIST Letters, exported via pnnx to NCNN FP16
- Training scripts: `tools/train_english.py`, `tools/export_english_ncnn.py`
- Unchanged: Chinese model, JNI runtime, preprocessing pipeline, TTS engine

## 9. 关键技术设计说明

### 9.1 拼句“XX的XX”
1. 识别 top-1 汉字 X（模型输出 3755 类 softmax，取最高概率）；
2. 查 `words.json` 得到以 X 开头的常用词 W（如 中→中国）；
3. 朗读文本 = `W + "的" + X`，例如：
   - 中 → **中国的中**
   - 口 → **口味的口**（词典取词频最高词，示例中的“口子”同样在词典内）
4. 词典未覆盖的罕见字回退为 `X + "字的" + X`（例：某生僻字 → “X字的X”），保证句式完整。

### 9.2 组词词典再生成
```powershell
# 1) 下载 jieba 词频表（仅首次）
curl -L -o jieba_dict.txt https://raw.githubusercontent.com/fxsjy/jieba/master/jieba/dict.txt
# 2) 生成
python tools/gen_words_json.py jieba_dict.txt
```
产物直接覆盖 `android/app/src/main/assets/words.json`，重新构建即可生效。

### 9.3 TTS 离线性
- 使用系统 `TextToSpeech`（Android 原生 API），不引入第三方引擎；
- 优先遍历 `tts.getVoices()` 选取 `isNetworkConnectionRequired() == false` 且语言为 `zh` 的本地语音；
- 找不到时回退 `setLanguage(Locale.SIMPLIFIED_CHINESE)`；
- 初次使用时若系统提示“语音数据未下载”，请在系统设置中为中文语音包下载一次离线数据
  （此后朗读全程离线）。部分国产 ROM 自带离线中文语音，无需任何设置。

### 9.4 朗读完成自动清空
- 记录识别瞬间的笔画数 `recognizedStrokeCount`；
- TTS `onDone/onError` 回调后即时清空；同时识别后调度 `CLEAR_MS`（默认 2500ms）定时器兜底清空（部分机型 TTS 不回调 onDone）；
- 两者都校验“当前笔画数 == 识别时笔画数”才清空，避免误删用户新写的笔画；
- 用户开始写新笔画时（`onStrokeStart`）取消待识别与待清空任务。
- 这样无需任何按钮即可连续练字（写一个字 → 读一个字 → 自动清空）。

### 9.5 全屏兼容
- API 30+：`WindowInsetsController.hide(statusBars|navigationBars)` + `BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE`（沉浸式，从屏幕边缘滑动可临时唤出）；
- API 24~29：`SYSTEM_UI_FLAG_IMMERSIVE_STICKY | FULLSCREEN | HIDE_NAVIGATION` 等；
- 在 `onWindowFocusChanged` 与 `onConfigurationChanged` 中重复应用，保证旋转后仍全屏。

## 10. 常见问题排查

| 现象 | 排查/处理 |
| --- | --- |
| 打开项目 Android Studio 报 NDK/CMake 缺失 | 本机已装到 SDK（见第 4 节）；若换机，需重新安装 `ndk;30.0.14904198` 与 `cmake;3.22.1` |
| `ncnn-android 找不到`（CMake 报错） | 检查 `android/third_party/ncnn-android/{arm64-v8a,x86_64}` 是否存在；缺失时下载 `ncnn-20240820-android-vulkan.zip` 解压改名 |
| 模型加载失败 | 确认 `app/src/main/assets/` 下 `mbv2_aug.int8.ncnn.{param,bin}` 与 `charset.json` 存在（模型来自 HuggingFace `Ismantic/Handwritten`） |
| Java 编译乱码 | 本项目源码为 UTF-8（无 BOM）；`build.gradle.kts` 已设 `encoding="UTF-8"`；勿用 ANSI/GBK 编辑器改写含中文注释的源码 |
| 下载/同步依赖超时 | 本机按第 4 节代理写入 `%USERPROFILE%\.gradle\gradle.properties`；Gradle wrapper 走腾讯云镜像 |
| TTS 无声 | 系统设置 → 文字转语音（TTS）→ 确认勾选支持中文的引擎并安装/下载中文语音包，重启 App |

## 11. 上游模型/数据来源

- 模型：https://huggingface.co/Ismantic/Handwritten （`ncnn/model.ncnn.{param,bin}`、`charset.json`）
- 训练链路：CASIA HWDB1.1 → PyTorch → NCNN INT8（详见上游 README / docs）
- 精度：MobileNetV2 在 3755 类 GB2312 一级字上 Top-1 约 95.47%，INT8 模型 4.1MB，移动端推理约 2~4ms
### v1.4.0 EMNIST model upgrade (2026-10-28)
- English model retrained on EMNIST Letters (124,800 real handwritten samples)
- Accuracy: 93.88% on real handwritten test set
- Training: tools/train_english_emnist.py (optimized pre-process)
- Export: tools/export_english_ncnn.py (unchanged)
- build.gradle.kts: versionName 1.4.0, versionCode 7

### v1.4.0 Bug fix (2026-10-28)
- Fix: low-confidence branch in recognize() had showStatus+return, skipping speak() and scheduleClear()
- Removed showStatus(phrase,2000) and return from low-confidence branch
- Now falls through to speak(phrase) + scheduleClear() like normal recognition
- Raised CONFIDENCE_THRESHOLD from 0.35f to 0.45f
- build.gradle.kts: versionName 1.4.0, versionCode 8
- User impact: low-confidence now says "please rewrite" aloud and auto-clears canvas

### v1.5.0 Mode-split handwriting + settings & TTS (2026-10-29)

- **Split recognition into independent modes**: `chinese` only uses the Chinese model, `english` only uses the English model. Removed the v1.4.x cross-model cascade that guessed between hanzi and letters. A mis-entered sample (e.g. writing a letter in Chinese mode) now shows a friendly "please switch to X learning" hint instead of being mis-read.
- **New home screen `HomeActivity`** (LAUNCHER) with three entries: 汉字学习 / 英文学习 / 设置.
- **`MainActivity`** now reads Intent extra `mode` (`chinese`/`english`); each mode loads its own model for primary recognition; the other model is loaded only to detect mis-entry.
- **New `SettingsActivity`**: 文本转语音 / 关于软件 / 帮助说明.
- **New `TtsSettingsActivity`**: pick a system `Voice` for "中文朗读" and "英文朗读" separately, or "跟随系统默认". Stored in `SharedPreferences` `writeread_prefs` (`tts_voice_cn` / `tts_voice_en`); empty = follow system TTS.
- **New `HelpActivity`**: in-app help text (consistent with docs/HELP.md).
- **TTS default changed**: no longer force-locks a Chinese offline voice; default follows system, optional per-mode voice via settings.
- **`AndroidManifest.xml`**: LAUNCHER moved to HomeActivity; registered Settings/Tts/Help; MainActivity is internal (fullscreen theme).
- **`res/values/styles.xml`**: added `Theme.HCCR.Panel` (non-fullscreen) and `HomeListItem`.
- **`res/values/strings.xml`**: added home/settings/help/TTS/hint strings and `help_content`.
- **`build.gradle.kts`**: versionName 1.5.0, versionCode 9.
- **Layouts**: activity_home.xml, activity_settings.xml, activity_tts_settings.xml, activity_help.xml.
- **Unchanged**: JNI runtime, src/cpp/preprocess.c, NCNN pipeline, model files.
- **Build**: `./gradlew assembleRelease` → `dist/写读板-v1.5.0-release.apk` (SHA-256 `59c95add69a12333b977cd8e3b04da2ce152c430f57432e651a3f8ade430e76a`); debug → `dist/写读板-v1.5.0-debug.apk` (see RELEASE_v1.5.0.md).

### v1.5.1 TTS engine-switch fix (2026-10-29)

- **Bug（真机复现）**：设置页「文本转语音」打开后列表里只有「跟随系统默认」，列不出手机已安装的任何语音库，无法切换语音。
- **Root cause**：原 `TtsSettingsActivity` 用 `tts.getVoices()` 枚举可选发音。而华为等厂商的默认 TTS 引擎（华为 HUAWEI TTS）**不实现 `getVoices()`** API，返回空集，导致界面无可选语音。
- **Fix**：改为用 `getEngines()` 枚举手机已安装的 **TTS 引擎（即语音库，厂商 ROM 上可靠）**，选中引擎后再用临时 `TextToSpeech` 实例探测该引擎内部的 `voices`（按语言前缀 `zh`/`en` 过滤）。中文/英文朗读可分别指定「引擎包名 + 发音名」；二者皆空即跟随系统默认。
- **New `TtsManager`** (singleton, `java/com/shiyu/hccr/TtsManager.java`)：封装 TTS 生命周期、引擎异步切换（`setEngineByPackageName`，会重入 `onInit`）、按模式应用保存偏好、朗读、引擎不可用回调（`setOnUnavailable`）。
- **`TtsSettingsActivity`** (rewritten)：两级选择「语音库(引擎) + 发音」，新增「打开系统文字转语音设置」按钮（`com.android.settings.TTS_SETTINGS`）。
- **`MainActivity`** (TTS part rewritten)：`TtsManager.get(this)` 初始化、`setOnUtteranceFinished` 清屏、`setOnUnavailable` 提示；`speak(text, mode)` 委托 TtsManager。
- **`res/values/strings.xml`**：新增 `tts_engine_default` / `tts_voice_default` / `tts_loading_voices` / `tts_no_voices` / `tts_open_system_settings` / `tts_engine_label` / `tts_voice_label` / `tts_saved`。
- **`res/layout/activity_tts_settings.xml`**：改为 引擎组 + 发音组（中/英各一组）+ 系统设置按钮。
- **`build.gradle.kts`**：versionName 1.5.1, versionCode 10.
- **Tested on**：华为 PCT-AL10 (Android 10) — 已安装测试，复现并修复「列不出语音库」问题。真机验证步骤见 `docs/RELEASE_v1.5.1.md`。
- **Build**: release → `dist/写读板-v1.5.1-release.apk` (SHA-256 `e44c80c654b624259509b4bd1d568a99534822969393ea74ad43777c3ffcabdb`); debug → `dist/写读板-v1.5.1-debug.apk` (see RELEASE_v1.5.1.md).

### v1.5.2 TTS speech-deadlock fix (2026-10-29)

- **Bug（真机复现）**：v1.5.1 已能列出手机全部语音库，但一旦在设置里选中某个语音库，App 就再也不朗读（静默、无任何提示）。
- **Root cause**：`TtsManager` 切换引擎用废弃的 `setEngineByPackageName()`，该 API **不会再次触发 `onInit` 回调**，导致 `switchingEngine` 永远为 true、待朗读文本缓存进 `pendingText` 后再无消费方 → 朗读逻辑死锁；且 `onUnavailable` 提示只在校验 `onInit` 失败时才触发，这条路径不回调，用户连提示都看不到。
- **Fix**：
  1. 引擎切换改为**构造时指定引擎** `new TextToSpeech(ctx, listener, engine)`（可靠重入 `onInit`，消费 pending）；
  2. 新增三层兜底——引擎初始化失败 / 语言不支持(`isLanguageAvailable`) / `speak()` 返回 ERROR → 自动回退系统默认引擎并提示「所选语音库不可用，已使用系统默认语音朗读」；
  3. 设置页新增「测试中文朗读 / 测试英文朗读」按钮，选完引擎可当场验证能否出声；
  4. `MainActivity.onResume()` 重新注册"不可用"提示，避免被设置页的 Toast 覆盖。
- **Files changed**：`TtsManager.java`(重写切换+兜底)、`TtsSettingsActivity.java`(试听按钮+回调)、`MainActivity.java`(onResume)、`activity_tts_settings.xml`(试听区块)、`strings.xml`(试听文案 + tts_unavailable 文案)、`build.gradle.kts`(1.5.2 / versionCode 11)。
- **Build**: release → `dist/写读板-v1.5.2-release.apk` (SHA-256 `f8bbb013cb41bb6999faf19037a63dd7911711de42ba4ecfc5dec1cb95249a62`); debug → `dist/写读板-v1.5.2-debug.apk` (SHA-256 `13f09848ad8a0d4c640c2c73083a448109661ec7d37682d8e8065d070eed25c1`).

### v1.5.3 TTS silent-failure fallback (2026-10-29)

- **Bug（真机复现）**：v1.5.2 已能列出并切换语音库，但选中**讯飞语音引擎**后写字不朗读；选**豆芽 TTS** 正常。属与 v1.5.2「死锁」不同的**第二类失败**。
- **Root cause**：讯飞等云端/厂商 TTS 引擎在第三方 App 里 `onInit()` 返回 SUCCESS（能初始化），但 `speak()` **既不真正播放、也不回调 `onError`/`onStart`**（静默失败）。旧代码只看 `speak()` 是否抛异常，调用无异常即视为成功 → 静默失败、无回退、无提示。
- **Fix（`TtsManager.java`）**：
  1. 新增**出声超时检测**（3 秒）：`speak` 后若未收到 `onStart`（引擎真正开始播放）即判定该引擎未出声；
  2. 自动**回退系统默认引擎**朗读并提示「所选语音库无法朗读，已改用系统默认语音。可在设置→文本转语音改选其他可用语音库」；
  3. **会话失效记忆**：首次失灵的引擎记入本会话失效表，之后直接回退不再空等；重启 App 清空（装好该引擎语音包后可重新生效）；
  4. 设置页「测试中文/英文朗读」同源逻辑，选了不能用的引擎当场提示；
  5. `tts_unavailable` 文案优化。
- **Files changed**：`TtsManager.java`（出声超时检测+回退+失效记忆）、`strings.xml`（`tts_unavailable`）、`build.gradle.kts`（1.5.3 / versionCode 12）。
- **Tested on**：华为 PCT-AL10 (Android 10)。识别/分模式/画布逻辑未改动。
- **Build**: release → `dist/写读板-v1.5.3-release.apk` (SHA-256 `8dfd3a8754528cec32cdd171363cbc77f53812e745f12e5cfb67271daafcea68`); debug → `dist/写读板-v1.5.3-debug.apk` (SHA-256 `667a193cdfbcdb7671a03abc26a7c9aaf72783f108657502f2a1bb9150e5f5a7`).
