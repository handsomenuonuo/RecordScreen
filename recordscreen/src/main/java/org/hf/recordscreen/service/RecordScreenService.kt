package org.hf.recordscreen.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.util.DisplayMetrics
import android.util.Log
import android.view.Surface
import androidx.activity.result.ActivityResult
import androidx.core.app.NotificationCompat
import androidx.core.content.PackageManagerCompat.LOG_TAG
import org.hf.recordscreen.IRecordScreenAidlInterface
import org.hf.recordscreen.R
import org.hf.recordscreen.RecordScreenListener
import org.hf.recordscreen.bean.RecordScreenConfig
import java.io.File
import java.nio.ByteBuffer
import kotlin.concurrent.thread
import kotlin.system.exitProcess


/**********************************
 * @Name:         RecordScreenService
 * @Copyright：  Antoco
 * @CreateDate： 2023/6/7 18:08
 * @author:      huang feng
 * @Version：    1.0
 * @Describe:
 **********************************/
internal class RecordScreenService : Service() {

    private val NOTIFICATION_FLAG = 0X12

    private var mediaProjection: MediaProjection ?= null

    private val VIDEO_MIME_TYPE = "video/avc"
    private var VIDEO_WIDTH = 0
    private var VIDEO_HEIGHT = 0
    private var VIDEO_BITRATE = 0
    private var VIDEO_FRAME_RATE = 0
    private var VIDEO_IFRAME_INTERVAL = 0

    private var VIDEO_PATH :String ?= null
    private var VIDEO_FILE_DESCRIPTOR : ParcelFileDescriptor ?= null

    private var encoder : MediaCodec? = null
    private var mediaMuxer : MediaMuxer ?= null
    @Volatile
    private var isStarted = false
    private var recordThread:Thread ?=null
    private lateinit var inputSurface: Surface
    private var recordScreenListener : RecordScreenListener?=null

    private var videoBufferInfo : MediaCodec.BufferInfo ?= null
    // 获取视频轨道索引
    private var videoTrackIndex = -1

    private var outVideoPath :String ? = null

    private var virtualDisplay : VirtualDisplay? = null
    private var config : RecordScreenConfig? = null

    private val binder : IRecordScreenAidlInterface.Stub by lazy(mode = LazyThreadSafetyMode.SYNCHRONIZED){
        object : IRecordScreenAidlInterface.Stub() {
            override fun setListener(listener: RecordScreenListener?) {
                recordScreenListener = listener
                startRecordThread()
            }

            override fun setRecordConfig(recordScreenConfig: RecordScreenConfig?) {
               Log.e("test", "setRecordConfig = $recordScreenConfig")
                config = recordScreenConfig
            }


            override fun stopRecord() {
                isStarted = false
            }
        }
    }


    override fun onBind(intent: Intent?): IBinder {
        val result = intent!!.getParcelableExtra<ActivityResult>("result")!!
        mediaProjection = (getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager)
            .getMediaProjection(result.resultCode, result.data!!)
        initCodec()
        createVirtualDisplay()
        return binder
    }

