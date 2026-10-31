package com.shiyu.hccr;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 文本转语音设置（v1.5.7）。
 *
 * 本版变化：
 *  - 语音切换「合并为一个」：中文、英文共用同一个语音库（引擎）选择；
 *  - 去掉「发音（voice）」选项；
 *  - 新增「语速」「音调」两个进度条。
 *
 * 所有选择仅对写读板生效，**不会修改系统默认 TTS 引擎**，不影响其它应用。
 */
public class TtsSettingsActivity extends Activity {

    private Spinner spEngine;
    private SeekBar sbRate, sbPitch;
    private TextView tvRateValue, tvPitchValue;

    private final List<TextToSpeech.EngineInfo> engines = new ArrayList<>();
    private boolean binding = false;

    private static final int PROGRESS_MAX = 150;   // 0.5x ~ 2.0x

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tts_settings);

        spEngine = findViewById(R.id.spinner_engine);
        sbRate = findViewById(R.id.seek_rate);
        sbPitch = findViewById(R.id.seek_pitch);
        tvRateValue = findViewById(R.id.tv_rate_value);
        tvPitchValue = findViewById(R.id.tv_pitch_value);

        findViewById(R.id.btn_test).setOnClickListener(v -> testSpeak());
        findViewById(R.id.btn_reset).setOnClickListener(v -> resetToSystemDefault());
        findViewById(R.id.btn_open_system).setOnClickListener(v -> openSystemSettings());

        TtsManager.get(this).setOnUnavailable(() -> runOnUiThread(() ->
                Toast.makeText(this, getString(R.string.tts_engine_no_sound), Toast.LENGTH_LONG).show()));

        initSeekBars();
        loadEngineSpinner();
    }

    // ---------------- 语速 / 音调 ----------------

    private void initSeekBars() {
        sbRate.setMax(PROGRESS_MAX);
        sbPitch.setMax(PROGRESS_MAX);

        sbRate.setProgress(rateToProgress(TtsManager.prefRate(this)));
        sbPitch.setProgress(rateToProgress(TtsManager.prefPitch(this)));
        updateRateLabel(progressToRate(sbRate.getProgress()));
        updatePitchLabel(progressToRate(sbPitch.getProgress()));

        sbRate.setOnSeekBarChangeListener(new SimpleSeekListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float v = progressToRate(progress);
                updateRateLabel(v);
                if (fromUser) TtsManager.saveRate(TtsSettingsActivity.this, v);
            }
        });
        sbPitch.setOnSeekBarChangeListener(new SimpleSeekListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float v = progressToRate(progress);
                updatePitchLabel(v);
                if (fromUser) TtsManager.savePitch(TtsSettingsActivity.this, v);
            }
        });
    }

    private static float progressToRate(int progress) { return 0.5f + progress / 100f; }

    private static int rateToProgress(float rate) {
        int p = Math.round((rate - 0.5f) * 100f);
        if (p < 0) p = 0;
        if (p > PROGRESS_MAX) p = PROGRESS_MAX;
        return p;
    }

    private void updateRateLabel(float v) {
        tvRateValue.setText(getString(R.string.tts_rate_value, String.format(Locale.US, "%.1f", v)));
    }

    private void updatePitchLabel(float v) {
        tvPitchValue.setText(getString(R.string.tts_pitch_value, String.format(Locale.US, "%.1f", v)));
    }

    private abstract static class SimpleSeekListener implements SeekBar.OnSeekBarChangeListener {
        @Override public void onStartTrackingTouch(SeekBar seekBar) { }
        @Override public void onStopTrackingTouch(SeekBar seekBar) { }
    }

    // ---------------- 引擎 Spinner（单一） ----------------

    private void loadEngineSpinner() {
        TtsManager.loadEngines(this, list -> runOnUiThread(() -> {
            engines.clear();
            engines.addAll(list);
            rebuildEngineSpinner();
        }));
    }

    private void rebuildEngineSpinner() {
        binding = true;

        List<String> labels = new ArrayList<>();
        labels.add(getString(R.string.tts_follow_system));
        for (TextToSpeech.EngineInfo e : engines) {
            String label = (e.label == null || e.label.isEmpty()) ? e.name : e.label;
            labels.add(label + "（" + e.name + "）");
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spEngine.setAdapter(adapter);

        spEngine.setSelection(indexOfEngine(TtsManager.prefEngine(this)));

        spEngine.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                if (binding) return;
                TtsManager.saveEngine(TtsSettingsActivity.this, engineAt(pos));
                TtsManager.get(TtsSettingsActivity.this).clearFailures();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });

        binding = false;
    }

    private int indexOfEngine(String pkg) {
        if (pkg == null || pkg.isEmpty()) return 0;
        for (int i = 0; i < engines.size(); i++) {
            if (pkg.equals(engines.get(i).name)) return i + 1;
        }
        return 0;
    }

    private String engineAt(int position) {
        if (position <= 0 || position - 1 >= engines.size()) return "";
        return engines.get(position - 1).name;
    }

    // ---------------- 测试 / 重置 / 系统设置 ----------------

    private void testSpeak() {
        TtsManager.get(this).speak("中国的中", HomeActivity.MODE_CHINESE);
    }

    private void resetToSystemDefault() {
        TtsManager.saveEngine(this, "");
        TtsManager.saveRate(this, 1.0f);
        TtsManager.savePitch(this, 1.0f);
        TtsManager.get(this).clearFailures();

        binding = true;
        spEngine.setSelection(0);
        binding = false;
        sbRate.setProgress(rateToProgress(1.0f));
        sbPitch.setProgress(rateToProgress(1.0f));
        updateRateLabel(1.0f);
        updatePitchLabel(1.0f);

        Toast.makeText(this, getString(R.string.tts_reset_done), Toast.LENGTH_SHORT).show();
    }

    private void openSystemSettings() {
        try {
            Intent it = new Intent("com.android.settings.TTS_SETTINGS");
            it.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(it);
        } catch (Throwable e) {
            Toast.makeText(this, getString(R.string.tts_open_system_manual), Toast.LENGTH_LONG).show();
        }
    }
}
