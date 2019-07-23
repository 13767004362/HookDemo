package com.xingen.plugin;

import android.app.Application;
import android.content.Context;
import android.util.Log;

/**
 * @author HeXinGen
 * date 2019/7/22.
 */
public class DelegateApplication extends Application {
private static final String TAG=DelegateApplication.class.getSimpleName();
    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        Log.i(TAG,TAG+"执行 attachBaseContext() ");
    }
}
