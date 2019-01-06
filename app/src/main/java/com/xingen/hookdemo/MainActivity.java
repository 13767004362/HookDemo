package com.xingen.hookdemo;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import com.xingen.hookdemo.hook.activity.AMSHook;
import com.xingen.hookdemo.hook.activity.TargetActivity;
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
        findViewById(R.id.main_load_plugin_btn).setOnClickListener(this);
        findViewById(R.id.main_hook_activity_btn).setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {

        switch (v.getId()){
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
    private void loadTargetActivity(){
        if (!AMSHook.isIsInIt()){
            String targetPackgaeName="com.xingen.hookdemo";
            AMSHook.init(targetPackgaeName);
        }
        startActivity(new Intent(this, TargetActivity.class));
    }

    /**
     * 加载插件
     */
    private void loadPlugin(){
        if (dexClassLoader==null){ //初始化，加载插件
            this.dexClassLoader = new DexClassLoader(dexPath, cacheDir, null, this.getClassLoader());
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
