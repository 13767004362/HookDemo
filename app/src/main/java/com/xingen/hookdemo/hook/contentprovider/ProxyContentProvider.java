package com.xingen.hookdemo.hook.contentprovider;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import java.util.Random;

/**
 * @author HeXinGen
 * date 2019/6/14.
 *
 * 代理的ContentProvider,作为一个中转站，对第三方app提供查询入口、
 */
public class ProxyContentProvider  extends ContentProvider{
    private static final  String TAG=ProxyContentProvider.class.getSimpleName();
    public static final String AUTHORITY = "com.xingen.hookdemo.hook.contentprovider.ProxyContentProvider";
    @Override
    public boolean onCreate() {

        return false;
    }

    @Nullable
    @Override
    public Cursor query(@NonNull Uri uri, @Nullable String[] projection, @Nullable String selection, @Nullable String[] selectionArgs, @Nullable String sortOrder) {
        return getContext().getContentResolver().query(splitUri(uri),projection,selection,selectionArgs,sortOrder);
    }

    @Nullable
    @Override
    public String getType(@NonNull Uri uri) {
        return null;
    }

    @Nullable
    @Override
    public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) {
        return getContext().getContentResolver().insert(splitUri(uri),values);
    }

    @Override
    public int delete(@NonNull Uri uri, @Nullable String selection, @Nullable String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(@NonNull Uri uri, @Nullable ContentValues values, @Nullable String selection, @Nullable String[] selectionArgs) {
        return 0;
    }
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
    public static  Uri splitUri(Uri raw){
      String authority =raw.getAuthority();
        if (!AUTHORITY.equals(authority)) {
            Log.w(TAG, "rawAuth:" + authority);
        }
        String uriString = raw.toString();
        uriString = uriString.replaceAll(authority+ '/', "");
        Uri newUri = Uri.parse(uriString);
        Log.i(TAG, "realUri:" + newUri);
        return newUri;
    }
}
