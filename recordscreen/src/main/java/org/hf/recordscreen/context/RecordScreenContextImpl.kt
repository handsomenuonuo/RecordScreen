package org.hf.recordscreen.context

import android.app.Activity
import android.app.ActivityManager
import android.app.Application
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.media.projection.MediaProjectionManager
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import org.hf.recordscreen.IRecordScreenAidlInterface
import org.hf.recordscreen.RecordScreenListener
import org.hf.recordscreen.bean.RecordScreenCallback
import org.hf.recordscreen.bean.RecordScreenConfig
import org.hf.recordscreen.service.RecordScreenService
import java.lang.ref.WeakReference
import kotlin.reflect.KFunction

/**********************************
 * @Name:         RecordScreenContext
 * @Copyright：  Antoco
 * @CreateDate： 2023/6/9 14:01
 * @author:      huang feng
 * @Version：    1.0
 * @Describe:
 **********************************/
internal class RecordScreenContextImpl : RecordScreenContext{

    companion object{
        @JvmStatic
        @Volatile
        var isRecord = false

        @JvmStatic
        var application : WeakReference<Application> ?= null

        @JvmStatic
        val contextMap : MutableMap<String,WeakReference<RecordScreenContextImpl>?> = mutableMapOf()

        @JvmStatic
        fun toast(string: String){
            Toast.makeText(application?.get(),string,Toast.LENGTH_SHORT).show()
        }

        @JvmStatic
        fun initRecordScreen(appCompatActivity: AppCompatActivity,recordScreenConfig : RecordScreenConfig?): RecordScreenContext {
            var c = contextMap[appCompatActivity.componentName?.className]?.get()
            if(c == null) {
               c=  RecordScreenContextImpl(appCompatActivity).apply {
                    config = recordScreenConfig
                }
            }else{
                toast("请勿重复初始化！")
            }
            return  c
        }
    }

    constructor(appCompatActivity: AppCompatActivity){
        activity = WeakReference(appCompatActivity)
        contextMap[appCompatActivity.componentName.className] = WeakReference(this)
        initRecordScreenPlugin()
        appCompatActivity.lifecycle.addObserver(object : DefaultLifecycleObserver{
            override fun onDestroy(owner: LifecycleOwner) {
                stopRecordScreen()
                release()
                appCompatActivity.lifecycle.removeObserver(this)
                super.onDestroy(owner)
            }
        })
    }
    private val tag = "RecordScreenContextImpl"
    private var binder : IRecordScreenAidlInterface ? =null
    private var callBack : WeakReference<RecordScreenCallback>? = null
    private var activity : WeakReference<AppCompatActivity>? = null
    private var launcher : ActivityResultLauncher<Intent> ? =null
    var startFunction: KFunction<*>?= null
    var stopFunction: KFunction<*> ?= null

    val listener : RecordScreenListener.Stub by lazy(mode = LazyThreadSafetyMode.SYNCHRONIZED) {
        object : RecordScreenListener.Stub(){
            override fun onStartRecord() {
                callBack?.get()?.onStartRecord()
                try {
                    startFunction?.call(activity?.get())
                }catch (e:Exception){
                    e.printStackTrace()
                }
                isRecord = true
            }

            override fun onStopRecord() {
                isRecord = false
                callBack?.get()?.onStopRecord()
                try {
                    stopFunction?.call(activity?.get())
                }catch (e:Exception){
                    e.printStackTrace()
                }
                callBack = null
                application?.get()?.unbindService(serviceConnection)
            }
        }
    }

    val serviceConnection : ServiceConnection by lazy(mode = LazyThreadSafetyMode.SYNCHRONIZED) {
        object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                service?.let {
                    binder = IRecordScreenAidlInterface.Stub.asInterface(it)
                }
                binder?.setRecordConfig(config)
                binder?.setListener(listener)
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                binder = null
            }
        }}


    private fun initRecordScreenPlugin() {
        try{
            val activity = activity?.get()
            if(activity == null){
                Log.e(tag,"初始化失败  ， activity is null")
                toast("初始化录制模块失败！")
                return
            }
            val aLauncher = activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == AppCompatActivity.RESULT_OK) {
                    Log.i(tag,"允许录制屏幕。。。。")
                    val intent = Intent(application?.get(), RecordScreenService::class.java)
                    intent.putExtra("result",result)
                    application?.get()?.bindService(intent, serviceConnection, Service.BIND_AUTO_CREATE)
                }else{
                    callBack = null
                    toast("需要点击允许，才能进行屏幕录制")
                }
            }
            launcher = aLauncher
        }catch (e:IllegalStateException){
            e.printStackTrace()
            throw IllegalStateException("Please call initRecordScreenPlugin in Activity‘s onCreate!")
        }
    }

    private fun checkProcessExit(processName: String,activity: Activity): Boolean{
        val am = activity.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val processInfos = am.runningAppProcesses
        for ( info in processInfos) {
            if (info.processName == processName) {
                return true
            }
        }
        return false
    }

    private fun release(){
        activity?.get()?.componentName?.let {
            contextMap.remove(it.toString())
        }
        startFunction = null
        stopFunction = null
        launcher = null
        binder = null
        callBack = null
        config = null
        activity = null
    }

    override fun startRecordScreen(listener : RecordScreenCallback?) {
        val activity = activity?.get()
        if (activity == null) {
            Log.e(tag,"开始录制失败  ， activity is null")
            toast("开始录制失败！")
            return
        }
        listener?.let { callBack = WeakReference(listener)  }
        Log.e(tag,"${activity.packageName}:record_screen")
        if(isRecord && checkProcessExit("${activity.packageName}:record_screen",activity)){
            toast("上一次录屏还未结束，请不要重复开启！")
            Log.e(tag,"上一次录屏还未结束，请不要重复开启！")
            return
        }
        isRecord = false
        if(launcher == null ){
            throw IllegalStateException("Please call initRecordScreenPlugin in Activity‘s onCreate first!")
        }
        val mediaProjectionManager = activity.getSystemService(AppCompatActivity.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val permissionIntent = mediaProjectionManager.createScreenCaptureIntent()
        launcher!!.launch(permissionIntent)
    }

    override fun stopRecordScreen() {
        val activity = activity?.get()
        if (activity == null) {
            Log.e(tag,"结束录制失败  ， activity is null")
            toast("结束录制失败！")
            return
        }
        try {
            if(isRecord && checkProcessExit("${activity.packageName}:record_screen",activity)){
                binder?.stopRecord()
            }
        }catch (e : Exception){
            e.printStackTrace()
        }
    }

}