package com.xingen.hookdemo.hook.ams;

import android.os.Handler;
import android.os.Message;

/**
 * Created by ${HeXinGen} on 2019/1/6.
 * blog博客:http://blog.csdn.net/hexingen
 */

public class ActivityThreadHandlerCallback implements Handler.Callback {

    private  Handler activityThreadHandler;

    public ActivityThreadHandlerCallback(Handler activityThreadHandler) {
        this.activityThreadHandler = activityThreadHandler;
    }

    @Override
    public boolean handleMessage(Message msg) {
        AMSHookManager.Utils.recoverActivityIntent(msg);
        activityThreadHandler.handleMessage(msg);
        return true;
    }
}
