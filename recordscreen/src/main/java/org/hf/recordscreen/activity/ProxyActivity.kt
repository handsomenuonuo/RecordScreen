package org.hf.recordscreen.activity

import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.appcompat.app.AppCompatActivity
import org.hf.recordscreen.context.RecordScreenContextImpl

/**********************************
 * @Name:         ProxyActivity
 * @Copyright：  Antoco
 * @CreateDate： 2023/6/13 13:36
 * @author:      huang feng
 * @Version：    1.0
 * @Describe:
 **********************************/
internal class ProxyActivity : AppCompatActivity() {

    private lateinit var fLauncher : ActivityResultLauncher<Intent>
    private lateinit var pLauncher : ActivityResultLauncher<Intent>
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val window = window
        window.setGravity(Gravity.LEFT or Gravity.TOP)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        )
        val params = window.attributes
        params.x = 0
        params.y = 0
        params.height = 1
        params.width = 1
        window.attributes = params
        RecordScreenContextImpl.bindApplication(application)
        fLauncher = registerForActivityResult(StartActivityForResult()) { result ->
            if(!Settings.canDrawOverlays(this)){
                RecordScreenContextImpl.callBack?.get()?.onRecordFailure("需要开启悬浮框权限！")
                RecordScreenContextImpl.toast("需要开启悬浮框权限！")
                finish()
                return@registerForActivityResult
            }
            requireProjectionPermission()
        }
        pLauncher =  registerForActivityResult(StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                RecordScreenContextImpl.bindRecordScreenService(application,result)
            }else{
                RecordScreenContextImpl.callBack?.get()?.onRecordFailure("需要点击允许，才能进行屏幕录制")
                RecordScreenContextImpl.release()
                RecordScreenContextImpl.toast("需要点击允许，才能进行屏幕录制")
            }
            finish()
        }

        checkFloatingPermission()
    }

    private fun checkFloatingPermission(){
        RecordScreenContextImpl.config?.let {
            if(it.useFloatingView){
                if(!Settings.canDrawOverlays(this)){
                    RecordScreenContextImpl.toast("需要开启悬浮框权限！")
                    requireFloatingPermission()
                    return
                }
            }
            requireProjectionPermission()
        }
    }

    private fun requireFloatingPermission(){
        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
        intent.data = Uri.parse("package:$packageName")
        fLauncher.launch(intent)
    }

    private fun requireProjectionPermission(){
        val mediaProjectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val permissionIntent = mediaProjectionManager.createScreenCaptureIntent()
        pLauncher.launch(permissionIntent)
    }
}