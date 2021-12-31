package com.example.ar

import android.Manifest
import android.app.Instrumentation
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.view.*
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.ar.core.exceptions.*
import io.agora.rtc.Constants
import io.agora.rtc.IRtcEngineEventHandler
import io.agora.rtc.RtcEngine
import io.agora.rtc.video.VideoCanvas
import io.agora.rtc.video.VideoEncoderConfiguration
import java.nio.ByteBuffer
import java.util.*

class UserActivity : AppCompatActivity() {
    private val PERMISSION_REQUEST_CODE = 0X0001
    private var mWidth = 0
    private var mHeight = 0
    private val instrumentation = Instrumentation()
    private lateinit var token:String
    private lateinit var channelName : String
    //Agora
    private var mRtcEngine: RtcEngine? = null
    private var mSource: AgoraVideoSource? = null
    private var mRender: AgoraVideoRender? = null
    private var mSenderHandler: Handler? = null
    private var mRemoteView: SurfaceView? = null
    private var mRemoteContainer: ConstraintLayout? = null

    private val mRtcEventHandler: IRtcEngineEventHandler = object : IRtcEngineEventHandler() {
        override fun onJoinChannelSuccess(channel: String, uid: Int, elapsed: Int) {
            //when local user joined the channel
            runOnUiThread {
                Toast.makeText(
                    this@UserActivity,
                    "Joined channel $channel",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        override fun onRemoteVideoStateChanged(uid: Int, state: Int, reason: Int, elapsed: Int) {
            super.onRemoteVideoStateChanged(uid, state, reason, elapsed)
            //when remote user join the channel
            if (state == Constants.REMOTE_VIDEO_STATE_STARTING) {
                runOnUiThread { addRemoteRender(uid) }
            }
        }

        override fun onUserOffline(uid: Int, reason: Int) {
            //when remote user leave the channel
            runOnUiThread { removeRemoteRender() }
        }

        override fun onStreamMessage(uid: Int, streamId: Int, data: ByteArray) {
            //when received the remote user's stream message data
            super.onStreamMessage(uid, streamId, data)
            val touchCount = data.size / 8 //number of touch points from data array
            for (k in 0 until touchCount) {
                //get the touch point's x,y position related to the center of the screen and calculated the raw position
                val xByte = ByteArray(4)
                val yByte = ByteArray(4)
                for (i in 0..3) {
                    xByte[i] = data[i + 8 * k]
                    yByte[i] = data[i + 8 * k + 4]
                }
                val convertedX = ByteBuffer.wrap(xByte).float
                val convertedY = ByteBuffer.wrap(yByte).float
                val center_X = convertedX + mWidth.toFloat() / 2
                val center_Y = convertedY + mHeight.toFloat() / 2

                //simulate the clicks based on the touch position got from the data array
                instrumentation.sendPointerSync(
                    MotionEvent.obtain(
                        SystemClock.uptimeMillis(),
                        SystemClock.uptimeMillis(),
                        MotionEvent.ACTION_DOWN,
                        center_X,
                        center_Y,
                        0
                    )
                )
                instrumentation.sendPointerSync(
                    MotionEvent.obtain(
                        SystemClock.uptimeMillis(),
                        SystemClock.uptimeMillis(),
                        MotionEvent.ACTION_UP,
                        center_X,
                        center_Y,
                        0
                    )
                )
            }
        }
    }

    private val permissions = arrayOf(
        Manifest.permission.WRITE_EXTERNAL_STORAGE,
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.CAMERA
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user)


        //Getting values that were passed by MainActivity
        token = intent.getStringExtra("EXTRA_TOKEN").toString()
        channelName = intent.getStringExtra("EXTRA_CHANNEL").toString()
        checkAndInitRtc()

        //get device screen size
        mWidth = this.resources.displayMetrics.widthPixels
        mHeight = this.resources.displayMetrics.heightPixels
        mRemoteContainer = findViewById(R.id.videoView)


        //Exit using leave button
        val leaveButton :Button= findViewById(R.id.buttonLeaveUser)
        leaveButton.setOnClickListener{
            finish()
        }
    }

    //onResume is called when the app resumes after being paused
    override fun onResume() {
        super.onResume()

        // ARCore requires camera permissions to operate. If we did not yet obtain runtime
        // permission on Android M and above, now is a good time to ask the user for it.

        // ARCore requires camera permissions to operate. If we did not yet obtain runtime
        // permission on Android M and above, now is a good time to ask the user for it.
        if (!CameraPermissionHelper.hasCameraPermission(this)) {
            CameraPermissionHelper.requestCameraPermission(this)
            return
        }
        // Note that order matters - see the note in onPause(), the reverse applies here.
    }

    /*override fun onPause() {
        super.onPause()
        // Note that the order matters - GLSurfaceView is paused first so that it does not try
        // to query the session. If Session is paused before GLSurfaceView, GLSurfaceView may
        // still call mSession.update() and get a SessionPausedException.
        mDisplayRotationHelper.onPause()
        mSurfaceView.onPause()
        if (mSession != null) {
            mSession.pause()
        }
    }*/

    override fun onDestroy() {
        super.onDestroy()
        removeRemoteRender()
        mSenderHandler?.looper?.quit()
        mRtcEngine?.leaveChannel()
        RtcEngine.destroy()
    }

    private fun checkAndInitRtc() {
        if (checkSelfPermissions()) {
            initRtcEngine()
        }
    }

    private fun initRtcEngine() {
        try {
            mRtcEngine = RtcEngine.create(
                this,
                getString(R.string.app_ID),
                mRtcEventHandler
            )
            mRtcEngine?.setParameters("{\"rtc.log_filter\": 65535}")
            mRtcEngine?.setChannelProfile(Constants.CHANNEL_PROFILE_LIVE_BROADCASTING)
            mRtcEngine?.enableDualStreamMode(true)
            mRtcEngine?.setVideoEncoderConfiguration(
                VideoEncoderConfiguration(
                    VideoEncoderConfiguration.VD_640x480,
                    VideoEncoderConfiguration.FRAME_RATE.FRAME_RATE_FPS_30,
                    VideoEncoderConfiguration.STANDARD_BITRATE,
                    VideoEncoderConfiguration.ORIENTATION_MODE.ORIENTATION_MODE_ADAPTIVE
                )
            )
            mRtcEngine?.setClientRole(Constants.CLIENT_ROLE_BROADCASTER)
            mRtcEngine?.enableVideo()
            mSource = AgoraVideoSource()
            mRender = AgoraVideoRender(0, true)
            mRtcEngine?.setVideoSource(mSource)
            mRtcEngine?.setLocalVideoRenderer(mRender)
            mRtcEngine?.joinChannel(
                "0069d4ea58d2f1a44b3935e9e8a998cbe90IADCplMmU+4c/eoizbpoKxen/4mqztzaYwitw6cRBw3Z2zLRTXgAAAAAEAAUEHcdN92xYQEAAQA13bFh",
                "Test",
                "",
                0
            )
        } catch (ex: Exception) {
            Toast.makeText(this, "Exception: $ex", Toast.LENGTH_SHORT).show()
        }
        val thread = HandlerThread("ArSendThread")
        thread.start()
        mSenderHandler = Handler(thread.looper)
    }

    private fun removeRemoteRender() {
        if (mRemoteView != null) {
            mRemoteContainer?.removeView(mRemoteView)
        }
        mRemoteView = null
    }

    private fun addRemoteRender(uid: Int) {
        mRemoteView = RtcEngine.CreateRendererView(baseContext)
        mRemoteContainer?.addView(mRemoteView)
        val remoteVideoCanvas = VideoCanvas(mRemoteView, VideoCanvas.RENDER_MODE_HIDDEN, uid)
        mRtcEngine?.setupRemoteVideo(remoteVideoCanvas)
    }

    private fun checkSelfPermissions(): Boolean {
        val needList: MutableList<String> = ArrayList()
        for (perm in permissions) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    perm
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                needList.add(perm)
            }
        }
        if (needList.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                needList.toTypedArray(),
                PERMISSION_REQUEST_CODE
            )
            return false
        }
        return true
    }

    //Creating ArCore Session
    /*private fun createSession() {
        // Create a new ARCore session.
        mSession = Session(this)

        // Create a session config.
        val config = Config(mSession)

        // Do feature-specific operations here, such as enabling depth or turning on
        // support for Augmented Faces.

        // Configure the session.
        mSession.configure(config)
    }*/

    //onDestroy method is automatically called when the activity is terminated
    /*override fun onDestroy(){
        super.onDestroy()
        mSession.close()
    }*/

    // Verify that ARCore is installed and using the current version.

    /*private fun sendARViewMessage() {
        val outBitmap = Bitmap.createBitmap(
            mSurfaceView.getWidth(),
            mSurfaceView.getHeight(),
            Bitmap.Config.ARGB_8888
        )
        PixelCopy.request(AR, outBitmap,
            OnPixelCopyFinishedListener { copyResult ->
                if (copyResult == PixelCopy.SUCCESS) {
                    sendARView(outBitmap)
                } else {
                    Toast.makeText(
                        this@AgoraARStreamerActivity,
                        "Pixel Copy Failed",
                        Toast.LENGTH_SHORT
                    )
                }
            }, mSenderHandler
        )
    }

    private fun sendARView(bitmap: Bitmap?) {
        if (bitmap == null) return
        if (mSource!!.consumer == null) return

        //Bitmap bitmap = source.copy(Bitmap.Config.ARGB_8888,true);
        val width = bitmap.width
        val height = bitmap.height
        val size = bitmap.rowBytes * bitmap.height
        val byteBuffer = ByteBuffer.allocate(size)
        bitmap.copyPixelsToBuffer(byteBuffer)
        val data = byteBuffer.array()
        mSource!!.consumer.consumeByteArrayFrame(
            data,
            MediaIO.PixelFormat.RGBA.intValue(),
            width,
            height,
            0,
            System.currentTimeMillis()
        )
    }*/
}