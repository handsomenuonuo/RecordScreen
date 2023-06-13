package org.hf.recordscreen

import android.app.Activity
import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import org.hf.recordscreen.annotation.RecordScreen
import org.hf.recordscreen.annotation.RecordScreenStart
import org.hf.recordscreen.annotation.RecordScreenStop
import org.hf.recordscreen.bean.RecordScreenCallback
import org.hf.recordscreen.bean.RecordScreenConfig
import org.hf.recordscreen.context.RecordScreenContext
import org.hf.recordscreen.context.RecordScreenContextImpl
import org.hf.recordscreen.context.RecordScreenContextImpl.Companion.application
import org.hf.recordscreen.context.RecordScreenContextImpl.Companion.initRecordScreen
import org.hf.recordscreen.context.RecordScreenContextImpl.Companion.toast
import java.lang.ref.WeakReference
import kotlin.reflect.full.declaredFunctions
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.starProjectedType

/**********************************
 * @Name:         Ext
 * @Copyright：  Antoco
 * @CreateDate： 2023/6/9 11:34
 * @author:      huang feng
 * @Version：    1.0
 * @Describe:
 **********************************/
private fun Context.isMainProcess(): Boolean {
    val packageName = packageName
    val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

    val runningAppProcesses = activityManager.runningAppProcesses
    if (runningAppProcesses != null) {
        for (processInfo in runningAppProcesses) {
            if (processInfo.processName == packageName && processInfo.pid == android.os.Process.myPid()) {
                val applicationInfo = applicationInfo
                return applicationInfo != null && applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM == 0
            }
        }
    }
    return false
}

fun Application.initRecordScreen() {
    if(isMainProcess()){
        application = WeakReference(this)
        registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                if(activity !is AppCompatActivity) {return}
                decodeAnnotation(activity)
            }

            override fun onActivityStarted(activity: Activity) {
            }

            override fun onActivityResumed(activity: Activity) {
            }

            override fun onActivityPaused(activity: Activity) {
            }

            override fun onActivityStopped(activity: Activity) {
            }

            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
            }

            override fun onActivityDestroyed(activity: Activity) {
            }

        })
    }
}

private fun decodeAnnotation(activity : AppCompatActivity) {
    // 解析注解
    val initAnnotation = activity::class.java.getAnnotation(RecordScreen::class.java)
    if (initAnnotation != null) {
        // 执行相应的操作
        activity.initRecordScreenPlugin()
    }
}

fun AppCompatActivity.initRecordScreenPlugin(recordScreenConfig : RecordScreenConfig?=null) {
    val recordContext = initRecordScreen(this,recordScreenConfig)
    val functions = this::class.declaredFunctions
    for (f in functions) {
        val parameters = f.parameters
        val returnType = f.returnType
        if(parameters.size > 1 || returnType != Unit::class.starProjectedType) {
            continue
        }
        val start = f.findAnnotation<RecordScreenStart>()
        if (start!=null){
            try {
                (recordContext as RecordScreenContextImpl).startFunction = f
            }catch (e:Exception) {
                e.printStackTrace()
            }
            if((recordContext as RecordScreenContextImpl).stopFunction != null)break
            continue
        }
        val stop = f.findAnnotation<RecordScreenStop>()
        if (stop!=null){
            try {
                (recordContext as RecordScreenContextImpl).stopFunction = f
            }catch (e:Exception) {
                e.printStackTrace()
            }
            if((recordContext as RecordScreenContextImpl).startFunction != null)break
            continue
        }
    }
}

fun AppCompatActivity.getRecordScreenContext(): RecordScreenContext? {
    return RecordScreenContextImpl.contextMap[this.componentName?.className]?.get()
}

fun AppCompatActivity.startRecordScreen(){
    startRecordScreen(null,null)
}

fun AppCompatActivity.startRecordScreen(
    recordScreenConfig: RecordScreenConfig? = null,
){
    startRecordScreen(null,recordScreenConfig)
}

fun AppCompatActivity.startRecordScreen(
    listener: RecordScreenCallback?=null,
){
    startRecordScreen(listener,null)
}

fun AppCompatActivity.startRecordScreen(
    listener: RecordScreenCallback?=null,
    recordScreenConfig: RecordScreenConfig? = null,
){
    val context = getRecordScreenContext()
    if(context == null){
        toast("请先注册需要录屏的Activity")
        return
    }
    context.config = recordScreenConfig
    context.startRecordScreen(listener)
}

fun AppCompatActivity.stopRecordScreen(){
    val context = getRecordScreenContext()
    if(context == null){
        toast("请先注册需要录屏的Activity")
        return
    }
    context.stopRecordScreen()
}