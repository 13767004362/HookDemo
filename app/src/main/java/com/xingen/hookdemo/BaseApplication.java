package com.xingen.hookdemo;

import android.app.Application;
import android.content.Context;

import com.xingen.hookdemo.hook.classLoader.ClassLoaderHookManager;
import com.xingen.hookdemo.utils.Utils;

import me.weishu.reflection.Reflection;

/**
 * @author HeXinGen
 * date 2019/5/31.
 */
public class BaseApplication extends Application {
    private static  BaseApplication instance;
    private     String zipFilePath;
    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        Reflection.unseal(base);
    }

    @Override
    public void onCreate() {
        super.onCreate();
         instance=this;
        loadPluginDex();
    }


    public String getZipFilePath() {
        return zipFilePath;
    }

    public static BaseApplication getInstance() {
        return instance;
    }

    private void loadPluginDex() {
        // 先拷贝assets 下的apk，写入磁盘中。
        zipFilePath = Utils.copyFiles(this, PluginConfig.apk_file_name);
        String optimizedDirectory = Utils.getCacheDir(this).getAbsolutePath();
        // 加载插件dex
        ClassLoaderHookManager.init(zipFilePath, optimizedDirectory);
    }
}
