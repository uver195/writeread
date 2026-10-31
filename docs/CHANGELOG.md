# 写读板 · 更新说明 / 版本日志

## v1.4.0 (2026-10-28) Fix low-confidence prompt + auto-clear

> Fixed: low-confidence recognition had no voice prompt and did not auto-clear canvas.
> Now speaks "please rewrite" and auto-clears so user can immediately write again.

### Bug fixes

| Issue | Fix |
| --- | --- |
| No voice on low-confidence | Low-confidence branch now calls speak() to read "please rewrite" aloud |
| Canvas not clearing on low-confidence | Removed early return; now falls through to scheduleClear() |
| False recognition on uncertain input | Raised CONFIDENCE_THRESHOLD from 0.35 to 0.45 |

### Checksums

| File | SHA-256 |
| --- | --- |
| v1.4.0-release.apk | 9375734A1E7AC10CB1B332F0D6780902132B9C3F52A7F4E7B5C894510E60B679 |
| v1.4.0-debug.apk | F6060A69DB506F59BE4C693E1CCEF7EF6CCD7047B149445EDC270FEF39C2FF70 |

---

## v1.3.1 (2026-10-28) English model upgraded to EMNIST real handwriting

> English letter model retrained with EMNIST Letters (124,800 real handwritten samples).
> Accuracy on real handwriting: 93.88%, much better generalization than v1.3.0 font-based model.

### Changes

| Category | Details |
| --- | --- |
| English model upgrade | Retrained on EMNIST Letters (124,800 train + 20,800 test real handwritten samples) |
| Accuracy | 93.88% on real handwritten test set |
| Generalization | Significantly better for finger-written letters on phone screen |
| Model size | Unchanged: NCNN FP16, ~804KB |
| Other features | Unchanged: dual-model cascade, low-confidence prompt, Chinese unaffected |

### Checksums

| File | SHA-256 |
| --- | --- |
| v1.3.1-release.apk | D842E181E430FFC12F211D673E7BB58AB4A99CC98705DAE434AF6798231EDA0F |
| v1.3.1-debug.apk | E0F2898A2E6936A98F00AA248DF42B74B3FFC92B3FB3C19F0B19C9B2F60D9FA9 |

---

## v1.3.0 (2026-10-28) English letter recognition + accuracy improvement

> Added 26 English letter (A-Z) offline recognition and TTS; dual-model cascade inference, Chinese recognition unaffected.

### Changes

| Category | Details |
| 
### Installation packages and checksums

| File | Size | SHA-256 |
| --- | --- | --- |
| 写读板-v1.3.0-release.apk | 31.9MB | 82BB6B1E1898DC6801C568183E71D1C94E10500EE6CEDF0F339F78779E2124FE |
| 写读板-v1.3.0-debug.apk | 32.5MB | 8A2019CF17CAF035E483EFDC641921A7A9AC9F8F8FDAB2C1A83D4E2DD2A30B7C |

--- | --- |
| English letter recognition | New independent English model (PlainCNN, 26 classes, EMNIST Letters trained, NCNN FP16 deployed), supports handwritten A-Z recognition and TTS |
| Dual-model cascade | Chinese and English models run simultaneously, compare top-1 confidence: use English when clearly higher, otherwise Chinese (+0.1 bias), Chinese accuracy unaffected |
| English TTS | Recognized letter read as "letter X" (e.g. write A -> "letter A"), same system TTS engine |
| Low-confidence handling | When both models below threshold (0.35), prompts "please rewrite" instead of wrong read, reducing misrecognition |
| Accuracy improvement | Low-confidence results no longer force-read; confusing pairs (O/mouth, I/stroke) resolved via dual-model comparison |
| Offline guarantee | English model also local NCNN inference, no network, no permissions |

### Technical details

- English model: PlainCNN (5-stage CNN + GAP + Linear), input [1,64,64], output 26 classes, ~1.1M params
- Training data: EMNIST Letters (~140k train + ~15k test)
- Preprocessing: same C pipeline as Chinese (bbox crop -> resize 56 -> center 64x64 -> invert+normalize)
- Deployment: NCNN FP16, ~1.1MB, ~1ms inference
- Cascade: dual-model parallel inference (~3-5ms total), English used when confidence > 0.35 and > Chinese + 0.1

---

## v1.2.0（2026-10-23）正式版

> 本版为面向公开发布的首个正式版。已在真机（华为 PCT-AL10 / Android 10）实测通过。

### 本版本改进

