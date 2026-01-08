package com.xingen.hookdemo

import android.os.Build

/**
 * Created by HeXinGen  on 2026/1/8
 * Description:.
 */
//android 9及其以上版本
fun isAndroid9Above() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
//android 10及其以上版本
fun isAndroid10Above() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

//android 11及其以上版本
fun isAndroid11Above() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

//android 12及其以上版本
fun isAndroid12Above() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

//android 13及其以上版本
fun isAndroid13Above() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

//android 14及其以上版本
fun isAndroid14Above() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE

//android 15及其以上版本
fun isAndroid15Above() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM