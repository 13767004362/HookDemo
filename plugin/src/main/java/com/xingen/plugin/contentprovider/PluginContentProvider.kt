package com.xingen.plugin.contentprovider

import android.content.ContentProvider
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.net.Uri
import androidx.core.net.toUri

class PluginContentProvider : ContentProvider() {
    companion object {
        const val AUTHORITY = "com.xingen.plugin.contentprovider.PluginContentProvider"
        val URI = "content://$AUTHORITY".toUri()
        const val NAME = "name"
        private const val TABLE_NAME = "developer"
    }

    private var db: PluginDB? = null

    override fun onCreate(): Boolean {
        db = PluginDB(context)
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?
    ): Cursor? {
        val database = db?.readableDatabase
        return database?.query(TABLE_NAME, projection, selection, selectionArgs, null, null, sortOrder)
    }

    override fun getType(uri: Uri): String? {
        return null
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        val database = db?.writableDatabase
        val rowId = database?.insert(TABLE_NAME, null, values) ?: return null
        return ContentUris.appendId(URI.buildUpon(), rowId).build()
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

    private class PluginDB(context: Context?) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {
        companion object {
            private const val DB_NAME = "developer.db"
            private const val DB_VERSION = 1
        }

        override fun onCreate(db: SQLiteDatabase) {
            val stringBuffer = StringBuilder()
            stringBuffer.append("Create table ")
            stringBuffer.append(TABLE_NAME)
            stringBuffer.append("( _id INTEGER PRIMARY KEY AUTOINCREMENT, ")
            stringBuffer.append(NAME)
            stringBuffer.append(" text ")
            stringBuffer.append(");")
            db.execSQL(stringBuffer.toString())
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        }
    }
}
