/*
 * Copyright 2018 Google Inc. All Rights Reserved.
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
package com.example.ar

import android.Manifest
import android.app.Activity
import android.app.ActivityManager
import android.app.Instrumentation
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.*
import android.util.Log
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.ar.activity.MessageActivity
import com.example.ar.rtmtutorial.AGApplication
import com.example.ar.rtmtutorial.ChatManager
import com.example.ar.utils.MessageUtil
import com.google.ar.core.*
import com.google.ar.sceneform.*
import com.google.ar.sceneform.collision.Ray
import com.google.ar.sceneform.math.Vector3
import com.google.ar.sceneform.rendering.Color
import com.google.ar.sceneform.rendering.Material
import com.google.ar.sceneform.rendering.MaterialFactory
import com.google.ar.sceneform.rendering.Texture
import com.google.ar.sceneform.ux.ArFragment
import io.agora.rtc.Constants
import io.agora.rtc.IRtcEngineEventHandler
import io.agora.rtc.RtcEngine
import io.agora.rtc.mediaio.MediaIO
import io.agora.rtc.video.VideoCanvas
import io.agora.rtc.video.VideoEncoderConfiguration
import io.agora.rtm.ErrorInfo
import io.agora.rtm.ResultCallback
import io.agora.rtm.RtmClient
import java.nio.ByteBuffer
import java.util.*
import java.util.concurrent.CompletionException


/** Implements an AR drawing experience using Sceneform.  */
class DrawingActivity : AppCompatActivity(), Scene.OnUpdateListener, Scene.OnPeekTouchListener {
    private var fragment: ArFragment? = null
    private var anchorNode: AnchorNode? = null
    private val strokes = ArrayList<Stroke>()
    private var material: Material? = null
    private var currentStroke: Stroke? = null
    private var colorPanel: LinearLayout? = null
    private var controlPanel: LinearLayout? = null

    private val TAG: String = DrawingActivity::class.java.simpleName
    private val PERMISSION_REQUEST_CODE = 0X0001
    private var mRemoteView: SurfaceView? = null
    private var mRemoteContainer: RelativeLayout? = null
    private var mRtcEngine: RtcEngine? = null
    private var mSource: AgoraVideoSource? = null
    private var mRender: AgoraVideoRender? = null
    private var mSenderHandler: Handler? = null
    private val instrumentation = Instrumentation()
    private var installRequested = false
    private var mWidth = 0
    private var mHeight: Int = 0

    private lateinit var token: String
    private lateinit var channelName: String

    //RTM
    private var messageButton: Button? = null
    private var mRtmClient: RtmClient? = null
    private var mChatManager: ChatManager? = null
    private var mIsInChat = false
    private val mUserId: String? = "User"
    private val mIsPeerToPeerMode = true // whether peer to peer mode or channel mode\
    private val mTargetName: String? = "Expert"
    private val CHAT_REQUEST_CODE = 1

