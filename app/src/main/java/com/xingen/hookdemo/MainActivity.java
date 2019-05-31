package com.xingen.hookdemo;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.sax.Element;
import android.view.View;

import com.xingen.hookdemo.hook.activity.AMSHook;
import com.xingen.hookdemo.hook.classLoader.ClassLoaderHookManager;
import com.xingen.hookdemo.utils.Utils;

import java.io.File;
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
        this.dexClassLoader = new DexClassLoader(dexPath, cacheDir, null, this.getClassLoader());
        ClassLoaderHookManager.init(new File(dexPath),new File(cacheDir+File.separator+"plugin.dex"),this.getClassLoader());
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        findViewById(R.id.main_load_plugin_btn).setOnClickListener(this);
        findViewById(R.id.main_hook_activity_btn).setOnClickListener(this);

    }

    @Override
    public void onClick(View v) {

        switch (v.getId()) {
            case R.id.main_load_plugin_btn:
                loadPlugin();
                break;
            case R.id.main_hook_activity_btn:


                loadTargetActivity();
                break;

        }

    }

    /**
     * 绕过ams，启动目标的Activity
     */
    private void loadTargetActivity() {
        if (!AMSHook.isIsInIt()) {
            String subPackageName = getPackageName();
            AMSHook.init(subPackageName);
        }
        startActivity(new Intent().setComponent(new ComponentName("com.xingen.plugin", "com.xingen.plugin.activity.TargetActivity")));
    }

    /**
     * 加载插件
     */
    private void loadPlugin() {
        //初始化，加载插件
        try {
            //通过dexClassLoader加载指定包名的类
            Class<?> mClass = dexClassLoader.loadClass("com.xingen.plugin.PluginClient");
            Method method = mClass.getDeclaredMethod("initClassLoader",Context.class);
            method.setAccessible(true);
            method.invoke(null,getBaseContext());
        }catch (Exception e){
            e.printStackTrace();
        }
        //开始显示插件中的DialogFragment
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
