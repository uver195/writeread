package com.shiyu.hccr;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.text.TextUtils;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 统一文本转语音管理（单例，v1.5.7）。
 *
 * 用户需求（v1.5.7）：
 *  1) 语音切换「合并为一个」——中文、英文共用同一个语音库选择；
 *  2) 去掉「发音（voice）」选项；
 *  3) 增加「语速」「音调」两个进度条调节；
 *  4) 切换只在「写读板」内部生效，**不修改系统默认 TTS 引擎**、不改系统"文字转语音"设置。
 *
 * 关键实现：
 *  - 使用 new TextToSpeech(ctx, listener, enginePkg) 创建「应用私有」实例：
 *    指定引擎仅作用于本实例，不调用 setEngineByPackageName()，不写 Settings.Secure，
 *    因此系统默认引擎保持不变，其它 App 不受影响。
 *  - 单选「引擎」+「语速」+「音调」三项偏好存 SharedPreferences；引擎为空即「跟随系统默认」。
 *  - 安全网：所选自定义引擎若在 SPEAK_TIMEOUT_MS 内未真正出声（onStart 未回调），
 *    在应用内临时回退到系统默认引擎朗读一次并提示；全程不改动任何系统设置。
 */
public class TtsManager implements TextToSpeech.OnInitListener {

    private static final String TAG = "TtsManager";
    private static final String UTTERANCE_ID = "writeread_speech";
    private static final long SPEAK_TIMEOUT_MS = 4000;

    private static final String PREF_NAME = "tts_prefs";
    /** 单一语音库（引擎）偏好；空 = 跟随系统默认 */
    public static final String PREF_ENGINE = "engine";
    /** 语速，默认 1.0 */
    public static final String PREF_RATE   = "rate";
    /** 音调，默认 1.0 */
    public static final String PREF_PITCH  = "pitch";

    private static TtsManager s;
    private final Context app;
    private TextToSpeech tts;
    private boolean ready;
    /** 当前实例绑定的引擎包名；null/空 = 系统默认 */
    private String currentEngine;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private String pendingText;
    private String pendingMode;
    private boolean pendingFallback;
    private boolean utteranceStarted;
    private Runnable speakTimeoutTask;

    private String lastMode;
    private final Set<String> failedEngines = new HashSet<>();

    private Runnable onFinished;
    private Runnable onUnavailable;

    // ---------------- 单例 ----------------

    public static synchronized TtsManager get(Context c) {
        if (s == null) s = new TtsManager(c.getApplicationContext());
        return s;
    }

    private TtsManager(Context app) {
        this.app = app;
        ensureInstance(null);   // 默认先绑定系统默认引擎
    }

    // ---------------- 偏好（与设置页共享） ----------------

    static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static String prefEngine(Context c) {
        return prefs(c).getString(PREF_ENGINE, "");
    }

    public static void saveEngine(Context c, String engine) {
        prefs(c).edit().putString(PREF_ENGINE, engine == null ? "" : engine).apply();
    }

    public static float prefRate(Context c) {
        return prefs(c).getFloat(PREF_RATE, 1.0f);
    }

    public static void saveRate(Context c, float rate) {
        prefs(c).edit().putFloat(PREF_RATE, rate).apply();
    }

    public static float prefPitch(Context c) {
        return prefs(c).getFloat(PREF_PITCH, 1.0f);
    }

    public static void savePitch(Context c, float pitch) {
        prefs(c).edit().putFloat(PREF_PITCH, pitch).apply();
    }

    // ---------------- 对外接口 ----------------

    public void setOnUtteranceFinished(Runnable r) { onFinished = r; }
    public void setOnUnavailable(Runnable r) { onUnavailable = r; }
    public boolean isReady() { return ready && tts != null; }

    /** 清空「本会话内已判定失败」的引擎记忆（用户重新选择引擎时调用）。 */
    public void clearFailures() { failedEngines.clear(); }

    // ---------------- 实例创建（应用私有，不动系统默认） ----------------

