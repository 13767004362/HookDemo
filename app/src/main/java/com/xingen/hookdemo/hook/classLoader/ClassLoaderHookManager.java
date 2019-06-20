package com.xingen.hookdemo.hook.classLoader;

import android.os.Build;
import android.sax.Element;
import android.util.Log;
import android.widget.EditText;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;

import dalvik.system.BaseDexClassLoader;
import dalvik.system.DexClassLoader;
import dalvik.system.DexFile;

/**
 * @author HeXinGen
 * date 2019/5/30.
 */
public class ClassLoaderHookManager {
    private static final String TAG = ClassLoaderHookManager.class.getSimpleName();

    public static void init(String zipFilePath, String optimizedDirectory) {
        try {
            // 先解压dex文件
            DexFile dexFile = DexParse.parseDex(zipFilePath, optimizedDirectory);
            // 将插件dex加载到主进程的classloader
            ClassLoader appClassLoader = ClassLoaderHookManager.class.getClassLoader();
            loadPluginDex(new File(zipFilePath), dexFile, appClassLoader);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void loadPluginDex(File apkFile, DexFile dexFile, ClassLoader appClassLoader) {
        try {
            Class<?> baseDexClassLoaderClass = DexClassLoader.class.getSuperclass();
            Field pathListField = baseDexClassLoaderClass.getDeclaredField("pathList");
            pathListField.setAccessible(true);
            // 获取到DexPathList
            Object pathList = pathListField.get(appClassLoader);
            // ElementsField对象
            Field dexElementsField = pathList.getClass().getDeclaredField("dexElements");
            dexElementsField.setAccessible(true);
            Object[] oldElementsArray = (Object[]) dexElementsField.get(pathList);
            // Elements 获取的类型
            Class<?> elementClass = oldElementsArray.getClass().getComponentType();

            Log.i(TAG, " 查询的类型 " + elementClass.getName());
            // 创建新的ElementsField数组
            Object[] newElementsArray = (Object[]) Array.newInstance(elementClass, oldElementsArray.length + 1);
            Object o;
            if (Build.VERSION.SDK_INT<Build.VERSION_CODES.O){
                // 构造插件Element(File file, boolean isDirectory, File zip, DexFile dexFile) 这个构造函数
                Constructor<?> constructor = elementClass.getConstructor(File.class, boolean.class, File.class, DexFile.class);
                 o = constructor.newInstance(apkFile, false, apkFile, dexFile);
            }else{
               // 构造插件的 Element，构造函数参数：(DexFile dexFile, File file)
                Constructor<?> constructor = elementClass.getConstructor(DexFile.class,File.class);
               o= constructor.newInstance(dexFile,apkFile);
            }
            Object[] toAddElementArray = new Object[]{o};
            // 把原始的elements复制进去
            System.arraycopy(oldElementsArray, 0, newElementsArray, 0, oldElementsArray.length);
            // 插件的那个element复制进去
            System.arraycopy(toAddElementArray, 0, newElementsArray, oldElementsArray.length, toAddElementArray.length);
            // 替换
            dexElementsField.set(pathList, newElementsArray);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    private static final class DexParse {

        /**
         * 从.apk .jar .zip 解析出 dex 文件
         * @param zipFilePath
         * @param optimizedDirectory
         * @return
         * @throws IOException
         */
        public static DexFile parseDex(String zipFilePath, String optimizedDirectory) throws IOException {
            String dexFilePath = optimizedPathFor(new File(zipFilePath), new File(optimizedDirectory));
            return DexFile.loadDex(zipFilePath, dexFilePath, 0);
        }

        /**
         * 构建dex文件路径
         */
        private static String optimizedPathFor(File path,
                                               File optimizedDirectory) {
            /*
             * Get the filename component of the path, and replace the
             * suffix with ".dex" if that's not already the suffix.
             *
             * We don't want to use ".odex", because the build system uses
             * that for files that are paired with resource-only jar
             * files. If the VM can assume that there's no classes.dex in
             * the matching jar, it doesn't need to open the jar to check
             * for updated dependencies, providing a slight performance
             * boost at startup. The use of ".dex" here matches the use on
             * files in /data/dalvik-cache.
             */
            final String DEX_SUFFIX = ".dex";
            String fileName = path.getName();
            if (!fileName.endsWith(DEX_SUFFIX)) {
                int lastDot = fileName.lastIndexOf(".");
                if (lastDot < 0) {
                    fileName += DEX_SUFFIX;
                } else {
                    StringBuilder sb = new StringBuilder(lastDot + 4);
                    sb.append(fileName, 0, lastDot);
                    sb.append(DEX_SUFFIX);
                    fileName = sb.toString();
                }
            }

            File result = new File(optimizedDirectory, fileName);
            return result.getPath();
        }
    }
}
