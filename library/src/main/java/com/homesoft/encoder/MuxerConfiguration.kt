package com.homesoft.encoder

import android.media.MediaFormat
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.annotation.RequiresApi
import java.io.File

/*
 * Copyright (C) 2020 Israel Flores
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

open class MuxerConfig @JvmOverloads constructor(
        var file: FileOrParcelFileDescriptor,
        var videoWidth: Int = 320,
        var videoHeight: Int = 240,
        var mimeType: String = MediaFormat.MIMETYPE_VIDEO_AVC,
        var framesPerImage: Int = 1,
        var framesPerSecond: Float = 10F,
        var bitrate: Int = 1500000,
        var frameMuxer: FrameMuxer = Mp4FrameMuxer(file, framesPerSecond),
        var iFrameInterval: Int = 10
){
    @JvmOverloads constructor(
            file: File,
            videoWidth: Int = 320,
            videoHeight: Int = 240,
            mimeType: String = MediaFormat.MIMETYPE_VIDEO_AVC,
            framesPerImage: Int = 1,
            framesPerSecond: Float = 10F,
            bitrate: Int = 1500000,
            frameMuxer: FrameMuxer = Mp4FrameMuxer(file.absolutePath, framesPerSecond),
            iFrameInterval: Int = 10
    ) : this(FileOrParcelFileDescriptor(file),
            videoWidth,
            videoHeight,
            mimeType, framesPerImage, framesPerSecond, bitrate, frameMuxer, iFrameInterval)
}

/**
 * Subclass of [MuxerConfig] that makes it easier to use in Java by rearranging constructor parameters.
 * Contains nothing but Constructors.
 *
 */
class MuxerJavaConfiguration:MuxerConfig{
    @JvmOverloads constructor(
            file: FileOrParcelFileDescriptor,
            videoWidth: Int = 320,
            videoHeight: Int = 240,
            framesPerImage: Int = 1,
            framesPerSecond: Float = 10F,
            bitrate: Int = 1500000,
            iFrameInterval: Int = 10,
            mimeType: String = MediaFormat.MIMETYPE_VIDEO_AVC,
            frameMuxer: FrameMuxer = Mp4FrameMuxer(file, framesPerSecond)
    ):super(file, videoWidth,
            videoHeight,mimeType,framesPerImage,framesPerSecond,bitrate,frameMuxer,iFrameInterval)
    @JvmOverloads constructor(
            file: File,
            videoWidth: Int = 320,
            videoHeight: Int = 240,
            framesPerImage: Int = 1,
            framesPerSecond: Float = 10F,
            bitrate: Int = 1500000,
            iFrameInterval: Int = 10,
            mimeType: String = MediaFormat.MIMETYPE_VIDEO_AVC,
            frameMuxer: FrameMuxer = Mp4FrameMuxer(file.absolutePath, framesPerSecond)
    ):super(file, videoWidth,
            videoHeight,mimeType,framesPerImage,framesPerSecond,bitrate,frameMuxer,iFrameInterval)}

interface MuxingCompletionListener {
    fun onVideoSuccessful(file: FileOrParcelFileDescriptor)
    fun onVideoError(error: Throwable)
}

interface MuxingResult

data class MuxingSuccess(
        val file: FileOrParcelFileDescriptor
): MuxingResult

data class MuxingError(
        val message: String,
        val exception: Exception
): MuxingResult

class MuxingPending:MuxingResult

/**
 * Mixture of File and ParcelFileDescriptor supposed to be a compatibility class to make it possible to use this
 * Library on devices with Android 10+, because you cant work with Files on Android 10+ anymore
 * (You don't have access to the media storage via Files).
 * If this Class is an ParcelFileDescriptor it is not supposed to use this as a File, because it may occur that this
 * File won't work because it is not initialised correctly.
 * Don't close yourParcelFileDescriptor while using this class.
 *
 *
 */
class FileOrParcelFileDescriptor : File {
    constructor(s:String): super(s){}
    constructor(file:File): super(file.path)
    @RequiresApi(Build.VERSION_CODES.O) constructor(parcelFileDescriptor:ParcelFileDescriptor): super(parcelFileDescriptor.toString()){
        this.parcelFileDescriptor=parcelFileDescriptor
        this.isParcelFileDescriptor=true
    }
    var isParcelFileDescriptor:Boolean=false
        private set
    var parcelFileDescriptor: ParcelFileDescriptor?=null
        @RequiresApi(Build.VERSION_CODES.O)
        set(value) {
            field=value
            isParcelFileDescriptor = field != null
        }
}