    private val mRtcEventHandler: IRtcEngineEventHandler = object : IRtcEngineEventHandler() {
        override fun onJoinChannelSuccess(channel: String, uid: Int, elapsed: Int) {
            //when local user joined the channel
            runOnUiThread {
                Toast.makeText(
                    this@DrawingActivity,
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
            //Log.d("StreamTAGYES","DataReceived")
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
                val center_X = convertedX //+ mWidth.toFloat() / 2
                val center_Y = convertedY //+ mHeight.toFloat() / 2

                //simulate the clicks based on the touch position got from the data array
                if (k == 0) {
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
                } else {
                    instrumentation.sendPointerSync(
                        MotionEvent.obtain(
                            SystemClock.uptimeMillis(),
                            SystemClock.uptimeMillis(),
                            MotionEvent.ACTION_MOVE,
                            center_X,
                            center_Y,
                            0
                        )
                    )
                }
            }
        }
    }

    private val permissions = arrayOf(
        Manifest.permission.WRITE_EXTERNAL_STORAGE,
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.CAMERA,
        Manifest.permission.FOREGROUND_SERVICE,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!checkIsSupportedDeviceOrFinish(this)) {
            return
        }
        setContentView(R.layout.activity_drawing)
        //get device screen size
        mWidth = this.resources.displayMetrics.widthPixels
        mHeight = this.resources.displayMetrics.heightPixels

        //Getting values that were passed by MainActivity
        token = intent.getStringExtra("EXTRA_TOKEN").toString()
        channelName = intent.getStringExtra("EXTRA_CHANNEL").toString()

        mRemoteContainer = findViewById(R.id.remote_video_view_container)

        colorPanel = findViewById(R.id.colorPanel)
        controlPanel = findViewById(R.id.controlsPanel)
        MaterialFactory.makeOpaqueWithColor(this, WHITE)
            .thenAccept { material1: Material -> material = material1.makeCopy() }
            .exceptionally { throwable: Throwable ->
                displayError(throwable)
                throw CompletionException(throwable)
            }
        fragment = supportFragmentManager.findFragmentById(R.id.sceneform_fragment) as ArFragment
        fragment!!.arSceneView.planeRenderer.isEnabled = false
        fragment!!.arSceneView.scene.addOnUpdateListener(this)
        fragment!!.arSceneView.scene.addOnPeekTouchListener(this)

        /*fragment!!.arSceneView.scene.setOnTouchListener(object : Scene.OnTouchListener {
            override fun onSceneTouch(p0: HitTestResult?, tap: MotionEvent?): Boolean {
                val action: Int = tap!!.action
                val camera: com.google.ar.sceneform.Camera? = fragment?.arSceneView?.scene?.camera
                val ray: Ray? = camera?.screenPointToRay(tap.x, tap.y)
                val drawPoint: Vector3? = ray?.getPoint(DRAW_DISTANCE)
                if (action == MotionEvent.ACTION_DOWN) {
                    if (anchorNode == null) {
                        val arSceneView: ArSceneView? = fragment?.arSceneView
                        val coreCamera: com.google.ar.core.Camera? = arSceneView?.arFrame?.camera
                        if (coreCamera != null) {
                            if (coreCamera.trackingState != TrackingState.TRACKING) {
                                return true
                            }
                        }
                        val pose: Pose? = coreCamera?.pose
                        if (arSceneView != null) {
                            anchorNode = AnchorNode(arSceneView.session?.createAnchor(pose))
                        }
                        if (arSceneView != null) {
                            anchorNode!!.setParent(arSceneView.scene)
                        }
                    }
                    currentStroke = Stroke(anchorNode!!, material)
                    strokes.add(currentStroke!!)
                    currentStroke!!.add(drawPoint)
                } else if (action == MotionEvent.ACTION_MOVE && currentStroke != null) {
                    currentStroke!!.add(drawPoint)
                }
                return true
            }
        })*/

        val clearButton = findViewById<ImageView>(R.id.clearButton)
        clearButton.setOnClickListener {
            for (stroke in strokes) {
                stroke.clear()
            }
            strokes.clear()
        }
        val undoButton = findViewById<ImageView>(R.id.undoButton)
        undoButton.setOnClickListener(
            View.OnClickListener {
                if (strokes.size < 1) {
                    return@OnClickListener
                }
                val lastIndex = strokes.size - 1
                strokes[lastIndex].clear()
                strokes.removeAt(lastIndex)
            })
        setUpColorPickerUi()
        installRequested = false
        checkAndInitRtc()

        mChatManager = AGApplication.the().chatManager
        mRtmClient = mChatManager!!.rtmClient
        messageButton = findViewById(R.id.textButtonUser)
        messageButton!!.setOnClickListener {
            doLogin()
            if (mIsInChat)
                jumpToMessageActivity()
        }
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        return super.onTouchEvent(event)
    }

    override fun onDestroy() {
        super.onDestroy()
        removeRemoteRender()
        mSenderHandler!!.looper!!.quit()
        mRtcEngine!!.leaveChannel()
        RtcEngine.destroy()
    }

