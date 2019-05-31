package com.xingen.hookdemo;

import android.app.Application;
import android.content.Context;

import me.weishu.reflection.Reflection;

/**
 * @author HeXinGen
 * date 2019/5/31.
 */
public class BaseApplication extends Application {
    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        Reflection.unseal(base);
    }

    @Override
    public void onCreate() {
        super.onCreate();
    }
}
