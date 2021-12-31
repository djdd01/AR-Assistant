package com.example.ar

import io.agora.rtc.mediaio.IVideoSink
import io.agora.rtc.mediaio.MediaIO
import java.nio.ByteBuffer

class AgoraVideoRender(uid: Int, local: Boolean) : IVideoSink {
    private val peer: Peer = Peer()
    private val mIsLocal: Boolean
    override fun onInitialize(): Boolean {
        return true
    }
    fun getPeer(): Peer? {
        return peer
    }
    override fun onStart(): Boolean {
        return true
    }

    override fun onStop() {}
    override fun onDispose() {}
    override fun getEGLContextHandle(): Long {
        return 0
    }

    override fun getBufferType(): Int {
        return MediaIO.BufferType.BYTE_BUFFER.intValue()
    }

    override fun getPixelFormat(): Int {
        return MediaIO.PixelFormat.RGBA.intValue()
    }

    override fun consumeByteBufferFrame(
        buffer: ByteBuffer,
        format: Int,
        width: Int,
        height: Int,
        rotation: Int,
        ts: Long
    ) {
        if (!mIsLocal) {
            peer.data = buffer
            peer.width = width
            peer.height = height
            peer.rotation = rotation
            peer.ts = ts
        }
    }

    override fun consumeByteArrayFrame(
        data: ByteArray,
        format: Int,
        width: Int,
        height: Int,
        rotation: Int,
        ts: Long
    ) {
        //Log.e("AgoraVideoRender", "consumeByteArrayFrame");
    }

    override fun consumeTextureFrame(
        texId: Int,
        format: Int,
        width: Int,
        height: Int,
        rotation: Int,
        ts: Long,
        matrix: FloatArray
    ) {
    }

    init {
        peer.uid = uid
        mIsLocal = local
    }
}