    private fun checkAndInitRtc() {
        if (checkSelfPermissions()) {
            initRtcEngine()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            var deniedCount = 0
            for (i in grantResults.indices) {
                if (grantResults[i] == PackageManager.PERMISSION_DENIED) {
                    deniedCount++
                }
            }
            if (deniedCount == 0) {
                initRtcEngine()
            } else {
                finish()
            }
        }
    }

    private fun initRtcEngine() {
        try {
            mRtcEngine = RtcEngine.create(
                this,
                getString(R.string.app_ID),
                mRtcEventHandler
            )
            mRtcEngine!!.setParameters("{\"rtc.log_filter\": 65535}")
            mRtcEngine!!.setChannelProfile(Constants.CHANNEL_PROFILE_LIVE_BROADCASTING)
            mRtcEngine!!.enableDualStreamMode(true)
            mRtcEngine!!.setVideoEncoderConfiguration(
                VideoEncoderConfiguration(
                    VideoEncoderConfiguration.VD_640x480,
                    VideoEncoderConfiguration.FRAME_RATE.FRAME_RATE_FPS_30,
                    VideoEncoderConfiguration.STANDARD_BITRATE,
                    VideoEncoderConfiguration.ORIENTATION_MODE.ORIENTATION_MODE_ADAPTIVE
                )
            )
            mRtcEngine!!.setClientRole(Constants.CLIENT_ROLE_BROADCASTER)
            mRtcEngine!!.enableVideo()
            mSource = AgoraVideoSource()
            mRender = AgoraVideoRender(0, true)
            mRtcEngine!!.setVideoSource(mSource)
            mRtcEngine!!.setLocalVideoRenderer(mRender)
            mRtcEngine!!.joinChannel(null, "channel", "", 0)
        } catch (ex: Exception) {
            Toast.makeText(this, "Exception: $ex", Toast.LENGTH_SHORT).show()
        }
        val thread = HandlerThread("ArSendThread")
        thread.start()
        mSenderHandler = Handler(thread.looper)
    }

    private fun removeRemoteRender() {
        if (mRemoteView != null) {
            mRemoteContainer!!.removeView(mRemoteView)
        }
        mRemoteView = null
    }

