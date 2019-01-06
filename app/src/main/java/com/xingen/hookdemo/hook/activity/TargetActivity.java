package com.xingen.hookdemo.hook.activity;

import android.app.Activity;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.view.Gravity;
import android.widget.TextView;

/**
 * Created by ${HeXinGen} on 2019/1/6.
 * blog博客:http://blog.csdn.net/hexingen
 */

public class TargetActivity extends Activity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TextView textView=new TextView(this);
        setContentView(textView);
        textView.setText("插件化之Hook，启动不在AndroidManifest.xml中声明的Activity");
        textView.setGravity(Gravity.CENTER);
    }
}
