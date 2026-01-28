package com.xingen.plugin

import android.app.Application
import android.content.Context
import android.util.Log

class DelegateApplication : Application() {
    companion object {
        private const val TAG = "DelegateApplication"
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        Log.i(TAG, "$TAG 执行 attachBaseContext() ")
    }
}
