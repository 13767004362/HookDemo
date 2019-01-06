package com.xingen.plugin.hook.classloader;

import android.app.Activity;
import android.app.Instrumentation;
import android.os.Bundle;

/**
 * Created by ${HeXinGen} on 2018/12/5.
 * blog博客:http://blog.csdn.net/hexingen
 *
 *
 * 自定义Instrumentation子类，替换掉系统ActivityThread中的Instrumentation对象。
 */

public class InstrumentationFilter  extends Instrumentation{


    /**
     *  在调用Activity的.onCreate()中替换掉宿主的ClassLoader。
     *  从而替换成插件的ClassLoader。
     * @param activity
     * @param icicle
     */
    @Override
    public void callActivityOnCreate(Activity activity, Bundle icicle) {
        try {
            ClassLoader classLoader=InstrumentationFilter.class.getClassLoader();
            ContextImplUtils.hookClassLoader(activity.getBaseContext(),classLoader);
        }catch (Exception e){
            e.printStackTrace();
        }finally {
            super.callActivityOnCreate(activity, icicle);
        }
    }
}
