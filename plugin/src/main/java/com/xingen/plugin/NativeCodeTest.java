package com.xingen.plugin;

/**
 * @author HeXinGen
 * date 2019/6/25.
 */
public class NativeCodeTest {
    static {
        System.loadLibrary("plugin_lib");
    }

    public native  String getContentFromJNI();

    public String getShowContent(){
        return getContentFromJNI();
    }
}
