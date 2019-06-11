package com.xingen.hookdemo.hook.receiver;


import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;


import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author HeXinGen
 * date 2019/6/11.
 */
public class ReceiverHookManager {
    private static Map<ActivityInfo, List<? extends IntentFilter>> receivers = new HashMap<>();

    public static void init(Context context, String apkFilePath) {
        preloadParseReceiver(apkFilePath);
        // 因已经自定义过classloader，已经加载了插件的dex ,这里省略加载的过程。
        registerPluginReceiver(context);
    }

    /**
     *  注册插件中的广播
     * @param context
     */
    private static void registerPluginReceiver(Context context){
        try {
            ClassLoader classLoader= ReceiverHookManager.class.getClassLoader();
            for ( ActivityInfo activityInfo:receivers.keySet()){
                List<? extends IntentFilter> intentFilters=receivers.get(activityInfo);
                BroadcastReceiver broadcastReceiver=(BroadcastReceiver) classLoader.loadClass(activityInfo.name).newInstance();
                if (intentFilters!=null&&broadcastReceiver!=null){
                    for ( IntentFilter intentFilter:intentFilters){
                        context.registerReceiver(broadcastReceiver,intentFilter);
                    }
                }
            }
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    /**
     *  解析插件中的广播
     * @param apkFilePath
     */
    private static void preloadParseReceiver(String apkFilePath) {
        try {
            // 先获取PackageParser对象
            Class<?> packageParserClass = Class.forName("android.content.pm.PackageParser");
            Object packageParser = packageParserClass.newInstance();
            //接着获取PackageParser.Package
            Method parsePackageMethod = packageParserClass.getDeclaredMethod("parsePackage", File.class, int.class);
            parsePackageMethod.setAccessible(true);
            Object packageParser$package = parsePackageMethod.invoke(packageParser, new File(apkFilePath), PackageManager.GET_RECEIVERS);
            // 接着获取到Package中的receivers列表
            Class<?> packageParser$package_Class = packageParser$package.getClass();
            Field receiversField= packageParser$package_Class.getDeclaredField("receivers");
            receiversField.setAccessible(true);
            List receiversList= (List) receiversField.get(packageParser$package);
            Class<?> packageParser$Activity_Class =Class.forName("android.content.pm.PackageParser$Activity");
            // intent-filter过滤器
            Field intentsFiled=packageParser$Activity_Class.getField("intents");
            intentsFiled.setAccessible(true);
            // 获取 name
            Field infoField=packageParser$Activity_Class.getDeclaredField("info");
            infoField.setAccessible(true);
            for (Object receiver:receiversList){
                ActivityInfo info=(ActivityInfo) infoField.get(receiver);
                List<? extends IntentFilter> intentFiltersList= (List<? extends IntentFilter>) intentsFiled.get(receiver);
                receivers.put(info,intentFiltersList);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


}
