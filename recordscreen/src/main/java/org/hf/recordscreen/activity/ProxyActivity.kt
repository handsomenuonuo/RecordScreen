package org.hf.recordscreen.activity

import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import androidx.activity.result.contract.ActivityResultContracts
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

        val aLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                RecordScreenContextImpl.bindRecordScreenService(application,result)
                finish()
            }else{
                RecordScreenContextImpl.release()
                RecordScreenContextImpl.toast("需要点击允许，才能进行屏幕录制")
            }
        }
        val mediaProjectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val permissionIntent = mediaProjectionManager.createScreenCaptureIntent()
        aLauncher.launch(permissionIntent)
    }
}