    private void ensureInstance(String engine) {
        if (tts != null) {
            try { tts.shutdown(); } catch (Throwable ignored) { }
            tts = null;
        }
        ready = false;
        currentEngine = engine;
        try {
            if (engine == null || engine.isEmpty()) {
                tts = new TextToSpeech(app, this);
            } else {
                // 三参构造：仅本实例使用指定引擎，不改变系统默认 TTS 引擎
                tts = new TextToSpeech(app, this, engine);
            }
            Log.d(TAG, "new TextToSpeech engine=" + (engine == null || engine.isEmpty() ? "SYSTEM_DEFAULT" : engine));
        } catch (Throwable e) {
            Log.e(TAG, "create TTS failed", e);
            tts = null;
        }
    }

    @Override
    public void onInit(int status) {
        if (status != TextToSpeech.SUCCESS || tts == null) {
            ready = false;
            Log.w(TAG, "onInit failed status=" + status + " engine=" + currentEngine);
            // 指定引擎初始化失败 -> 应用内回退系统默认再试一次（系统设置不变）
            if (currentEngine != null && !currentEngine.isEmpty()) {
                ensureInstance(null);
                return;
            }
            notifyUnavailable();
            return;
        }
        ready = true;
        Log.d(TAG, "onInit ok engine=" + (currentEngine == null || currentEngine.isEmpty() ? "SYSTEM_DEFAULT" : currentEngine));
        try {
            tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override public void onStart(String id) { utteranceStarted = true; cancelSpeakTimeout(); }
                @Override public void onDone(String id)  { cancelSpeakTimeout(); if (onFinished != null) handler.post(onFinished); }
                @Override public void onError(String id) { cancelSpeakTimeout(); if (onFinished != null) handler.post(onFinished); }
            });
        } catch (Throwable ignored) { /* 极少数引擎不支持回调，靠超时兜底 */ }

        if (pendingText != null) {
            String t = pendingText, m = pendingMode;
            boolean fb = pendingFallback;
            pendingText = null;
            pendingMode = null;
            pendingFallback = false;
            speakInternal(t, m, fb);
        }
    }

    // ---------------- 朗读 ----------------

    public void speak(String text, String mode) {
        if (text == null || text.isEmpty()) return;
        if (tts == null) ensureInstance(currentEngine);
        if (!ready) {
            pendingText = text;
            pendingMode = mode;
            pendingFallback = false;
            return;
        }
        speakInternal(text, mode, false);
    }

    private void speakInternal(String text, String mode, boolean fromFallback) {
        if (tts == null) { notifyUnavailable(); return; }
        lastMode = mode;
        boolean cn = HomeActivity.MODE_CHINESE.equals(mode);

        // 回退朗读：直接用当前（系统默认）实例，不再切引擎
        if (fromFallback) {
            applyParams(cn);
            doSpeakWithTimeout(text, currentEngine);
            return;
        }

        String targetEngine = prefEngine(app);   // 单一引擎，中英文共用

        // 本会话已判定该自定义引擎无法出声：直接应用内回退，避免每次白等超时
        if (targetEngine != null && !targetEngine.isEmpty() && failedEngines.contains(targetEngine)) {
            pendingText = text;
            pendingMode = mode;
            pendingFallback = true;
            ensureInstance(null);
            return;
        }

        // 需要切换引擎？（当前实例引擎 != 目标引擎）
        if (!TextUtils.equals(norm(currentEngine), norm(targetEngine))) {
            pendingText = text;
            pendingMode = mode;
            pendingFallback = false;
            ensureInstance(targetEngine);
            return; // onInit 重入后消费 pending
        }

        applyParams(cn);
        doSpeakWithTimeout(text, targetEngine);
    }

    private static String norm(String s) { return s == null ? "" : s; }

    /** 应用朗读参数：语言 + 语速 + 音调（全部来自应用内设置，不影响系统）。 */
    private void applyParams(boolean cn) {
        if (tts == null) return;
        Locale loc = cn ? Locale.SIMPLIFIED_CHINESE : Locale.US;
        try { tts.setLanguage(loc); } catch (Throwable ignored) { }
        try { tts.setSpeechRate(prefRate(app)); } catch (Throwable ignored) { }
        try { tts.setPitch(prefPitch(app)); } catch (Throwable ignored) { }
    }

    private int doSpeak(String text) {
        try {
            if (Build.VERSION.SDK_INT >= 21) {
                return tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID);
            }
            return tts.speak(text, TextToSpeech.QUEUE_FLUSH, null);
        } catch (Throwable e) {
            return TextToSpeech.ERROR;
        }
    }

    private void doSpeakWithTimeout(String text, String engineUsed) {
        int r = doSpeak(text);
        Log.d(TAG, "doSpeak r=" + r + " engine=" + engineUsed);
        if (r == TextToSpeech.ERROR) {
            handleNoSound(text, engineUsed);
            return;
        }
        scheduleSpeakTimeout(text, engineUsed);
    }

    private void scheduleSpeakTimeout(final String text, final String engineUsed) {
        cancelSpeakTimeout();
        utteranceStarted = false;
        speakTimeoutTask = () -> {
            if (utteranceStarted) return;   // 已真正开始播放，取消误判
            Log.w(TAG, "speak timeout engine=" + engineUsed);
            handleNoSound(text, engineUsed);
        };
        handler.postDelayed(speakTimeoutTask, SPEAK_TIMEOUT_MS);
    }

    private void cancelSpeakTimeout() {
        if (speakTimeoutTask != null) {
            handler.removeCallbacks(speakTimeoutTask);
            speakTimeoutTask = null;
        }
    }

    /**
     * 无声处理：
     *  - 自定义引擎失败 -> 记入失败集，并在应用内临时改用系统默认朗读一次（不改系统设置），提示用户；
     *  - 本身即系统默认仍失败 -> 仅提示用户去系统设置安装/更换语音包。
     */
    private void handleNoSound(String text, String engineUsed) {
        cancelSpeakTimeout();
        boolean custom = engineUsed != null && !engineUsed.isEmpty();
        if (custom) {
            failedEngines.add(engineUsed);
            notifyUnavailable();
            pendingText = text;
            pendingMode = lastMode;
            pendingFallback = true;
            ensureInstance(null);   // 应用内回退系统默认
        } else {
            notifyUnavailable();
        }
    }

    private void notifyUnavailable() {
        if (onUnavailable != null) handler.post(onUnavailable);
    }

    public void shutdown() {
        cancelSpeakTimeout();
        if (tts != null) {
            try { tts.stop(); tts.shutdown(); } catch (Throwable ignored) { }
            tts = null;
        }
        ready = false;
    }

    // ---------------- 枚举手机已安装的语音引擎（供设置页使用） ----------------

    public interface EnginesCallback { void onEngines(List<TextToSpeech.EngineInfo> engines); }

    /** 枚举手机已安装、可被本应用使用的 TTS 引擎（异步，主线程回调）。 */
    public static void loadEngines(Context ctx, EnginesCallback cb) {
        final Context app = ctx.getApplicationContext();
        final TextToSpeech[] ref = new TextToSpeech[1];
        final boolean[] done = {false};
        TextToSpeech.OnInitListener l = status -> {
            if (done[0]) return;
            done[0] = true;
            List<TextToSpeech.EngineInfo> out = new ArrayList<>();
            if (status == TextToSpeech.SUCCESS && ref[0] != null) {
                try { out.addAll(ref[0].getEngines()); } catch (Throwable ignored) { }
            }
            try { if (ref[0] != null) ref[0].shutdown(); } catch (Throwable ignored) { }
            cb.onEngines(out);
        };
        try {
            ref[0] = new TextToSpeech(app, l);
        } catch (Throwable e) {
            cb.onEngines(new ArrayList<>());
        }
    }
}
