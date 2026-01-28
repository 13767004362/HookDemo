package com.xingen.plugin

class NativeCodeTest {
    companion object {
        init {
            System.loadLibrary("plugin_lib")
        }
    }

    external fun getContentFromJNI(): String

    fun getShowContent(): String {
        return getContentFromJNI()
    }
}
