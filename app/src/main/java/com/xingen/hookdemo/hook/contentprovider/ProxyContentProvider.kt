package com.xingen.hookdemo.hook.contentprovider

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.util.Log
import androidx.core.net.toUri

/**
 * @author HeXinGen
 * date 2019/6/14.
 *
 * 代理的ContentProvider,作为一个中转站，对第三方app提供查询入口、
 */

class ProxyContentProvider : ContentProvider() {
    companion object {
        private const val TAG = "ProxyContentProvider"
        const val AUTHORITY = "com.xingen.hookdemo.hook.contentprovider.ProxyContentProvider"

        /**
         * 为了使得插件的ContentProvder提供给外部使用，我们需要一个StubProvider做中转；
         * 如果外部程序需要使用插件系统中插件的ContentProvider，不能直接查询原来的那个uri
         * 我们对uri做一些手脚，使得插件系统能识别这个uri；
         *
         * 这里的处理方式如下：
         *
         * 原始查询插件的URI应该为：
         * content://plugin_auth/path/query
         *
         * 如果需要查询插件，需要修改为：
         *
         * content://stub_auth/plugin_auth/path/query
         *
         * 也就是，我们把插件ContentProvider的信息放在URI的path中保存起来；
         * 然后在StubProvider中做分发。
         *
         * 当然，也可以使用QueryParamerter,比如：
         * content://plugin_auth/path/query/ ->  content://stub_auth/path/query?plugin=plugin_auth
         * @param raw 外部查询我们使用的URI
         * @return 插件真正的URI
         */

        fun splitUri(raw: Uri): Uri {
            val authority = raw.authority
            if (AUTHORITY != authority) {
                Log.w(TAG, "rawAuth:$authority")
            }
            var uriString = raw.toString()
            uriString = uriString.replace("$authority/", "")
            val newUri = uriString.toUri()
            Log.i(TAG, "realUri:$newUri")
            return newUri
        }
    }

    override fun onCreate(): Boolean {
        return false
    }

    override fun query(
        uri: Uri,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?
    ): Cursor? {
        return context?.contentResolver?.query(
            splitUri(uri),
            projection,
            selection,
            selectionArgs,
            sortOrder
        )
    }

    override fun getType(uri: Uri): String? {
        return null
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        return context?.contentResolver?.insert(splitUri(uri), values)
    }

    override fun delete(
        uri: Uri,
        selection: String?,
        selectionArgs: Array<String>?
    ): Int {
        return 0
    }

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<String>?
    ): Int {
        return 0
    }
}
