package com.xingen.hookdemo.utils;

import android.content.Context;
import android.os.Environment;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import dalvik.system.DexFile;

/**
 * Created by ${新根} on 2018/6/9.
 * blog博客:http://blog.csdn.net/hexingen
 */

public class Utils {
    public static String copyFiles(Context context, String fileName) {
        File dir = getCacheDir(context);
        String filePath = dir.getAbsolutePath() + File.separator + fileName;
        try {
            File desFile = new File(filePath);
            if (desFile.exists()) {
                desFile.delete();
            }
            copyFiles(context, fileName, desFile);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return filePath;

    }
    public static void copyFiles(Context context, String fileName, File desFile) {
        InputStream in = null;
        OutputStream out = null;
        try {
            in = context.getApplicationContext().getAssets().open(fileName);
            out = new FileOutputStream(desFile.getAbsolutePath());
            byte[] bytes = new byte[1024];
            int i;
            while ((i = in.read(bytes)) != -1)
                out.write(bytes, 0, i);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (in != null)
                    in.close();
                if (out != null)
                    out.close();
            } catch (IOException e) {
                e.printStackTrace();
            }

        }
    }
    public static boolean hasExternalStorage() {
        return Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED);
    }

    /**
     * 获取缓存路径
     *
     * @param context
     * @return 返回缓存文件路径
     */
    public static File getCacheDir(Context context) {
        File cache;
        if (hasExternalStorage()) {
            cache = context.getExternalCacheDir();
        } else {
            cache = context.getCacheDir();
        }
        if (!cache.exists())
            cache.mkdirs();
        return cache;
    }


}
