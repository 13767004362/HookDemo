package com.xingen.plugin.contentprovider;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

/**
 * @author HeXinGen
 * date 2019/6/14.
 */
public class PluginContentProvider extends ContentProvider {
    public static final String AUTHORITY = "com.xingen.plugin.contentprovider.PluginContentProvider";

    public static final Uri URI = Uri.parse("content://" + AUTHORITY);

    public static final String NAME = "name";

    private static final String TABLE_NAME = "developer";
    private PluginDB db;

    @Override
    public boolean onCreate() {
        db = new PluginDB(getContext());
        return true;
    }

    @Nullable
    @Override
    public Cursor query(@NonNull Uri uri, @Nullable String[] projection, @Nullable String selection, @Nullable String[] selectionArgs, @Nullable String sortOrder) {
        SQLiteDatabase database = db.getReadableDatabase();
        return database.query(TABLE_NAME, projection, selection, selectionArgs, null, null, sortOrder);
    }

    @Nullable
    @Override
    public String getType(@NonNull Uri uri) {
        return null;
    }

    @Nullable
    @Override
    public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) {
        SQLiteDatabase database = db.getWritableDatabase();
        long rowId = database.insert(TABLE_NAME, null, values);
        Uri rowUri = ContentUris.appendId(URI.buildUpon(), rowId).build();
        return rowUri;
    }

    @Override
    public int delete(@NonNull Uri uri, @Nullable String selection, @Nullable String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(@NonNull Uri uri, @Nullable ContentValues values, @Nullable String selection, @Nullable String[] selectionArgs) {
        return 0;
    }

    private static class PluginDB extends SQLiteOpenHelper {
        private static final String db_name = "developer.db";
        private static final int db_version = 1;

        public PluginDB(Context context) {
            super(context, db_name, null, db_version);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            StringBuffer stringBuffer = new StringBuffer();
            stringBuffer.append("Create table ");
            stringBuffer.append(TABLE_NAME);
            stringBuffer.append("( _id INTEGER PRIMARY KEY AUTOINCREMENT, ");
            stringBuffer.append(NAME);
            stringBuffer.append(" text ");
            stringBuffer.append(");");
            db.execSQL(stringBuffer.toString());
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

        }
    }
}
