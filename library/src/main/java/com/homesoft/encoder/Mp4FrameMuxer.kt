package com.homesoft.encoder

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.annotation.RequiresApi
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit

/*
 * Copyright (C) 2020 Homesoft, LLC
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

class Mp4FrameMuxer(private val muxer: MediaMuxer, private val fps: Float) : FrameMuxer {

    constructor(fileOrParcelFileDescriptor: FileOrParcelFileDescriptor, fps: Float) : this(forceOpenMediaMuxer(fileOrParcelFileDescriptor),fps){
        parcelFileDescriptor=fileOrParcelFileDescriptor.parcelFileDescriptor
    }
    constructor(path: String, fps: Float): this(MediaMuxer(path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4),fps)

    companion object {
        private val TAG: String = Mp4FrameMuxer::class.java.simpleName
        fun forceOpenMediaMuxer(fileOrParcelFileDescriptor: FileOrParcelFileDescriptor): MediaMuxer =
                openMediaMuxer(fileOrParcelFileDescriptor)
                        ?: throw IllegalStateException(
                                "You didn't initialise your FileOrParcelFileDescriptor correctly!" +
                                        " Only Android Versions over VersionCode.O can manage ParcelFile" +
                                        "Descriptors for MediaMuxers!")
        fun openMediaMuxer(fileOrParcelFileDescriptor: FileOrParcelFileDescriptor): MediaMuxer? =
                if(fileOrParcelFileDescriptor.isParcelFileDescriptor){
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                        fileOrParcelFileDescriptor.parcelFileDescriptor?.let { openMediaMuxer(it) }
                    else null
                }else MediaMuxer(fileOrParcelFileDescriptor.absolutePath,MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        @RequiresApi(Build.VERSION_CODES.O)
        fun openMediaMuxer(parcelFileDescriptor: ParcelFileDescriptor): MediaMuxer =
                MediaMuxer(parcelFileDescriptor.fileDescriptor,MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    }

    private var parcelFileDescriptor:ParcelFileDescriptor?=null

    private val frameUsec: Long = run {
        (TimeUnit.SECONDS.toMicros(1L) / fps).toLong()
    }
    private var started = false
    private var videoTrackIndex = 0
    private var audioTrackIndex = 0
    private var videoFrames = 0
    private var finalVideoTime: Long = 0

    override fun isStarted(): Boolean {
        return started
    }

    override fun start(videoFormat: MediaFormat, audioExtractor: MediaExtractor?) {
        // now that we have the Magic Goodies, start the muxer
        audioExtractor?.selectTrack(0)
        val audioFormat = audioExtractor?.getTrackFormat(0)
        videoTrackIndex = muxer.addTrack(videoFormat)
        audioFormat?.run {
            audioTrackIndex = muxer.addTrack(audioFormat)
            Log.d("Audio format: %s", audioFormat.toString())
        }
        Log.d("Video format: %s", videoFormat.toString())
        muxer.start()
        started = true
    }

    override fun muxVideoFrame(encodedData: ByteBuffer, bufferInfo: MediaCodec.BufferInfo) {
        // This code will break if the encoder supports B frames.
        // Ideally we would use set the value in the encoder,
        // don't know how to do that without using OpenGL
        finalVideoTime = frameUsec * videoFrames++
        bufferInfo.presentationTimeUs = finalVideoTime

        muxer.writeSampleData(videoTrackIndex, encodedData, bufferInfo)
    }

    override fun muxAudioFrame(encodedData: ByteBuffer, audioBufferInfo: MediaCodec.BufferInfo) {
        muxer.writeSampleData(audioTrackIndex, encodedData, audioBufferInfo)
    }

    override fun release() {
        muxer.stop()
        parcelFileDescriptor?.close()
        muxer.release()
    }

    override fun getVideoTime(): Long {
        return finalVideoTime
    }

    override fun getMediaMuxer(): MediaMuxer {
        return muxer
    }
}
