package com.example.ar

import io.agora.rtc.mediaio.IVideoFrameConsumer
import io.agora.rtc.mediaio.IVideoSource
import io.agora.rtc.mediaio.MediaIO

class AgoraVideoSource : IVideoSource {
    private var consumer: IVideoFrameConsumer? = null

    override fun onInitialize(iVideoFrameConsumer: IVideoFrameConsumer): Boolean {
        consumer = iVideoFrameConsumer
        return true
    }

    override fun onStart(): Boolean {
        return true
    }

    override fun onStop() {}
    override fun onDispose() {}
    override fun getBufferType(): Int {
        return MediaIO.BufferType.BYTE_ARRAY.intValue()
    }

    override fun getCaptureType(): Int {
        return 0
    }

    override fun getContentHint(): Int {
        return 0
    }

    fun getConsumer(): IVideoFrameConsumer? {
        return consumer
    }
}