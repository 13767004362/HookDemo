package com.xingen.hookdemo.hook.ams;

import android.content.Context;
import android.content.Intent;

import com.xingen.hookdemo.ProxyApplication;
import com.xingen.hookdemo.hook.service.ServiceHookManager;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

/**
 * Created by ${HeXinGen} on 2019/1/6.
 * blog博客:http://blog.csdn.net/hexingen
 */

public class IActivityManagerHandler implements InvocationHandler {
    private Object rawIActivityManager;
    private Context context;

    public IActivityManagerHandler(Context context,Object rawIActivityManager) {
        this.context=context;
        this.rawIActivityManager = rawIActivityManager;

    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        //替换掉交给AMS的intent对象，将里面的TargetActivity的暂时替换成已经声明好的替身StubActivity
        switch (method.getName()) {
            case "startActivity":
                  AMSHookManager.Utils.replaceActivityIntent(args);
                return method.invoke(rawIActivityManager, args);
            case "startService":
                AMSHookManager.Utils.replaceServiceIntent(args);
                return method.invoke(rawIActivityManager, args);
            case "stopService":
                // 判断是否停止插件中的服务。
                Intent intent = AMSHookManager.Utils.filter(args);
                if (!context.getPackageName().equals(intent.getComponent().getPackageName())) {
                    return ServiceHookManager.stopService(intent);
                } else {
                    return method.invoke(rawIActivityManager, args);
                }
            default:
                return method.invoke(rawIActivityManager, args);
        }
    }
}
