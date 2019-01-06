package com.xingen.plugin.hook.classloader;

import java.lang.reflect.Field;

/**
 * Created by ${HeXinGen} on 2018/12/5.
 * blog博客:http://blog.csdn.net/hexingen
 */

public class HookManager {


    public static void init() {
        try {
            //获取到ActivityThread
            Class<?> ActivityThreadClass = Class.forName("android.app.ActivityThread");
            Field sCurrentActivityThreadField = ActivityThreadClass.getDeclaredField("sCurrentActivityThread");
            sCurrentActivityThreadField.setAccessible(true);
            Object ActivityThread = sCurrentActivityThreadField.get(null);
            //hook,替换掉Instrumentation
            Field mInstrumentationField = ActivityThreadClass.getDeclaredField("mInstrumentation");
            mInstrumentationField.setAccessible(true);
            InstrumentationFilter instrumentationFilter = new InstrumentationFilter();
            mInstrumentationField.set(ActivityThread, instrumentationFilter);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
