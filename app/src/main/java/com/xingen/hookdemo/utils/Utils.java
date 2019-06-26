package com.xingen.hookdemo.utils;

import android.content.Context;
import android.os.Environment;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

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
    public static void copyFiles(String originFilePath,String endFilePath) {
        InputStream in = null;
        OutputStream out = null;
        try {
            in = new FileInputStream(originFilePath);
            out = new FileOutputStream(endFilePath);
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
    public static void copyFiles(Context context, String fileName, File desFile) {
        InputStream in = null;
        OutputStream out = null;
        try {
            in = context.getAssets().open(fileName);
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

    /**
     * 获取文件夹下的全部文件。
     *
     * @param dir
     * @param filePathList 用于存储文件路径的list
     */
    public static void queryFilesPath(String dir, List<File> filePathList) {
        File dirFile = new File(dir);
        if (dirFile != null && dirFile.exists()) {
            if (dirFile.isDirectory()) {
                //遍历dir下的文件和目录，放在File数组中
                File[] fileArray = dirFile.listFiles();
                if (fileArray == null && fileArray.length == 0) {
                    //文件夹不存在或者为空
                } else {
                    for (File file : fileArray) {
                        if (file.isDirectory()) {// 文件夹
                            queryFilesPath(file.getAbsolutePath(), filePathList);
                        } else {
                            filePathList.add(file);
                        }
                    }
                }
            } else {//该路径是一个文件，而不是文件夹，防止传入错误。
                filePathList.add(dirFile);
            }
        } else {
            //文件不存在
        }
    }
    /**
     * 解压zip文件
     *
     * @param zipFilePath
     * @param existPath
     */
    public static void unZipFolder(String zipFilePath, String existPath) {
        ZipFile zipFile = null;
        try {
            File originFile = new File(zipFilePath);
            if (originFile.exists()) {//zip文件存在
                zipFile = new ZipFile(originFile);
                Enumeration<? extends ZipEntry> enumeration = zipFile.entries();
                while (enumeration.hasMoreElements()) {
                    ZipEntry zipEntry = enumeration.nextElement();
                    if (zipEntry.isDirectory()) {//若是该文件是文件夹，则创建
                        File dir = new File(existPath + File.separator + zipEntry.getName());
                        dir.mkdirs();
                    } else {
                        File targetFile = new File(existPath + File.separator + zipEntry.getName());
                        if (!targetFile.getParentFile().exists()) {
                            targetFile.getParentFile().mkdirs();
                        }
                        targetFile.createNewFile();
                        InputStream inputStream = zipFile.getInputStream(zipEntry);
                        FileOutputStream fileOutputStream = new FileOutputStream(targetFile);
                        int len;
                        byte[] buf = new byte[1024];
                        while ((len = inputStream.read(buf)) != -1) {
                            fileOutputStream.write(buf, 0, len);
                        }
                        // 关流顺序，先打开的后关闭
                        fileOutputStream.close();
                        inputStream.close();
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (zipFile != null) {
                try {
                    zipFile.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
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
