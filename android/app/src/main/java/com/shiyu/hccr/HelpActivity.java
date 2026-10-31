package com.shiyu.hccr;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

/** 帮助说明页：展示内置使用与排错文档（与 docs/HELP.md 保持一致）。 */
public class HelpActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_help);
        TextView tv = findViewById(R.id.help_text);
        tv.setText(R.string.help_content);
    }
}