    private fun addRemoteRender(uid: Int) {
        mRemoteView = RtcEngine.CreateRendererView(baseContext)
        mRemoteContainer!!.addView(mRemoteView)
        val remoteVideoCanvas = VideoCanvas(mRemoteView, VideoCanvas.RENDER_MODE_HIDDEN, uid)
        mRtcEngine!!.setupRemoteVideo(remoteVideoCanvas)
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

    private fun sendARViewMessage() {
        val outBitmap = Bitmap.createBitmap(
            fragment!!.arSceneView.width,
            fragment!!.arSceneView.height,
            Bitmap.Config.ARGB_8888
        )

        PixelCopy.request(fragment!!.arSceneView, outBitmap, { copyResult: Int ->
            if (copyResult == PixelCopy.SUCCESS) {
                sendARView(outBitmap)
            } else {
                Toast.makeText(
                    this@DrawingActivity,
                    "Pixel Copy Failed",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }, mSenderHandler!!)
    }

    private fun sendARView(bitmap: Bitmap?) {
        if (bitmap == null) return
        if (mSource!!.getConsumer() == null) return

        //Bitmap bitmap = source.copy(Bitmap.Config.ARGB_8888,true);
        val width = bitmap.width
        val height = bitmap.height
        val size = bitmap.rowBytes * bitmap.height
        val byteBuffer = ByteBuffer.allocate(size)
        bitmap.copyPixelsToBuffer(byteBuffer)
        val data = byteBuffer.array()
        mSource!!.getConsumer()!!.consumeByteArrayFrame(
            data,
            MediaIO.PixelFormat.RGBA.intValue(),
            width,
            height,
            0,
            System.currentTimeMillis()
        )
    }

    private fun setUpColorPickerUi() {
        val colorPickerIcon = findViewById<ImageView>(R.id.colorPickerIcon)
        colorPanel?.visibility = View.GONE
        colorPickerIcon.setOnClickListener {
            if (controlPanel?.visibility ?: Boolean == View.VISIBLE) {
                controlPanel?.visibility = View.GONE
                colorPanel?.visibility = View.VISIBLE
            }
        }
        val whiteCircle = findViewById<ImageView>(R.id.whiteCircle)
        whiteCircle.setOnClickListener {
            setColor(WHITE)
            colorPickerIcon.setImageResource(R.drawable.ic_selected_white)
        }
        val redCircle = findViewById<ImageView>(R.id.redCircle)
        redCircle.setOnClickListener {
            setColor(RED)
            colorPickerIcon.setImageResource(R.drawable.ic_selected_red)
        }
        val greenCircle = findViewById<ImageView>(R.id.greenCircle)
        greenCircle.setOnClickListener {
            setColor(GREEN)
            colorPickerIcon.setImageResource(R.drawable.ic_selected_green)
        }
        val blueCircle = findViewById<ImageView>(R.id.blueCircle)
        blueCircle.setOnClickListener {
            setColor(BLUE)
            colorPickerIcon.setImageResource(R.drawable.ic_selected_blue)
        }
        val blackCircle = findViewById<ImageView>(R.id.blackCircle)
        blackCircle.setOnClickListener {
            setColor(BLACK)
            colorPickerIcon.setImageResource(R.drawable.ic_selected_black)
        }
        val rainbowCircle = findViewById<ImageView>(R.id.rainbowCircle)
        rainbowCircle.setOnClickListener {
            setTexture(R.drawable.rainbow_texture)
            colorPickerIcon.setImageResource(R.drawable.ic_selected_rainbow)
        }
    }

    private fun setTexture(resourceId: Int) {
        Texture.builder()
            .setSource(fragment?.context, resourceId)
            .setSampler(
                Texture.Sampler.builder().setWrapMode(Texture.Sampler.WrapMode.REPEAT).build()
            )
            .build()
            .thenCompose { texture: Texture? ->
                MaterialFactory.makeOpaqueWithTexture(
                    fragment?.context,
                    texture
                )
            }
            .thenAccept { material1: Material -> material = material1.makeCopy() }
            .exceptionally { throwable: Throwable ->
                displayError(throwable)
                throw CompletionException(throwable)
            }
        colorPanel?.visibility = View.GONE
        controlPanel?.visibility = View.VISIBLE
    }

    private fun setColor(color: Color) {
        MaterialFactory.makeOpaqueWithColor(fragment?.context, color)
            .thenAccept { material1: Material -> material = material1.makeCopy() }
            .exceptionally { throwable: Throwable ->
                displayError(throwable)
                throw CompletionException(throwable)
            }
        colorPanel?.visibility = View.GONE
        controlPanel?.visibility = View.VISIBLE
    }

    override fun onPeekTouch(hitTestResult: HitTestResult, tap: MotionEvent) {
        val action: Int = tap.action
        val camera: com.google.ar.sceneform.Camera? = fragment?.arSceneView?.scene?.camera
        val ray: Ray? = camera?.screenPointToRay(tap.x, tap.y)
        val drawPoint: Vector3? = ray?.getPoint(DRAW_DISTANCE)
        if (action == MotionEvent.ACTION_DOWN) {
            if (anchorNode == null) {
                val arSceneView: ArSceneView? = fragment?.arSceneView
                val coreCamera: com.google.ar.core.Camera? = arSceneView?.arFrame?.camera
                if (coreCamera != null) {
                    if (coreCamera.trackingState != TrackingState.TRACKING) {
                        return
                    }
                }
                val pose: Pose? = coreCamera?.pose
                if (arSceneView != null) {
                    anchorNode = AnchorNode(arSceneView.session?.createAnchor(pose))
                }
                if (arSceneView != null) {
                    anchorNode!!.setParent(arSceneView.scene)
                }
            }
            currentStroke = Stroke(anchorNode!!, material)
            strokes.add(currentStroke!!)
            currentStroke!!.add(drawPoint)
        } else if (action == MotionEvent.ACTION_MOVE && currentStroke != null) {
            currentStroke!!.add(drawPoint)
        }
    }

    override fun onUpdate(frameTime: FrameTime) {
        val camera: com.google.ar.core.Camera? =
            fragment?.arSceneView?.arFrame?.camera
        if (camera != null) {
            if (camera.trackingState == TrackingState.TRACKING) {
                fragment?.planeDiscoveryController?.hide()
            }
        }
        if (fragment!!.arSceneView.height > 0 && fragment!!.arSceneView.width > 0)
            sendARViewMessage()
    }

    private fun displayError(throwable: Throwable) {
        Log.e(TAG, "Unable to create material", throwable)
        val toast: Toast = Toast.makeText(this, "Unable to create material", Toast.LENGTH_LONG)
        toast.setGravity(Gravity.CENTER, 0, 0)
        toast.show()
    }

    companion object {
        private val TAG = DrawingActivity::class.java.simpleName
        private const val MIN_OPENGL_VERSION = 3.0
        private const val DRAW_DISTANCE = 0.13f
        private val WHITE = Color(android.graphics.Color.WHITE)
        private val RED = Color(android.graphics.Color.RED)
        private val GREEN = Color(android.graphics.Color.GREEN)
        private val BLUE = Color(android.graphics.Color.BLUE)
        private val BLACK = Color(android.graphics.Color.BLACK)

        /**
         * Returns false and displays an error message if Sceneform can not run, true if Sceneform can run
         * on this device.
         *
         *
         * Sceneform requires Android N on the device as well as OpenGL 3.0 capabilities.
         *
         *
         * Finishes the activity if Sceneform can not run
         */

        fun checkIsSupportedDeviceOrFinish(activity: Activity): Boolean {
            val openGlVersionString: String =
                (activity.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager)
                    .deviceConfigurationInfo
                    .glEsVersion
            if (openGlVersionString.toDouble() < MIN_OPENGL_VERSION) {
                Log.e(TAG, "Sceneform requires OpenGL ES 3.0 later")
                Toast.makeText(
                    activity,
                    "Sceneform requires OpenGL ES 3.0 or later",
                    Toast.LENGTH_LONG
                )
                    .show()
                activity.finish()
                return false
            }
            return true
        }
    }

    /**
     * RTM Code Starts
     */
    private fun doLogin() {
        mIsInChat = true
        mRtmClient!!!!.login(null, mUserId, object : ResultCallback<Void?> {
            override fun onSuccess(responseInfo: Void?) {
                Log.i(TAG, "login success")
                mChatManager!!.enableOfflineMessage(true)
            }

            override fun onFailure(errorInfo: ErrorInfo) {
                Log.i(TAG, "login failed: " + errorInfo.errorCode)
                mIsInChat = false
            }
        })
    }

    private fun doLogout() {
        mRtmClient!!.logout(null)
        MessageUtil.cleanMessageListBeanList()
    }

    fun showToast(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    }

    override fun onResume() {
        super.onResume()
        if (mIsInChat) {
            doLogout()
        }
    }

    private fun jumpToMessageActivity() {
        val intent = Intent(this, MessageActivity::class.java)
        intent.putExtra(MessageUtil.INTENT_EXTRA_IS_PEER_MODE, mIsPeerToPeerMode)
        intent.putExtra(MessageUtil.INTENT_EXTRA_TARGET_NAME, mTargetName)
        intent.putExtra(MessageUtil.INTENT_EXTRA_USER_ID, mUserId)
        startActivityForResult(intent, CHAT_REQUEST_CODE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == CHAT_REQUEST_CODE) {
            if (resultCode == MessageUtil.ACTIVITY_RESULT_CONN_ABORTED) {
                finish()
            }
        }
    }

    override fun onBackPressed() {
        Intent(this, MainActivity::class.java).also {
            startActivity(it)
        }
        super.onBackPressed()
    }
}