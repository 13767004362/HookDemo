package com.xingen.plugin.hook;

import android.app.Instrumentation;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Created by ${新根} on 2018/6/9.
 * blog博客:http://blog.csdn.net/hexingen
 */

public class HookManager {

    public static void init(){
        try {
            //先获取当前的ActivityThread对象
            Class<?> activityThreadClass=Class.forName("android.app.ActivityThread");
            Method currentActivityThreadMethod= activityThreadClass.getDeclaredMethod("currentActivityThread");
            currentActivityThreadMethod.setAccessible(true);
            Object currentActivityThread=currentActivityThreadMethod.invoke(null);

            //拿到原始的Instrumentation
            Field mInstrumentationField=activityThreadClass.getDeclaredField("mInstrumentation");
            mInstrumentationField.setAccessible(true);
            Instrumentation mInstrumentation = (Instrumentation) mInstrumentationField.get(currentActivityThread);
            //替换成Hook中的 Instrumentation
            InstrumentationFilter instrumentationFilter=new InstrumentationFilter();
            mInstrumentationField.set(currentActivityThread,instrumentationFilter);
        }catch (Exception e){
            e.printStackTrace();
        }
    }
}
