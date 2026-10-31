# 写读板 v1.5.1 发布说明（语音库切换修复）

> 发布日期：2026-10-29
> 基于 v1.5.0，仅修复「文本转语音设置页列不出手机语音库、无法切换语音」的问题。

## 1. 问题现象（真机复现）

在华为 PCT-AL10（Android 10）上安装 v1.5.0 并打开「设置 → 文本转语音」：
- 界面里「中文朗读语音」「英文朗读语音」两组**只剩「跟随系统默认」一项**；
- 任何手机上已安装的语音库（如 Google 文字转语音、讯飞等）都**列不出来**，无法进行语音切换；
- 即用户反馈的"切换语音的地方不可以使用，找不到手机上安装的语音库"。

## 2. 根因

旧版 `TtsSettingsActivity` 通过 `tts.getVoices()` 枚举可选发音。问题在于：
- Android 上手机安装的"语音库"大多数以**独立的 TTS 引擎**形式存在（Google TTS、讯飞、各厂商语音…）；
- **厂商自带的默认引擎（如华为 HUAWEI TTS）往往不实现 `getVoices()` 这个 API**，调用返回空集；
- 因此列表为空，用户只能看到"跟随系统默认"，无法选择具体语音。

`getVoices()` 仅在 Google 文字转语音引擎下可靠，在大量国产 ROM 的默认引擎下为空——这是 Android TTS 的已知兼容性坑。

## 3. 修复方案

改为**按「TTS 引擎（语音库）」枚举**，引擎可靠后再列其内部发音：

1. 用 `TextToSpeech.getEngines()` 枚举手机已安装的所有 TTS 引擎（语音库）——该 API 在厂商 ROM 上可靠；
2. 用户选中某引擎后，用**临时 `TextToSpeech` 实例**（指定该引擎）探测其 `getVoices()`，并按语言前缀 `zh`/`en` 过滤出中文/英文发音；
3. 中文朗读、英文朗读可分别保存「引擎包名 + 发音名」；二者皆空即"跟随系统默认"；
4. 朗读时 `TtsManager` 按保存的偏好对该 `TextToSpeech` 实例调用 `setEngineByPackageName`（异步重入 `onInit`）再 `setVoice`/`setLanguage`；
5. 设置页新增「打开系统文字转语音设置」按钮，可一键跳转系统 TTS 设置安装/切换语音包。

## 4. 改动文件清单

| 文件 | 改动 |
| --- | --- |
| `android/app/src/main/java/com/shiyu/hccr/TtsManager.java` | **新增** 统一 TTS 管理单例：生命周期、引擎异步切换、按模式应用偏好、朗读、引擎不可用回调 |
| `android/app/src/main/java/com/shiyu/hccr/TtsSettingsActivity.java` | **重写** 两级选择（语音库引擎 + 发音）+ 系统设置按钮 |
| `android/app/src/main/java/com/shiyu/hccr/MainActivity.java` | TTS 部分改用 `TtsManager`（去掉 `getVoices()` 依赖） |
| `android/app/src/main/res/layout/activity_tts_settings.xml` | 改为 引擎组 + 发音组（中/英各一组）+ 系统设置按钮 |
| `android/app/src/main/res/values/strings.xml` | 新增 tts_engine_default / tts_voice_default / tts_loading_voices / tts_no_voices / tts_open_system_settings / tts_engine_label / tts_voice_label / tts_saved |
| `android/app/build.gradle.kts` | versionName 1.5.1, versionCode 10 |

未改：JNI runtime、预处理、`preprocess.c`、NCNN 模型、识别分模式逻辑、首页/关于/帮助结构。

## 5. 构建产物

| 文件 | 大小 | SHA-256 |
| --- | --- | --- |
| `dist/写读板-v1.5.1-release.apk`（推荐） | 30.4 MB | `e44c80c654b624259509b4bd1d568a99534822969393ea74ad43777c3ffcabdb` |
| `dist/写读板-v1.5.1-debug.apk`（调试版） | 31.0 MB | `cc47f6890e75ea56c9c28916fd24397fa7582ddb63bff1a05c70839d8acd6f62` |

编译 `BUILD SUCCESSFUL`（沿用原签名证书 `writeread-release.jks`，可覆盖升级 v1.5.0）。

## 6. 真机验证步骤（建议）

1. 卸载旧 v1.5.0 或直接覆盖安装 v1.5.1（同签名可升级）；
2. 打开写读板 → 设置 → 文本转语音；
3. 确认「中文朗读语音 / 英文朗读语音」下**能列出手机已安装的语音库（引擎）**，如「Google 文字转语音引擎」「讯飞语音」等；
4. 选一个引擎（如 Google 文字转语音引擎），下方「发音」应列出该引擎的中文/英文发音供选择；
5. 选完返回首页 → 汉字学习，写一个汉字（如"中"），确认朗读使用了所选语音；
6. 英文学习写一个字母（如 A），确认朗读使用英文发音；
7. 点「打开系统文字转语音设置」应能跳转系统 TTS 设置页；
8. 若不选择任何引擎（默认"跟随系统默认"），朗读行为与系统当前 TTS 设置一致。

## 7. 文档索引

- 用户文档：`docs/HELP.md`（含"如何切换语音库"说明）
- 软件介绍：`docs/FEATURES.md`（已更新 TTS 描述与下载表）
- 开发者交接：`docs/HANDOVER.md`（含 v1.5.1 修复章节与 TTS 架构）
- 工作目录综合说明：`写读板v1.5.1_语音修复说明.md`
