package com.homesoft.encoder

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.media.MediaCodecList
import android.media.MediaCodecList.REGULAR_CODECS
import android.util.Log
import androidx.annotation.RawRes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import java.io.File
import java.io.IOException
import java.lang.RuntimeException

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

class Muxer(private val context: Context, private val file: FileOrParcelFileDescriptor) {
    constructor(context: Context, config: MuxerConfig) : this(context, config.file) {
        muxerConfig = config
    }

    companion object {
        private val TAG = Muxer::class.java.simpleName
    }

    // Initialize a default configuration
    private var muxerConfig: MuxerConfig = MuxerConfig(file)
    private var muxingCompletionListener: MuxingCompletionListener? = null

    /**
     * Build the Muxer with a custom [MuxerConfig]
     *
     * @param config: muxer configuration object
     */
    fun setMuxerConfig(config: MuxerConfig) {
        muxerConfig = config
    }

    fun getMuxerConfig() = muxerConfig

    private var frameBuilder: FrameBuilder?=null

    @JvmOverloads
    fun prepareMuxingFrameByFrame(@RawRes audioTrack: Int? = null): MuxingResult{
        Log.d(TAG, "Generating video")
        this.frameBuilder = FrameBuilder(context, muxerConfig, audioTrack)

        try {
            this.frameBuilder?.start()
        } catch (e: IOException) {
            Log.e(TAG, "Start Encoder Failed")
            e.printStackTrace()
            muxingCompletionListener?.onVideoError(e)
            return MuxingError("Start encoder failed", e)
        }
        return MuxingPending()
    }

    /**
     * Mux a image into the Mp4
     *
     * You have to call [Muxer.prepareMuxingFrameByFrame] first, or a RuntimeException will be thrown
     *
     * Image must be one of the following formats:
     * [Bitmap] @RawRes Int [Canvas]
     */
    fun muxFrame(image :Any){
        if (this.frameBuilder?.createFrame(image) == null){
            throw RuntimeException("An Exception occurred or you haven't called Muxer#prepareMuxingFrameByFrame first!")
        }
    }

    @JvmOverloads
    fun endMuxingFrameByFrame(beforeRelease:Runnable?=null):MuxingResult{
        if (frameBuilder==null){
            return MuxingError("frameBuilder == null", RuntimeException("An Exception Occurred or you haven't called Muxer#prepareMuxingFrameByFrame first!"))
        }
        // Release the video codec so we can mux in the audio frames separately
        frameBuilder?.releaseVideoCodec()

        // Add audio
        frameBuilder?.muxAudioFrames()

        beforeRelease?.run()

        // Release everything
        frameBuilder?.releaseAudioExtractor()
        frameBuilder?.releaseMuxer()
        frameBuilder=null

        muxingCompletionListener?.onVideoSuccessful(file)
        return MuxingSuccess(file)
    }




    /**
     * List containing images in any of the following formats:
     * [Bitmap] [@DrawRes Int] [Canvas]
     */
    @JvmOverloads
    fun mux(imageList: List<Any>,
            @RawRes audioTrack: Int? = null): MuxingResult {
        // Returns on a callback a finished video
        Log.d(TAG, "Generating video")
        val frameBuilder = FrameBuilder(context, muxerConfig, audioTrack)

        try {
            frameBuilder.start()
        } catch (e: IOException) {
            Log.e(TAG, "Start Encoder Failed")
            e.printStackTrace()
            muxingCompletionListener?.onVideoError(e)
            return MuxingError("Start encoder failed", e)
        }

        for (image in imageList) {
            frameBuilder.createFrame(image)
        }

        // Release the video codec so we can mux in the audio frames separately
        frameBuilder.releaseVideoCodec()

        // Add audio
        frameBuilder.muxAudioFrames()

        // Release everything
        frameBuilder.releaseAudioExtractor()
        frameBuilder.releaseMuxer()

        muxingCompletionListener?.onVideoSuccessful(file)
        return MuxingSuccess(file)
    }

    @JvmOverloads
    suspend fun muxAsync(imageList: List<Any>, @RawRes audioTrack: Int? = null): MuxingResult {
        return mux(imageList, audioTrack)
    }

    fun setOnMuxingCompletedListener(muxingCompletionListener: MuxingCompletionListener) {
        this.muxingCompletionListener = muxingCompletionListener
    }
}

fun isCodecSupported(mimeType: String?): Boolean {
    val codecs = MediaCodecList(REGULAR_CODECS)
    for (codec in codecs.codecInfos) {
        if (!codec.isEncoder) {
            continue
        }
        for (type in codec.supportedTypes) {
            if (type == mimeType) return true
        }
    }
    return false
}
