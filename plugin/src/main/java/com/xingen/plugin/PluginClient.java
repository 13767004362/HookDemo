package com.xingen.plugin;

import android.app.Activity;

import com.xingen.plugin.dialog.MessageDialogFragment;
import com.xingen.plugin.hook.classloader.HookManager;


/**
 * Created by ${新根} on 2018/6/9.
 * blog博客:http://blog.csdn.net/hexingen
 */

public class PluginClient {

    public static void init(){
       HookManager.init();
    }
    public static void startMessageDialog(Activity activity){
        MessageDialogFragment.startDialog(activity);
    }
}
