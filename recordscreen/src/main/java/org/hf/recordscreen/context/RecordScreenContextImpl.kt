package org.hf.recordscreen.context

import android.app.ActivityManager
import android.app.Application
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.widget.Toast
import androidx.activity.result.ActivityResult
import org.hf.recordscreen.IRecordScreenAidlInterface
import org.hf.recordscreen.RecordScreenListener
import org.hf.recordscreen.bean.RecordScreenCallback
import org.hf.recordscreen.bean.RecordScreenConfig
import org.hf.recordscreen.service.RecordScreenService
import java.lang.ref.WeakReference

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
        private var isRecord = false

        @JvmStatic
        private var application : WeakReference<Application> ?= null

        @JvmStatic
        var callBack : WeakReference<RecordScreenCallback>? = null

        @JvmStatic
        var config : WeakReference<RecordScreenConfig>?= null

        @JvmStatic
        var recordContext : WeakReference<RecordScreenContextImpl> ?= null

        @JvmStatic
        fun toast(string: String){
            Toast.makeText(application?.get(),string,Toast.LENGTH_SHORT).show()
        }

        @JvmStatic
        fun bindRecordScreenService(app: Application,result:ActivityResult){
            application = WeakReference(app)
            val rContext = RecordScreenContextImpl()
            val intent = Intent(application?.get(), RecordScreenService::class.java)
            intent.putExtra("result",result)
            app.bindService(intent, rContext.serviceConnection, Service.BIND_AUTO_CREATE)
        }

        @JvmStatic
        private fun checkProcessExit(processName: String,app: Application): Boolean{
            val am = app.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val processInfos = am.runningAppProcesses
            for ( info in processInfos) {
                if (info.processName == processName) {
                    return true
                }
            }
            return false
        }

        @JvmStatic
        fun isRecord(app: Application):Boolean{
            val b = checkProcessExit("${app.packageName}:record_screen",app)
            isRecord = b
            return isRecord
        }

        @JvmStatic
        fun release(){
            recordContext = null
            callBack = null
            config = null
        }
    }

    private constructor(){
        recordContext = WeakReference(this)
    }
    private val tag = "RecordScreenContextImpl"
    private var binder : IRecordScreenAidlInterface ? =null

    private val listener : RecordScreenListener.Stub by lazy(mode = LazyThreadSafetyMode.SYNCHRONIZED) {
        object : RecordScreenListener.Stub(){
            override fun onStartRecord() {
                callBack?.get()?.onStartRecord()
                isRecord = true
            }

            override fun onStopRecord() {
                isRecord = false
                callBack?.get()?.onStopRecord()
                callBack = null
                binder = null
                release()
                application?.get()?.unbindService(serviceConnection)
            }
        }
    }

    private fun checkIsRecord(){
        val b = checkProcessExit("${application?.get()?.packageName}:record_screen",application?.get()!!)
        isRecord = b
    }

    private val serviceConnection : ServiceConnection by lazy(mode = LazyThreadSafetyMode.SYNCHRONIZED) {
        object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                service?.let {
                    binder = IRecordScreenAidlInterface.Stub.asInterface(it)
                }
                binder?.setRecordConfig(config?.get())
                binder?.setListener(listener)
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                binder = null
            }
        }}
    override fun stopRecordScreen() {
        try {
            checkIsRecord()
            if(isRecord){
                binder?.stopRecord()
            }
        }catch (e : Exception){
            e.printStackTrace()
        }
    }

}