package com.xingen.hookdemo.hook.ams;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.Message;

import com.xingen.hookdemo.hook.activity.StubActivity;
import com.xingen.hookdemo.hook.service.ProxyService;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;

/**
 * Created by ${HeXinGen} on 2019/1/6.
 * blog博客:http://blog.csdn.net/hexingen
 * <p>
 * Hook AMS
 */

public class AMSHookManager {
    private static String targetPackageName;
    public static final String KEY_RAW_INTENT = "raw_intent";
    private  static  boolean isInIt=false;

    public static boolean isIsInIt() {
        return isInIt;
    }

    /**
     * 初始化操作
     *
     * @param packageName
     */
    public static void init(Context context,String packageName) {
        try {
            targetPackageName = packageName;
            hookIActivityManager(context);
            hookActivityThreadHandler();
            isInIt=true;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * hook 掉IActivityManager，使用自己的代理对象和ams通讯
     */
    private static void hookIActivityManager(Context context) throws Exception {
        Field ActivityManagerSingletonFiled;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {//8.0以上发生改变
            Class<?> ActivityManagerClass = Class.forName("android.app.ActivityManager");
            ActivityManagerSingletonFiled = ActivityManagerClass.getDeclaredField("IActivityManagerSingleton");
        } else {
            Class<?> ActivityManagerNativeClass = Class.forName("android.app.ActivityManagerNative");
            ActivityManagerSingletonFiled = ActivityManagerNativeClass.getDeclaredField("gDefault");
        }
        ActivityManagerSingletonFiled.setAccessible(true);
        Object ActivityManagerSingleton = ActivityManagerSingletonFiled.get(null);
        // ActivityManagerSingleton是一个 android.util.Singleton对象; 我们取出这个单例里面的字段
        Class<?> SingletonClass = Class.forName("android.util.Singleton");
        Field mInstanceField = SingletonClass.getDeclaredField("mInstance");
        mInstanceField.setAccessible(true);
        //获取到ActivityManager通讯代理对象，即IActivityManager对象
        Object rawIActivityManager = mInstanceField.get(ActivityManagerSingleton);
        //动态代理，创建代理对象
        Object proxy = Proxy.newProxyInstance(Thread.currentThread().getContextClassLoader(),
                new Class[]{Class.forName("android.app.IActivityManager")},
                new IActivityManagerHandler(context,rawIActivityManager));
        //换成自己的IActivityManager对象
        mInstanceField.set(ActivityManagerSingleton, proxy);
    }


    /**
     * hook ActivityThread中 handler拦截处理
     * ,恢复要开启的activity
     */
    private static void hookActivityThreadHandler() throws Exception  {
        //获取到ActivityThread
        Class<?> ActivityThreadClass = Class.forName("android.app.ActivityThread");
        Field sCurrentActivityThreadField = ActivityThreadClass.getDeclaredField("sCurrentActivityThread");
        sCurrentActivityThreadField.setAccessible(true);
        Object ActivityThread = sCurrentActivityThreadField.get(null);
        //获取到ActivityThread中的handler
        Field mHField=ActivityThreadClass.getDeclaredField("mH");
        mHField.setAccessible(true);
         Handler mHandler=(Handler) mHField.get(ActivityThread);
         //给handler添加callback监听器，拦截
        Field mCallBackField = Handler.class.getDeclaredField("mCallback");
        mCallBackField.setAccessible(true);
        mCallBackField.set(mHandler, new ActivityThreadHandlerCallback(mHandler));
    }

    public static String getTargetPackageName() {
        return targetPackageName;
    }

    public static final class Utils {


        public static Intent filter(Object[] args){
            Intent intent=null;
            for (int i = 0; i < args.length; i++) {
                if (args[i] instanceof Intent) {
                    intent=(Intent) args[i];
                    break;
                }
            }
            return intent;
        }
        /**
         * 替换成代替的activity,绕过ams检查
         *
         * @param args
         */
        public static void replaceActivityIntent(Object[] args) {
            Intent rawIntent;
            int index = 0;

            for (int i = 0; i < args.length; i++) {
                if (args[i] instanceof Intent) {
                    index = i;
                    break;
                }
            }
            //真实启动的Intent
            rawIntent = (Intent) args[index];
            //构建一个替代的Activity对应的intent
            Intent subIntent = new Intent();
            ComponentName componentName = new ComponentName(targetPackageName, StubActivity.class.getName());
            subIntent.setComponent(componentName);
            //将真实启动的Intent作为参数附带上
            subIntent.putExtra(KEY_RAW_INTENT,rawIntent);
            args[index] = subIntent;
        }


        /**
         *  替换成ProxyService
         * @param args
         */
        public static void replaceServiceIntent(Object[] args){
            Intent rawIntent;
            int index=0;
            for (int i=0;i<args.length;++i){
                 if (args[i] instanceof Intent){
                     index=i;
                     break;
                 }
            }
            rawIntent=(Intent) args[index];
            // 构建一个ProxyService的intent
            Intent subIntent=new Intent();
            subIntent.setClassName(targetPackageName, ProxyService.class.getName());
            // 将信息存储在intent中
            subIntent.putExtra(KEY_RAW_INTENT,rawIntent);
            args[index]=subIntent;
        }


        /**
         * 恢复成要启动的activity
         * @param message
         */
        public  static  void recoverActivityIntent(Message message){
           final int LAUNCH_ACTIVITY=100;
            if (message.what==LAUNCH_ACTIVITY){
                try {
                    Class<?>  ActivityClientRecordClass=message.obj.getClass();
                   Field intentField= ActivityClientRecordClass.getDeclaredField("intent");
                   intentField.setAccessible(true);
                    Intent subIntent = (Intent)intentField.get(message.obj);
                    //真实启动的Intent
                    Intent  rawIntent= subIntent.getParcelableExtra(KEY_RAW_INTENT);
                    //把正式启动的intent设置进去
                    subIntent.setComponent(rawIntent.getComponent());
                }catch (Exception e){
                    e.printStackTrace();
                }
            }
        }
    }


}
