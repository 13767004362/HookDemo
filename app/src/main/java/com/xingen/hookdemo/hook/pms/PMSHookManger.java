package com.xingen.hookdemo.hook.pms;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;
import android.util.Log;

import com.xingen.hookdemo.hook.ams.AMSHookManager;
import com.xingen.hookdemo.hook.service.ServiceHookManager;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * @author ： HeXinGen
 * @date ： 2023/7/10
 * @description ：
 */
public class PMSHookManger {

    private static final  String TAG="PMSHookManger";
    public static void printAppSignature(Context context) {
        try {
            PackageInfo packageInfo =context. getPackageManager().getPackageInfo(context.getPackageName(), PackageManager.GET_SIGNATURES);

            if (packageInfo.signatures != null) {
                Log.i(TAG, "sig:"+packageInfo.signatures[0].toCharsString());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    public static void  hookPMS(Context context,String signed){
        try {
            //获取到ActivityThread
            Class<?> ActivityThreadClass = Class.forName("android.app.ActivityThread");
            Field sCurrentActivityThreadField = ActivityThreadClass.getDeclaredField("sCurrentActivityThread");
            sCurrentActivityThreadField.setAccessible(true);
            Object ActivityThread = sCurrentActivityThreadField.get(null);
            Field sPackageManagerField= ActivityThreadClass.getDeclaredField("sPackageManager");
            sPackageManagerField.setAccessible(true);
            Object sPackageManager=sPackageManagerField.get(ActivityThread);
            // 准备好代理对象, 用来替换原始的对象
            Class<?> iPackageManagerInterface = Class.forName("android.content.pm.IPackageManager");
            Object proxy = Proxy.newProxyInstance(
                    iPackageManagerInterface.getClassLoader(),
                    new Class<?>[]{iPackageManagerInterface},
                    new PMSHookProxy(sPackageManager, signed, context.getPackageName()));
            // 1. 替换掉ActivityThread里面的 sPackageManager 字段
            sPackageManagerField.set(ActivityThread, proxy);
            // 2. 替换 ApplicationPackageManager里面的 mPM对象
            PackageManager pm = context.getPackageManager();
            Field mPmField = pm.getClass().getDeclaredField("mPM");
            mPmField.setAccessible(true);
            mPmField.set(pm, proxy);
        }catch (Exception e){
            e.printStackTrace();
        }
    }
    private static class PMSHookProxy implements InvocationHandler{
        private Object oldPMS;
        //应用正确的签名信息
        private String SIGN;
        private String appPkgName = "";

        public PMSHookProxy(Object oldPMS, String SIGN, String appPkgName) {
            this.oldPMS = oldPMS;
            this.SIGN = SIGN;
            this.appPkgName = appPkgName;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            switch (method.getName()) {
                case "getPackageInfo":
                    String pkgName = (String)args[0];
                    //android 13 开始,getPackageInfo(String packageName, long flags, int userId) 第二个参数是long类型
                    long flag = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ? (long) args[1] : (int) args[1];
                    //是否是获取我们需要hook apk的签名
                    if(flag == PackageManager.GET_SIGNATURES && appPkgName.equals(pkgName)){
                        //将构造方法中传进来的新的签名覆盖掉原来的签名
                        Signature sign = new Signature(SIGN);
                        PackageInfo info = (PackageInfo) method.invoke(oldPMS, args);
                        info.signatures[0] = sign;
                        return info;
                    }
                    return method.invoke(oldPMS, args);

                default:
                    return method.invoke(oldPMS, args);
            }
        }
    }
}
