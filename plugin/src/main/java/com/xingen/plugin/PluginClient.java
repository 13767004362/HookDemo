package com.xingen.plugin;

import android.app.Activity;
import android.content.Context;

import com.xingen.plugin.fragment.MessageDialogFragment;
import com.xingen.plugin.hook.classloader.HookManager;


/**
 * Created by ${新根} on 2018/6/9.
 * blog博客:http://blog.csdn.net/hexingen
 */

public class PluginClient {

    public static void init(){
       HookManager.init();
    }
    public static void initClassLoader(Context context){
        HookManager.initClassLoader(context);
    }
    public static void startMessageDialog(Activity activity){
        MessageDialogFragment.startDialog(activity);
    }
}