| 类别 | 内容 |
| --- | --- |
| 组词词库升级 | 重做组词词典：排除人名（如“卜学亮”）等劣质词条，优先双字常用词，设最低词频门槛；覆盖 3755 字中的 87.6%。例：中→“中国的中”、天→“天下的天”、卜→“卜筮的卜”、长→“长江的长”、弓→“弓箭的弓”，朗读更自然准确 |
| 多厂商 TTS 兼容 | 重写语音初始化：优先遍历系统语音选取“中文 + 无需联网”的本地离线语音包；找不到时按 zh-CN/zh/zh-TW 顺序回退设置中文语言，适配华为/小米/红米/三星/OPPO/vivo/魅族等各厂商 TTS 引擎；引擎不可用或无中文语音时给出清晰引导提示 |
| 离线保障 | 仍不申请任何联网权限；识别用本地 NCNN INT8，朗读用本地语音包 |
| 其它 | 继承 v1.1.1 的“停笔静默后识别整字”与“朗读后兜底自动清空画布” |

### 安装包与校验（SHA-256）

| 文件 | 校验值 |
| --- | --- |
| `dist/写读板-v1.2.0-release.apk`（31.1MB，签名发布版，推荐） | `432B90270448902CF3DF39CEDB9CCBA7921508CD13DC8BD9860FBEDFC474A367` |
| `dist/写读板-v1.2.0-debug.apk`（31.7MB，调试版） | `3C98C9024AFF275047486CA5C8D9EA2280841085A7A6CB576E6303F1CA9E17A4` |

### 厂商兼容性说明

| 厂商 / 系统 | 状态 |
| --- | --- |
| 华为 EMUI（Android 10，实测） | ✅ 已实测：启动、全屏、识别、朗读、自动清空均正常 |
| 小米 / 红米（MIUI / HyperOS） | ✅ 预期兼容：TTS 走系统标准接口，优先选离线中文语音；若无声请到 设置→更多设置→语言和输入法→文字转语音 启用引擎并安装中文语音 |
| 三星 OneUI | ✅ 预期兼容：三星 TTS 自带离线中文 |
| OPPO ColorOS / vivo OriginOS / 魅族 Flyme | ✅ 预期兼容：均通过标准 TTS 接口；个别机型需在系统 TTS 设置确认中文语音已安装 |
| 未知厂商 | 未做特殊处理，依赖 Android 标准 TTS/全屏 API；如遇问题以标准设置排查 |

> 说明：本应用仅使用 Android 标准 API（TextToSpeech、WindowInsetsController/systemUiVisibility），
> 不针对单一厂商做 hack；不同厂商差异主要在“是否预装中文离线语音包”，可通过系统 TTS 设置解决。

### 已知限制（持续跟踪）

1. 按单字识别设计，一次只写一个字；多字同屏会影响识别效果；
2. 停笔静默期默认 1.5 秒，若单笔画间隔超过该值，仍可能在写完一笔后触发一次识别；
3. 朗读依赖设备内置中文 TTS 语音包；个别机型首次需在系统 TTS 设置中确认中文语音已安装；
4. 约 12% 的罕见字无合适常用词，朗读回退为“X字的X”；
5. 传感器旋转模式在少量平板平放时可能横竖切换较敏感；
6. APK 支持 `arm64-v8a` 与 `x86_64`（未包含 32 位 ABI）；
7. 无云备份、无多字连写、无笔迹保存功能（属版本规划外）。

---

## v1.1.1（2026-10-23）识别时机 + 自动清空 修复版

- 多笔画字：由“抬手即识别”改为“停笔静默 1.5 秒后识别整字”，避免“张=弓+长”只写完“弓”就被误识别；
- 自动清空：增加不依赖 TTS onDone 回调的兜底定时清空（2.5 秒），修复部分机型（华为 EMUI）不清空画布的问题。

## v1.0.1（2026-10-23）崩溃修复版

- 修复 Android 10（API 29）及以下点击图标即闪退：`InputStream.readAllBytes()` 仅 API 33+ 可用，改为自实现兼容读取，Android 7（API 24）起全兼容。

## v1.0.0（2026-10-22）首个交付版

- 基于开源项目 Ismantic/Handwritten（CASIA HWDB1.1 → PyTorch → NCNN INT8 → Android）本地改造；
- 全屏沉浸、横竖屏自适应、移除冗余控件、接入系统 TTS 自动拼句“XX的XX”、朗读后自动清空画布、全程离线无联网权限。

> 请使用最新正式版 **v1.2.0**。

### 后续规划（非承诺）

- 笔迹保存/回放、多字整行识别；
- 朗读语速/音色设置、“XX的XX”句式可配置；
- 生字本、跟读打分等练字增值功能。