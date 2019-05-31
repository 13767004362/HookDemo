package com.xingen.hookdemo.hook.classLoader;

import android.sax.Element;
import android.util.Log;

import java.io.File;
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
    private static final String TAG=ClassLoaderHookManager.class.getSimpleName();

    public static void init(File apkFile,File optDexFile ,ClassLoader appClassLoader) {
        try {
            Class<?> baseDexClassLoaderClass = DexClassLoader.class.getSuperclass();
            Field pathListField = baseDexClassLoaderClass.getDeclaredField("pathList");
            pathListField.setAccessible(true);
            // 获取到DexPathList
            Object pathList = pathListField.get(appClassLoader);
            // ElementsField对象
            Field dexElementsField = pathList.getClass().getDeclaredField("dexElements");
            dexElementsField.setAccessible(true);
            Object[] oldElementsArray=(Object[]) dexElementsField.get(pathList);
            // Elements 获取的类型
            Class<?> elementClass= oldElementsArray.getClass().getComponentType();

            Log.i(TAG," 查询的类型 "+elementClass.getName());
            // 创建新的ElementsField数组
            Object[] newElementsArray=(Object[]) Array.newInstance(elementClass,oldElementsArray.length+1);

            // 构造插件Element(File file, boolean isDirectory, File zip, DexFile dexFile) 这个构造函数
            Constructor<?> constructor = elementClass.getConstructor(File.class, boolean.class, File.class, DexFile.class);
            Object o = constructor.newInstance(apkFile, false, apkFile, DexFile.loadDex(apkFile.getCanonicalPath(), optDexFile.getAbsolutePath(), 0));
            Object[] toAddElementArray = new Object[] { o };
            // 把原始的elements复制进去
            System.arraycopy(oldElementsArray, 0,newElementsArray, 0, oldElementsArray.length);
            // 插件的那个element复制进去
            System.arraycopy(toAddElementArray, 0,newElementsArray, oldElementsArray.length, toAddElementArray.length);
            // 替换
            dexElementsField.set(pathList,newElementsArray);

        } catch (Exception e) {
            e.printStackTrace();
        }


    }
}
