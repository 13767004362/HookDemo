package com.xingen.plugin.activity;

import android.app.Activity;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Gravity;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Created by ${HeXinGen} on 2019/1/6.
 * blog博客:http://blog.csdn.net/hexingen
 */

public class TargetActivity extends Activity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Toast.makeText(this,"启动成功TargetActivity",Toast.LENGTH_SHORT).show();
        TextView textView=new TextView(this);
        setContentView(textView);
        textView.setText("插件化之Hook，启动不在AndroidManifest.xml中声明的Activity");
        textView.setGravity(Gravity.CENTER);
    }
}
