# 写读板 v1.5.2 发布说明

> 发布日期：2026-10-29
> 类型：**TTS 朗读修复（紧急）**
> 升级自：v1.5.1（v1.5.1 已解决"列不出语音库"，但引入"选了语音库不朗读"的死锁）

## 一、本次修复的问题

v1.5.1 真机复现并修复了"设置里列不出手机语音库"。但随后真机测试发现：
**一旦在「设置 → 文本转语音」里选中某个语音库，App 就再也不朗读了** —— 静默、无任何提示。

## 二、根因

`TtsManager` 切换引擎使用废弃 API `TextToSpeech.setEngineByPackageName()`：
- 该 API 在 Android 上**不会再次触发 `onInit` 回调**；
- 于是 `switchingEngine` 永远为 true、待朗读文本缓存进 `pendingText` 后再无消费方 → **朗读逻辑死锁**；
- 且"语音不可用"提示 `onUnavailable` 仅在初始 `onInit` 失败时才触发，这条切引擎路径不会回调它，用户连"为什么没声音"都看不到。

## 三、修复内容

| # | 修复 | 说明 |
| --- | --- | --- |
| 1 | 引擎切换改用「构造时指定引擎」 | `new TextToSpeech(ctx, listener, engine)` 会可靠重入 `onInit` 并消费缓存文本，死锁解除 |
| 2 | 引擎不可用自动回退 | 初始化失败 / 语言不支持(`isLanguageAvailable`) / `speak()` 返回 ERROR → 自动回退系统默认引擎朗读 |
| 3 | 回退时给出提示 | 状态条/Toast 提示「所选语音库不可用，已使用系统默认语音朗读」 |
| 4 | 设置页「试听」按钮 | 新增「测试中文朗读 / 测试英文朗读」，选完引擎可当场验证能否出声 |
| 5 | 主页恢复提示 | `MainActivity.onResume()` 重新注册"不可用"提示，避免被设置页临时 Toast 覆盖 |

## 四、改动文件

- `android/app/src/main/java/com/shiyu/hccr/TtsManager.java` — 重写引擎切换与朗读兜底
- `android/app/src/main/java/com/shiyu/hccr/TtsSettingsActivity.java` — 试听按钮 + 不可用 Toast
- `android/app/src/main/java/com/shiyu/hccr/MainActivity.java` — `onResume()` 重注册提示
- `android/app/src/main/res/layout/activity_tts_settings.xml` — 试听按钮区
- `android/app/src/main/res/values/strings.xml` — 试听文案 + `tts_unavailable` 文案
- `android/app/build.gradle.kts` — versionName 1.5.2 / versionCode 11

识别逻辑、分模式识别、书写画布等均未改动。

## 五、安装与校验

| 文件 | SHA-256 |
| --- | --- |
| `dist/写读板-v1.5.2-release.apk` | `f8bbb013cb41bb6999faf19037a63dd7911711de42ba4ecfc5dec1cb95249a62` |
| `dist/写读板-v1.5.2-debug.apk` | `13f09848ad8a0d4c640c2c73083a448109661ec7d37682d8e8065d070eed25c1` |

沿用原签名证书（writeread-release.jks），可覆盖升级 v1.5.1。

```bash
adb install -r 写读板-v1.5.2-release.apk
```

## 六、真机验证清单

- [ ] 进「设置 → 文本转语音」，能列出手机全部语音库
- [ ] 选某语音库 → 点「测试中文朗读 / 测试英文朗读」→ 能听到该引擎声音
- [ ] 选无效引擎 → 弹出「所选语音库不可用，已使用系统默认语音朗读」且仍能听到系统默认声
- [ ] 选「跟随系统默认」+ 回写字板 → 写字能正常朗读
- [ ] 选有效引擎 + 回写字板 → 写字用该引擎朗读
