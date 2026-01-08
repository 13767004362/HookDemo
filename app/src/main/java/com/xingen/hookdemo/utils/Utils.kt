package com.xingen.hookdemo.utils

import android.content.Context
import android.os.Environment
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/**
 * Created by ${新根} on 2018/6/9.
 * blog博客:http://blog.csdn.net/hexingen
 */
object Utils {
    fun copyFiles(context: Context, fileName: String): String {
        val dir = getCacheDir(context)
        val filePath = dir.absolutePath + File.separator + fileName
        try {
            val desFile = File(filePath)
            if (desFile.exists()) {
                desFile.delete()
            }
            copyFiles(context, fileName, filePath)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return filePath
    }

    @JvmStatic
    fun copyFiles(originFilePath: String?, endFilePath: String?) {
        var `in`: InputStream? = null
        var out: OutputStream? = null
        try {
            `in` = FileInputStream(originFilePath)
            out = FileOutputStream(endFilePath)
            val bytes = ByteArray(1024)
            var i: Int
            while ((`in`.read(bytes).also { i = it }) != -1) out.write(bytes, 0, i)
        } catch (e: IOException) {
            e.printStackTrace()
        } finally {
            try {
                `in`?.close()
                out?.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    fun copyFiles(context: Context, fileName: String, desFilePath: String?) {
        var `in`: InputStream? = null
        var out: OutputStream? = null
        try {
            `in` = context.assets.open(fileName)
            out = FileOutputStream(desFilePath)
            val bytes = ByteArray(1024)
            var i: Int
            while ((`in`.read(bytes).also { i = it }) != -1) out.write(bytes, 0, i)
        } catch (e: IOException) {
            e.printStackTrace()
        } finally {
            try {
                `in`?.close()
                out?.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    /**
     * 获取文件夹下的全部文件。
     *
     * @param dir
     * @param filePathList 用于存储文件路径的list
     */
    fun queryFilesPath(dir: String, filePathList: MutableList<File?>) {
        val dirFile = File(dir)
        if (dirFile.exists()) {
            if (dirFile.isDirectory()) {
                //遍历dir下的文件和目录，放在File数组中
                val fileArray = dirFile.listFiles()
                if (fileArray?.size == 0) {
                    //文件夹不存在或者为空
                } else {
                    for (file in fileArray!!) {
                        if (file.isDirectory()) { // 文件夹
                            queryFilesPath(file.absolutePath, filePathList)
                        } else {
                            filePathList.add(file)
                        }
                    }
                }
            } else { //该路径是一个文件，而不是文件夹，防止传入错误。
                filePathList.add(dirFile)
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
    @JvmStatic
    fun unZipFolder(zipFilePath: String, existPath: String?) {
        var zipFile: ZipFile? = null
        try {
            val originFile = File(zipFilePath)
            if (originFile.exists()) { //zip文件存在
                zipFile = ZipFile(originFile)
                val enumeration = zipFile.entries()
                while (enumeration.hasMoreElements()) {
                    val zipEntry: ZipEntry = enumeration.nextElement()
                    if (zipEntry.isDirectory) { //若是该文件是文件夹，则创建
                        val dir = File(existPath + File.separator + zipEntry.name)
                        dir.mkdirs()
                    } else {
                        val targetFile = File(existPath + File.separator + zipEntry.name)
                        if (!targetFile.getParentFile().exists()) {
                            targetFile.getParentFile().mkdirs()
                        }
                        targetFile.createNewFile()
                        val inputStream = zipFile.getInputStream(zipEntry)
                        val fileOutputStream = FileOutputStream(targetFile)
                        var len: Int
                        val buf = ByteArray(1024)
                        while ((inputStream.read(buf).also { len = it }) != -1) {
                            fileOutputStream.write(buf, 0, len)
                        }
                        // 关流顺序，先打开的后关闭
                        fileOutputStream.close()
                        inputStream.close()
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            if (zipFile != null) {
                try {
                    zipFile.close()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun hasExternalStorage(): Boolean {
        return Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED
    }

    /**
     * 获取缓存路径
     *
     * @param context
     * @return 返回缓存文件路径
     */
    fun getCacheDir(context: Context): File {
        val cache: File
        if (hasExternalStorage()) {
            cache = context.externalCacheDir!!
        } else {
            cache = context.cacheDir
        }
        if (!cache.exists()) cache.mkdirs()
        return cache
    }
}
