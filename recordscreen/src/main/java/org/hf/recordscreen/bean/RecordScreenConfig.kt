package org.hf.recordscreen.bean

import android.os.ParcelFileDescriptor
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**********************************
 * @Name:         RecordScreenConfig
 * @Copyright：  Antoco
 * @CreateDate： 2023/6/9 11:32
 * @author:      huang feng
 * @Version：    1.0
 * @Describe:
 **********************************/
@Parcelize
data class RecordScreenConfig(
    var videoWidth :Int= 0,
    var videoHeight  :Int = 0,
    var videoBitRate  :Int = 0,
    var videoFrameRate  :Int = 0,
    var videoIFrameInterval  :Int = 0,
    var videoPath:String?=null,
    var videoFileDescriptor: ParcelFileDescriptor?=null
) : Parcelable {

    fun setVideoWidthHeight(width: Int,height: Int): RecordScreenConfig {
        videoWidth = width
        videoHeight = height
        return this
    }

    fun setVideoBitRate(bitrate: Int): RecordScreenConfig {
        videoBitRate = bitrate
        return this
    }

    fun setVideoFrameRate(frameRate: Int): RecordScreenConfig {
        videoFrameRate = frameRate
        return this
    }

    fun setVideoIFrameInterval(interval: Int): RecordScreenConfig {
        videoIFrameInterval = interval
        return this
    }

    fun setVideoPath(path: String): RecordScreenConfig {
        videoPath = path
        return this
    }

    fun setVideoFileDescriptor(fileDescriptor: ParcelFileDescriptor): RecordScreenConfig {
        videoFileDescriptor = fileDescriptor
        return this
    }


}