package org.hf.recordscreen.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.provider.Settings
import android.util.DisplayMetrics
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.Surface
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.ActivityResult
import androidx.core.app.NotificationCompat
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
    private var mNotificationManager : NotificationManager?=null
    private var mainHandler : Handler? = Handler(Looper.getMainLooper())
    private var timeTextView : TextView ?= null
    private var time = 0
    private var runnable : Runnable ? =null
    private var startTime = 0L
    private var isSectionUseful = false
    private var sectionCount = 1
    private var stopByAuto = false

    private var floatView : View? = null

    private var perSectionTime = 0
    private var totalRecordTime = 0

    private val binder : IRecordScreenAidlInterface.Stub by lazy(mode = LazyThreadSafetyMode.SYNCHRONIZED){
        object : IRecordScreenAidlInterface.Stub() {
            override fun setListener(listener: RecordScreenListener?) {
                recordScreenListener = listener
                start()
            }

            override fun setRecordConfig(recordScreenConfig: RecordScreenConfig?) {
                config = recordScreenConfig
                config?.let {
                    VIDEO_WIDTH = it.videoWidth
                    VIDEO_HEIGHT = it.videoHeight
                    VIDEO_BITRATE = it.videoBitRate
                    VIDEO_FRAME_RATE = it.videoFrameRate
                    VIDEO_IFRAME_INTERVAL = it.videoIFrameInterval
                    VIDEO_FILE_DESCRIPTOR= it.videoFileDescriptor
                    VIDEO_PATH = it.videoPath
                    perSectionTime = it.perSectionTime
                    totalRecordTime = it.totalRecordTime

                    if(perSectionTime>0 && VIDEO_FILE_DESCRIPTOR == null){//VIDEO_FILE_DESCRIPTOR方式写入的视频，无法自行创建
                        if(perSectionTime<=5)perSectionTime = 5
                        isSectionUseful = true
                    }
                }
                mainHandler?.post{
                    config?.let {
                        if(it.useFloatingView){
                            addTestFloatView()
                        }
                    }
                }
            }

            override fun stopRecord() {
                stopByAuto = false
                isStarted = false
            }
        }
    }


    override fun onBind(intent: Intent?): IBinder {
        val result = intent!!.getParcelableExtra<ActivityResult>("result")!!
        mediaProjection = (getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager)
            .getMediaProjection(result.resultCode, result.data!!)
        return binder
    }

    private fun initMediaMuxer() : Boolean{
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if(VIDEO_FILE_DESCRIPTOR != null){
                    outVideoPath = VIDEO_FILE_DESCRIPTOR!!.fileDescriptor.toString()
                    Log.i("RecordScreenService","保存视频到1： $outVideoPath")
                    mediaMuxer = MediaMuxer(VIDEO_FILE_DESCRIPTOR!!.fileDescriptor, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                    return true
                }
            }
            if(VIDEO_PATH!= null){
                outVideoPath = if(isSectionUseful){
                    VIDEO_PATH!!.split('.')[0]+"_$sectionCount"+".mp4"
                }else{
                    VIDEO_PATH
                }
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
            recordScreenListener?.onRecordFailure(e.message)
            return false
        }
        return true
    }

    override fun onCreate() {
        super.onCreate()
        initNotification()
    }

    private fun start(){
        mainHandler?.post {
            initCodec()
            startRecordThread()
        }
    }

    private fun startRecordThread(){
        recordThread = thread{
            try {
                if(!initMediaMuxer()) return@thread
                startTimeCount()
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
                if(!stopByAuto || ((totalRecordTime > 0)&&(time >= totalRecordTime-4))){
                    mediaProjection?.let {
                        it.stop()
                        mediaProjection = null
                    }
                    recordScreenListener?.onStopRecord()
                    VIDEO_FILE_DESCRIPTOR?.close()
                    recordScreenListener = null
                    videoBufferInfo = null
                }else{
                    start()
                }
            }
        }

    }

    private fun startTimeCount() {
        if(startTime == 0L){
            time = 0

            runnable = Runnable {
                if(startTime == 0L)startTime = System.currentTimeMillis()
                time++
                recordScreenListener?.onRecordTime(time)
                timeTextView?.text = String.format("%02d:%02d:%02d", time / 3600, (time % 3600) / 60, time % 60)
                if((totalRecordTime > 0) && (time >= totalRecordTime)){//有录制时间，并且录制时间到了
                    stopByAuto = true
                    isStarted = false
                    return@Runnable
                }
                if(time >0 && isSectionUseful && (time%(perSectionTime+1) ==0)){//分段时间到了
                    sectionCount++
                    stopByAuto = true
                    isStarted = false
                }
                val l = startTime + time * 1000
                val delay = l - System.currentTimeMillis()
                mainHandler?.postDelayed(runnable!!,if(delay>0) delay else 0 )
            }
            mainHandler?.postDelayed(runnable!!,1000)
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
        createVirtualDisplay()
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
        mainHandler?.removeCallbacksAndMessages(null)
        mainHandler = null
        try {
            mediaMuxer?.let {
                it.stop()
                it.release()
                mediaMuxer = null
            }
        }catch (e: Exception){
            e.printStackTrace()
        }
        mNotificationManager?.cancelAll()
        floatView?.let {
            val wm =  getSystemService(Context.WINDOW_SERVICE) as WindowManager
            wm.removeView(it)
        }
        exitProcess(0)
    }

    private fun initNotification() {
        mNotificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
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
            mNotificationManager?.createNotificationChannel(notificationChannel)
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
        mNotificationManager?.notify(NOTIFICATION_FLAG, notification)
        // 启动前台服务
        // 参数一：唯一的通知标识；参数二：通知消息。
        startForeground(NOTIFICATION_FLAG, notification) // 开始前台服务
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun addTestFloatView():Boolean{
        if(Settings.canDrawOverlays(this)){
            val wm =  getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val layoutParams = WindowManager.LayoutParams()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                layoutParams.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            }else {
                layoutParams.type = WindowManager.LayoutParams.TYPE_PHONE
            }
            //悬浮窗弹出的位置
            layoutParams.gravity = Gravity.START or Gravity.TOP
            layoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            layoutParams.format = PixelFormat.RGBA_8888
            layoutParams.width = WindowManager.LayoutParams.WRAP_CONTENT
            layoutParams.height = WindowManager.LayoutParams.WRAP_CONTENT
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                layoutParams.fitInsetsTypes = 0
            }else{
                layoutParams.flags = layoutParams.flags or WindowManager.LayoutParams.FLAG_FULLSCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            }
            layoutParams.x =40
            layoutParams.y = 40
            val layoutInflater = LayoutInflater.from(this)
            floatView = layoutInflater.inflate(R.layout.view_floating, null)
            wm.addView(floatView, layoutParams)
            var initialX = 0
            var initialY = 0
            var vX = 0
            var vY = 0
            floatView!!.setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        // 记录手指按下时的初始位置
                        val initialParams = floatView!!.layoutParams as WindowManager.LayoutParams
                        initialX = event.rawX.toInt()
                        initialY = event.rawY.toInt()
                        vX = initialParams.x
                        vY = initialParams.y
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        // 计算手指移动的距离
                        val deltaX = event.rawX.toInt() - initialX
                        val deltaY = event.rawY.toInt() - initialY
                        // 更新悬浮框视图的位置
                        var params  : WindowManager.LayoutParams = floatView!!.layoutParams as WindowManager.LayoutParams
                        params.x = vX + deltaX
                        params.y = vY + deltaY
                        if(params.x >= wm.defaultDisplay.width - floatView!!.width){
                            params.x = wm.defaultDisplay.width - floatView!!.width
                        }else if(params.x < 0){
                            params.x = 0
                        }
                        if(params.y >= wm.defaultDisplay.height - floatView!!.height){
                            params.y = wm.defaultDisplay.height - floatView!!.height
                        }else if(params.y < 0){
                            params.y = 0
                        }
                        // 更新悬浮框视图的显示位置
                        wm.updateViewLayout(floatView, params )
                        true
                    }
                    else -> false
                }
            }
            floatView!!.findViewById<ImageView>(R.id.img_start).setOnClickListener {
                stopByAuto = false
                isStarted = false
            }
            timeTextView = floatView!!.findViewById<TextView>(R.id.tv_time)
            return true
        }else{
            return false
        }
    }
}