    private fun initMediaMuxer() {
        config?.let {
            VIDEO_WIDTH = it.videoWidth
            VIDEO_HEIGHT = it.videoHeight
            VIDEO_BITRATE = it.videoBitRate
            VIDEO_FRAME_RATE = it.videoFrameRate
            VIDEO_IFRAME_INTERVAL = it.videoIFrameInterval
            VIDEO_FILE_DESCRIPTOR= it.videoFileDescriptor
            VIDEO_PATH = it.videoPath
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if(VIDEO_FILE_DESCRIPTOR != null){
                    outVideoPath = VIDEO_FILE_DESCRIPTOR!!.fileDescriptor.toString()
                    Log.i("RecordScreenService","保存视频到1： $outVideoPath")
                    mediaMuxer = MediaMuxer(VIDEO_FILE_DESCRIPTOR!!.fileDescriptor, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                    return
                }
            }
            if(VIDEO_PATH!= null){
                outVideoPath = VIDEO_PATH
                Log.i("RecordScreenService","保存视频到2： $outVideoPath")
                mediaMuxer = MediaMuxer(outVideoPath!!, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            }else{
                VIDEO_PATH = getExternalFilesDir("recordVideos").toString()
                outVideoPath = VIDEO_PATH + File.separator + "${System.currentTimeMillis()}.mp4"
                Log.i("RecordScreenService","保存视频到3： $outVideoPath")
                mediaMuxer = MediaMuxer(outVideoPath!!, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            }
        }catch (e: Exception){
            e.printStackTrace()
            stopSelf()
        }

    }

    override fun onCreate() {
        super.onCreate()
        initNotification()
    }

    private fun startRecordThread(){
        initMediaMuxer()
        recordThread = thread{
            try {
                isStarted = true
                recordScreenListener?.onStartRecord()
                videoBufferInfo = MediaCodec.BufferInfo()
                encoder!!.start()
                while (true){
                    if (!isStarted) {
                        encoder?.let {
                            it.stop()
                            it.release()
                            encoder = null
                        }
                        mediaProjection?.let {
                            it.stop()
                            mediaProjection = null
                        }
                        virtualDisplay?.let {
                            it.release()
                            virtualDisplay = null
                        }
                        mediaMuxer?.let {
                            it.stop()
                            it.release()
                            mediaMuxer = null
                        }
                        break
                    }
                    var outputBufferIndex = encoder!!.dequeueOutputBuffer(videoBufferInfo!!, 10000)
                    if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        // 获得trackIndex

                        /**************这两段吊代码，为什么不放在这儿就出错！！！******************/
                        videoTrackIndex = mediaMuxer!!.addTrack(encoder!!.outputFormat)
                        mediaMuxer!!.start()
                        /**************这两段吊代码，为什么不放在这儿就出错！！！******************/
                    }else if (outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER){
                        try {
                            Thread.sleep(10)
                        } catch (e: InterruptedException) {
                            e.printStackTrace()
                        }
                    }else if(outputBufferIndex >= 0){
                        var outputBuffer: ByteBuffer? = encoder!!.getOutputBuffer(outputBufferIndex)
                        if (videoBufferInfo!!.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                            outputBuffer = null
                        }
                        outputBuffer?.let {
                            it.position(videoBufferInfo!!.offset)
                            it.limit(videoBufferInfo!!.offset + videoBufferInfo!!.size)
                            if (videoBufferInfo!!.size > 0) {
                                // 将数据写入MediaMuxer
                                mediaMuxer?.writeSampleData(videoTrackIndex, it, videoBufferInfo!!)
                            }
                            encoder!!.releaseOutputBuffer(outputBufferIndex, false)
                        }
                    }
                }
            }catch (e : Exception){
                e.printStackTrace()
            }finally {
                recordScreenListener?.onStopRecord()
                VIDEO_FILE_DESCRIPTOR?.close()
                recordScreenListener = null
                videoBufferInfo = null
            }
        }
    }

    private fun initCodec(){
        if(VIDEO_WIDTH == 0) VIDEO_WIDTH = resources.displayMetrics.widthPixels
        if(VIDEO_HEIGHT == 0) VIDEO_HEIGHT = resources.displayMetrics.heightPixels
        if(VIDEO_FRAME_RATE == 0) VIDEO_FRAME_RATE = 25
        if(VIDEO_BITRATE == 0) VIDEO_BITRATE = VIDEO_WIDTH * VIDEO_HEIGHT * 4
        if(VIDEO_IFRAME_INTERVAL == 0) VIDEO_IFRAME_INTERVAL = 1
        val format = MediaFormat.createVideoFormat(VIDEO_MIME_TYPE, VIDEO_WIDTH, VIDEO_HEIGHT)
        format.setInteger(MediaFormat.KEY_BIT_RATE, VIDEO_BITRATE)
        format.setInteger(MediaFormat.KEY_FRAME_RATE, VIDEO_FRAME_RATE)
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, VIDEO_IFRAME_INTERVAL)
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)

        encoder = MediaCodec.createEncoderByType(VIDEO_MIME_TYPE).apply {
            configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            inputSurface = createInputSurface()
        }
    }

    private fun createVirtualDisplay(){
        val callback: MediaProjection.Callback = object : MediaProjection.Callback() {
            override fun onStop() {
            }
        }
        virtualDisplay = mediaProjection!!.createVirtualDisplay(
            "ScreenRecording",
            VIDEO_WIDTH,
            VIDEO_HEIGHT,
            DisplayMetrics.DENSITY_HIGH,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR ,
            inputSurface,
            null,
            null
        )
        mediaProjection!!.registerCallback(callback, null)
    }


    override fun onDestroy() {
        super.onDestroy()
        try {
            mediaMuxer?.let {
                it.stop()
                it.release()
                mediaMuxer = null
            }
        }catch (e: Exception){
            e.printStackTrace()
        }
        exitProcess(0)
    }

    private fun initNotification() {
        val mNotificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val builder  = NotificationCompat.Builder(applicationContext,"org.hf.srcreenrecord")
        //  兼容代码
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val CHANNEL_ONE_ID = "org.hf.RecordScreenService"
            val CHANNEL_ONE_NAME = "Channel ONE"
            // 改方法是 Android 8.0 以后才有的
            builder.setChannelId(CHANNEL_ONE_ID)
            var notificationChannel = NotificationChannel(
                CHANNEL_ONE_ID,
                CHANNEL_ONE_NAME, NotificationManager.IMPORTANCE_HIGH
            )
            notificationChannel.enableLights(false)
            notificationChannel.setSound(null, null)
            notificationChannel.lightColor = Color.RED
            notificationChannel.setShowBadge(true)
            notificationChannel.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            mNotificationManager.createNotificationChannel(notificationChannel)
        }
        builder
            .setTicker("正在录制屏幕...") // statusBar上的提示
            .setContentTitle("正在录制屏幕...") // 设置下拉列表里的标题
            .setSmallIcon(R.mipmap.ic_record) // 设置状态栏内的小图标24X24
            .setWhen(System.currentTimeMillis()) // 设置该通知发生的时间
            .setPriority(NotificationCompat.PRIORITY_HIGH) //优先级高
            .setAutoCancel(false)

        val notification: Notification = builder.build() // 获取构建好的Notification

        notification.flags = notification.flags or
                NotificationCompat.FLAG_ONGOING_EVENT or//将此通知放到通知栏的"Ongoing"即"正在运行"组中
                NotificationCompat.FLAG_NO_CLEAR //表明在点击了通知栏中的"清除通知"后，此通知不清除，常与FLAG_ONGOING_EVENT一起使用
        mNotificationManager.notify(NOTIFICATION_FLAG, notification)

        // 启动前台服务
        // 参数一：唯一的通知标识；参数二：通知消息。
        startForeground(NOTIFICATION_FLAG, notification) // 开始前台服务
    }

}