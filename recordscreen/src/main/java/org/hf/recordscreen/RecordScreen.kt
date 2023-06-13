package org.hf.recordscreen

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import org.hf.recordscreen.activity.ProxyActivity
import org.hf.recordscreen.bean.RecordScreenCallback
import org.hf.recordscreen.bean.RecordScreenConfig
import org.hf.recordscreen.context.RecordScreenContextImpl
import java.lang.ref.WeakReference

/**********************************
 * @Name:
 * @Copyright：  Antoco
 * @CreateDate： 2023/6/9 11:34
 * @author:      huang feng
 * @Version：    1.0
 * @Describe:
 **********************************/
fun AppCompatActivity.startRecordScreen(
    listener: RecordScreenCallback
){
    startRecordScreen(listener,null)
}

fun AppCompatActivity.startRecordScreen(
    listener: RecordScreenCallback,
    recordScreenConfig: RecordScreenConfig? = null
):Boolean{
    if(RecordScreenContextImpl.isRecord(application)){
        return false
    }
    RecordScreenContextImpl.config = WeakReference(recordScreenConfig)
    RecordScreenContextImpl.callBack = WeakReference(listener)
    startActivity(Intent(this,ProxyActivity::class.java))
    return true
}

fun stopRecordScreen(){
    val context = RecordScreenContextImpl.recordContext?.get() ?: return
    context.stopRecordScreen()
}