package com.xingen.hookdemo.hook.classLoader

import android.content.Context
import android.os.Build
import android.text.TextUtils
import android.util.Log
import com.xingen.hookdemo.isAndroid14Above
import com.xingen.hookdemo.utils.Utils
import dalvik.system.DexClassLoader
import dalvik.system.DexFile
import java.io.File
import java.io.IOException
import java.lang.reflect.Array
import java.util.ArrayList
import java.util.LinkedList
import java.util.List
/**
 * @author HeXinGen
 * date 2019/5/30.
 */

object ClassLoaderHookManager {
    private const val TAG = "ClassLoaderHookManager"

    @JvmStatic
    fun init(context: Context, zipFilePath: String, optimizedDirectory: String) {
        try {
            //android 14 开始，动态加载代码dex,必须是只读
            if (isAndroid14Above()) {
                val zipFile = File(zipFilePath)
                zipFile.setReadOnly()
            }
            // 先解压dex文件
            val dexFile = DexParse.parseDex(zipFilePath, optimizedDirectory)
            val appClassLoader = ClassLoaderHookManager::class.java.classLoader
            // 将插件dex加载到主进程的classloader, dex文件可以放sdcard或者手机内部磁盘中，但so库只能放在手机内部磁盘中data/data下
            loadPluginDex(File(zipFilePath), dexFile, appClassLoader)
            loadNative(context, zipFilePath, optimizedDirectory, appClassLoader)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 加载native库
     */
    private fun loadNative(
        context: Context,
        zipFilePath: String,
        optimizedDirectory: String,
        appClassLoader: ClassLoader
    ) {
        try {
            var librarySearchPath: String? = null
            try {
                Utils.unZipFolder(zipFilePath, optimizedDirectory)
                librarySearchPath = File(optimizedDirectory + File.separator + "lib").absolutePath
                // 需要删除其余的文件,防止占用磁盘空间。
            } catch (e: Exception) {
                e.printStackTrace()
            }

            if (TextUtils.isEmpty(librarySearchPath)) return
            // 查询到so库中的文件目录
            var abi_file_dir: File? = null
            val dirFile = File(librarySearchPath)
            val files = dirFile.listFiles()
            for (file in files!!) {
                if (file != null && file.exists() && file.isDirectory) {
                    val abi = Build.CPU_ABI
                    // 获取当前应用程序支持cpu(非手机cpu),配到对应的so库。
                    // 注意点： 若是宿主没有32位数Zygote，是无法加载 插件中32位so库。
                    if (file.name.contains(abi)) {
                        abi_file_dir = file
                        break
                    }
                }
            }

            var mLibDir: File? = null
            try {
                // so库，不可以放在sdcard中。
                val mLibDirPath =
                    "${context.cacheDir}${File.separator}lib${File.separator}${Build.CPU_ABI}"
                mLibDir = File(mLibDirPath)
                if (!mLibDir.exists()) {
                    mLibDir.mkdirs()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            val pluginNativeLibraryDirList: MutableList<File> = LinkedList()
            if (mLibDir != null && abi_file_dir != null) {
                val so_file_array = abi_file_dir.listFiles()
                for (file in so_file_array) {
                    val so_file = File(mLibDir.absolutePath + File.separator + file.name)
                    Utils.copyFiles(file.absolutePath, so_file.absolutePath)
                    pluginNativeLibraryDirList.add(mLibDir)
                }
            }
            // 获取到DexPathList对象
            val baseDexClassLoaderClass = DexClassLoader::class.java.superclass
            val pathListField = baseDexClassLoaderClass.getDeclaredField("pathList")
            pathListField.isAccessible = true
            val dexPathList = pathListField.get(appClassLoader)

            /**
             * 接下来,合并宿主so,系统so,插件so库
             */
            val dexPathListClass = dexPathList.javaClass
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP_MR1) {
                // 先创建一个汇总so库的文件夹,收集全部
                val allNativeLibDirList: MutableList<File> = ArrayList()
                // 先添加插件的so库地址
                allNativeLibDirList.addAll(pluginNativeLibraryDirList)
                // 获取到宿主的so库地址
                val nativeLibraryDirectoriesField =
                    dexPathListClass.getDeclaredField("nativeLibraryDirectories")
                nativeLibraryDirectoriesField.isAccessible = true
                val oldNativeLibraryDirectories =
                    nativeLibraryDirectoriesField.get(dexPathList) as List<File>
                allNativeLibDirList.addAll(oldNativeLibraryDirectories)
                // 获取到system的so库地址
                val systemNativeLibraryDirectoriesField =
                    dexPathListClass.getDeclaredField("systemNativeLibraryDirectories")
                systemNativeLibraryDirectoriesField.isAccessible = true
                val systemNativeLibraryDirectories =
                    systemNativeLibraryDirectoriesField.get(dexPathList) as List<File>
                allNativeLibDirList.addAll(systemNativeLibraryDirectories)

                var allNativeLibraryPathElements: kotlin.Array<Any>? = null
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                    //通过makePathElements获取到c++存放的Element
                    val makePathElementsMethod = dexPathListClass.getDeclaredMethod(
                        "makePathElements",
                        List::class.java,
                        List::class.java,
                        ClassLoader::class.java
                    )
                    makePathElementsMethod.isAccessible = true
                    allNativeLibraryPathElements = makePathElementsMethod.invoke(
                        null,
                        allNativeLibDirList,
                        ArrayList<IOException>(),
                        appClassLoader
                    ) as kotlin.Array<Any>
                } else {
                    //android 8.0 以上有所改变, nativeLibraryPathElements = makePathElements(this.systemNativeLibraryDirectories);
                    val makePathElementsMethod =
                        dexPathListClass.getDeclaredMethod("makePathElements", List::class.java)
                    makePathElementsMethod.isAccessible = true
                    allNativeLibraryPathElements = makePathElementsMethod.invoke(
                        null,
                        allNativeLibDirList
                    ) as kotlin.Array<Any>
                }
                //将合并宿主和插件的so库，重新设置进去
                val nativeLibraryPathElementsField =
                    dexPathListClass.getDeclaredField("nativeLibraryPathElements")
                nativeLibraryPathElementsField.isAccessible = true
                nativeLibraryPathElementsField.set(dexPathList, allNativeLibraryPathElements)
            } else {
                // 获取到宿主的so库地址
                val nativeLibraryDirectoriesField =
                    dexPathListClass.getDeclaredField("nativeLibraryDirectories")
                nativeLibraryDirectoriesField.isAccessible = true
                val oldNativeDirs =
                    nativeLibraryDirectoriesField.get(dexPathList) as kotlin.Array<File>
                val oldNativeLibraryDirSize = oldNativeDirs.size
                // 创建一个汇总宿主，插件的so库地址的数组
                val totalNativeLibraryDir =
                    arrayOfNulls<File>(oldNativeLibraryDirSize + pluginNativeLibraryDirList.size)
                System.arraycopy(
                    oldNativeDirs,
                    0,
                    totalNativeLibraryDir,
                    0,
                    oldNativeLibraryDirSize
                )
                for (i in totalNativeLibraryDir.indices) {
                    totalNativeLibraryDir[oldNativeLibraryDirSize + i] =
                        pluginNativeLibraryDirList[i]
                }
                // 替换成合并的so库资源数组
                nativeLibraryDirectoriesField.set(dexPathList, totalNativeLibraryDir)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 加载插件中的dex文件
     */
    private fun loadPluginDex(apkFile: File, dexFile: DexFile, appClassLoader: ClassLoader) {
        try {
            val baseDexClassLoaderClass = DexClassLoader::class.java.superclass
            val pathListField = baseDexClassLoaderClass.getDeclaredField("pathList")
            pathListField.isAccessible = true
            // 获取到DexPathList
            val pathList = pathListField.get(appClassLoader)
            // ElementsField对象
            val dexElementsField = pathList.javaClass.getDeclaredField("dexElements")
            dexElementsField.isAccessible = true
            val oldElementsArray = dexElementsField.get(pathList) as kotlin.Array<*>
            // Elements 获取的类型
            val elementClass = oldElementsArray.javaClass.componentType

            Log.i(TAG, " 查询的类型 " + elementClass.name)
            // 创建新的ElementsField数组
            val newElementsArray =
                Array.newInstance(elementClass, oldElementsArray.size + 1) as kotlin.Array<*>
            var o: Any
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                // 构造插件Element(File file, boolean isDirectory, File zip, DexFile dexFile) 这个构造函数
                val constructor = elementClass.getConstructor(
                    File::class.java,
                    Boolean::class.javaPrimitiveType,
                    File::class.java,
                    DexFile::class.java
                )
                o = constructor.newInstance(apkFile, false, apkFile, dexFile)
            } else {
                // 构造插件的 Element，构造函数参数：(DexFile dexFile, File file)
                val constructor = elementClass.getConstructor(DexFile::class.java, File::class.java)
                o = constructor.newInstance(dexFile, apkFile)
            }

            val toAddElementArray = arrayOf(o)
            // 把原始的elements复制进去
            System.arraycopy(oldElementsArray, 0, newElementsArray, 0, oldElementsArray.size)
            // 插件的那个element复制进去
            System.arraycopy(
                toAddElementArray,
                0,
                newElementsArray,
                oldElementsArray.size,
                toAddElementArray.size
            )
            // 替换
            dexElementsField.set(pathList, newElementsArray)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private object DexParse {
        /**
         * 从.apk .jar .zip 解析出 dex 文件
         *
         */
        fun parseDex(zipFilePath: String, optimizedDirectory: String): DexFile {
            val dexFilePath = optimizedPathFor(File(zipFilePath), File(optimizedDirectory))
            return DexFile.loadDex(zipFilePath, dexFilePath, 0)
        }

        private fun optimizedPathFor(path: File, optimizedDirectory: File): String {
            val DEX_SUFFIX = ".dex"
            var fileName = path.name
            if (!fileName.endsWith(DEX_SUFFIX)) {
                val lastDot = fileName.lastIndexOf(".")
                if (lastDot < 0) {
                    fileName += DEX_SUFFIX
                } else {
                    val sb = StringBuilder(lastDot + 4)
                    sb.append(fileName.substring(0, lastDot))
                    sb.append(DEX_SUFFIX)
                    fileName = sb.toString()
                }
            }

            val result = File(optimizedDirectory, fileName)
            return result.path
        }
    }
}
