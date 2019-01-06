package com.xingen.hookdemo.hook.activity;

import android.content.Intent;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

/**
 * Created by ${HeXinGen} on 2019/1/6.
 * blog博客:http://blog.csdn.net/hexingen
 */

public class IActivityManagerHandler implements InvocationHandler {
    private  Object rawIActivityManager;
    public IActivityManagerHandler(Object rawIActivityManager) {
        this.rawIActivityManager = rawIActivityManager;

    }
    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        //替换掉交给AMS的intent对象，将里面的TargetActivity的暂时替换成已经声明好的替身StubActivity
        if ("startActivity".equals(method.getName())){
            AMSHook.Utils.replaceIntent(args);
        }
        return method.invoke(rawIActivityManager,args);
    }
}
