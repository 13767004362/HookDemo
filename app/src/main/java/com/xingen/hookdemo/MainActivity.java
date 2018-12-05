package com.xingen.hookdemo;

import android.app.Activity;
import android.content.Context;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;

import com.xingen.hookdemo.utils.Utils;

import java.lang.reflect.Method;

import dalvik.system.DexClassLoader;

public class MainActivity extends Activity implements View.OnClickListener {

    private String dexPath;
    private String fileName = "plugin.apk";
    private String cacheDir;
    private DexClassLoader dexClassLoader;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(newBase);
        this.dexPath = Utils.copyFiles(newBase, fileName);
        this.cacheDir = Utils.getCacheDir(newBase).getAbsolutePath();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        findViewById(R.id.main_btn).setOnClickListener(this);
        this.dexClassLoader = new DexClassLoader(dexPath, cacheDir, null, this.getClassLoader());
        initHook();
    }

    private void initHook() {
        try{
            //通过dexClassLoader加载指定包名的类
            Class<?> mClass = dexClassLoader.loadClass("com.xingen.plugin.PluginClient");
            Method method= mClass.getDeclaredMethod("init");
            method.setAccessible(true);
            method.invoke(null);
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    @Override
    public void onClick(View v) {
        try {
            //通过dexClassLoader加载指定包名的类
            Class<?> mClass = dexClassLoader.loadClass("com.xingen.plugin.PluginClient");
            Method method = mClass.getDeclaredMethod("startMessageDialog", Activity.class);
            method.setAccessible(true);
            method.invoke(null, this);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
