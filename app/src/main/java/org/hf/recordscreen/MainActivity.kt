package org.hf.recordscreen

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Button
import org.hf.recordscreen.annotation.RecordScreen
import org.hf.recordscreen.annotation.RecordScreenStart
import org.hf.recordscreen.annotation.RecordScreenStop
import org.hf.recordscreen.bean.RecordScreenCallback

@RecordScreen
class MainActivity : AppCompatActivity() {
    val callback = object : RecordScreenCallback(){
        override fun onStartRecord() {
            Log.e("test", "onStartRecord")
        }

        override fun onStopRecord() {
            Log.e("test", "onStopRecord")
        }

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)


//
        findViewById<Button>(R.id.start).setOnClickListener {
            startRecordScreen()
//            startRecordScreen(callback)
//            startRecordScreen(recordScreenConfig)
//            startRecordScreen(callback,recordScreenConfig)
//            getRecordScreenContext()?.startRecordScreen(callback)
//            startActivity(Intent(this, MainActivity1::class.java))
        }

        findViewById<Button>(R.id.stop).setOnClickListener {
            stopRecordScreen()
        }
    }

    @RecordScreenStart
    fun onRecordScreenStart() {
        Log.e("test", "onRecordScreenStart")
    }

    @RecordScreenStop
    fun onRecordScreenStop() {
        Log.e("test", "onRecordScreenStop")
    }
}