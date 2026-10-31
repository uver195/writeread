package com.shiyu.hccr;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

/**
 * 写读板首页：三个分类入口。
 *  - 汉字学习 → 进入手写板（模式=chinese，仅识别中文）
 *  - 英文学习 → 进入手写板（模式=english，仅识别英文）
 *  - 设置     → 设置页（文本转语音 / 关于 / 帮助）
 */
public class HomeActivity extends Activity {

    public static final String EXTRA_MODE = "mode";
    public static final String MODE_CHINESE = "chinese";
    public static final String MODE_ENGLISH = "english";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);
        // v1.5.8：打开首页静默检查更新（仅在发现新版时弹窗，无新版/无网时不出声）。
        UpdateChecker.check(this, true);
    }

    public void onHomeItemClick(View v) {
        int id = v.getId();
        if (id == R.id.item_chinese) {
            startWrite(MODE_CHINESE);
        } else if (id == R.id.item_english) {
            startWrite(MODE_ENGLISH);
        } else if (id == R.id.item_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
        }
    }

    private void startWrite(String mode) {
        Intent it = new Intent(this, MainActivity.class);
        it.putExtra(EXTRA_MODE, mode);
        startActivity(it);
    }
}
