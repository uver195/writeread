package com.shiyu.hccr;

import android.app.AlertDialog;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;

/**
 * 写读板设置页：文本转语音 / 关于软件 / 帮助说明。
 */
public class SettingsActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
    }

    public void onSettingsItemClick(View v) {
        int id = v.getId();
        if (id == R.id.item_tts) {
            startActivity(new Intent(this, TtsSettingsActivity.class));
        } else if (id == R.id.item_update) {
            UpdateChecker.check(this, false);
        } else if (id == R.id.item_about) {
            showAbout();
        } else if (id == R.id.item_help) {
            startActivity(new Intent(this, HelpActivity.class));
        }
    }

    private void showAbout() {
        String version = "1.5.0";
        try {
            version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException ignored) {
            // 取不到版本号时回退到常量
        }
        String msg = getString(R.string.about_text, version);
        new AlertDialog.Builder(this)
                .setTitle(R.string.about_title)
                .setMessage(msg)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }
}
