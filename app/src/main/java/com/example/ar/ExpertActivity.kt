package com.example.ar

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaScannerConnection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.MotionEvent
import android.view.SurfaceView
import android.widget.*
import androidx.annotation.DrawableRes
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.ar.activity.MessageActivity
import com.example.ar.rtmtutorial.AGApplication
import com.example.ar.rtmtutorial.ChatManager
import com.example.ar.utils.MessageUtil
import com.hbisoft.hbrecorder.Constants.MAX_FILE_SIZE_REACHED_ERROR
import com.hbisoft.hbrecorder.Constants.SETTINGS_ERROR
import com.hbisoft.hbrecorder.HBRecorder
import com.hbisoft.hbrecorder.HBRecorderListener
import io.agora.rtc.Constants
import io.agora.rtc.IRtcEngineEventHandler
import io.agora.rtc.RtcEngine
import io.agora.rtc.models.DataStreamConfig
import io.agora.rtc.video.VideoCanvas
import io.agora.rtc.video.VideoEncoderConfiguration
import io.agora.rtm.ErrorInfo
import io.agora.rtm.ResultCallback
import io.agora.rtm.RtmClient
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList

class ExpertActivity : AppCompatActivity(), HBRecorderListener {
    private val PERMISSION_REQ_ID = 22

    private var mRtcEngine: RtcEngine? = null
    private var mRemoteContainer: RelativeLayout? = null
    private var mRemoteView: SurfaceView? = null
    private var mLocalContainer: FrameLayout? = null
    private var mLocalView: SurfaceView? = null
    private var mDataStream = DataStreamConfig()
    private lateinit var token: String
    private lateinit var channelName: String

    var dataChannel = 0
    var mWidth = 0
    var mHeight: Int = 0

    //Sending Touch input
    private var touchCount = 0
    private val floatList = ArrayList<Float>()

    //RTM
    private var messageButton: Button? = null
    private val TAG: String? = ExpertActivity::class.java.simpleName
    private var mRtmClient: RtmClient? = null
    private var mChatManager: ChatManager? = null
    private var mIsInChat = false
    private val mUserId: String = "Expert"
    private val mIsPeerToPeerMode = true // whether peer to peer mode or channel mode\
    private val mTargetName: String = "User"
    private val CHAT_REQUEST_CODE = 1

    //HBRecorder
    var mRecordButton: Button? = null
    var hbRecorder: HBRecorder? = null
    private val SCREEN_RECORD_REQUEST_CODE = 777
    private val PERMISSION_REQ_ID_RECORD_AUDIO = 22
    private val PERMISSION_REQ_ID_WRITE_EXTERNAL_STORAGE = PERMISSION_REQ_ID_RECORD_AUDIO + 1
    private var hasPermissions = false
    var wasHDSelected = false
    var isAudioEnabled = true
    var resolver: ContentResolver? = null
    var contentValues: ContentValues? = null
    var mUri: Uri? = null

    private val mRtcEngineEventHandler: IRtcEngineEventHandler = object : IRtcEngineEventHandler() {
        override fun onJoinChannelSuccess(channel: String, uid: Int, elapsed: Int) {
            //when local user joined the channel
            super.onJoinChannelSuccess(channel, uid, elapsed)
            runOnUiThread {
                Toast.makeText(this@ExpertActivity, "", Toast.LENGTH_SHORT).show()
            }
        }

        override fun onRemoteVideoStateChanged(uid: Int, state: Int, reason: Int, elapsed: Int) {
            //when remote user join the channel
            super.onRemoteVideoStateChanged(uid, state, reason, elapsed)
            if (state == Constants.REMOTE_VIDEO_STATE_STARTING) {
                runOnUiThread { setUpRemoteView(uid) }
            }
        }

        override fun onUserOffline(uid: Int, reason: Int) {
            //when remote user leave the channel
            super.onUserOffline(uid, reason)
            runOnUiThread { removeRemoteView() }
        }
    }

    // Ask for Android device permissions at runtime.
    private val REQUESTED_PERMISSIONS = arrayOf(
        Manifest.permission.WRITE_EXTERNAL_STORAGE,
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.CAMERA,
        Manifest.permission.FOREGROUND_SERVICE,
    )

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_expert)
        initUI()

        //Getting values that were passed by MainActivity
        token = intent.getStringExtra("EXTRA_TOKEN").toString()
        channelName = intent.getStringExtra("EXTRA_CHANNEL").toString()
        mWidth = this.resources.displayMetrics.widthPixels
        mHeight = this.resources.displayMetrics.heightPixels
        if (checkSelfPermission(REQUESTED_PERMISSIONS[0], PERMISSION_REQ_ID) &&
            checkSelfPermission(REQUESTED_PERMISSIONS[1], PERMISSION_REQ_ID)
        ) {
            initEngineAndJoinChannel()
        }
        /**
         * RTM
         */
        mChatManager = AGApplication.the().chatManager
        mRtmClient = mChatManager!!.rtmClient
        messageButton = findViewById(R.id.textButtonExpert)
        messageButton!!.setOnClickListener {
            doLogin()
            if (mIsInChat)
                jumpToMessageActivity()
        }
        /**
         * Recorder
         */
        mRecordButton = findViewById(R.id.recordButton)
        hbRecorder = HBRecorder(this, this)
        mRecordButton!!.setOnClickListener {
            //first check if permissions was granted
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                if (checkSelfPermission(
                        Manifest.permission.RECORD_AUDIO,
                        PERMISSION_REQ_ID_RECORD_AUDIO
                    )
                ) {
                    hasPermissions = true
                }
            } else {
                if (checkSelfPermission(
                        Manifest.permission.RECORD_AUDIO,
                        PERMISSION_REQ_ID_RECORD_AUDIO
                    ) && checkSelfPermission(
                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        PERMISSION_REQ_ID_WRITE_EXTERNAL_STORAGE
                    )
                ) {
                    hasPermissions = true
                }
            }
            if (hasPermissions) {
                //check if recording is in progress
                //and stop it if it is
                if (hbRecorder!!.isBusyRecording) {
                    hbRecorder!!.stopScreenRecording()
                } else {
                    startRecordingScreen()
                }
            }
        }
         mRemoteContainer!!.setOnTouchListener { _, event ->
             when (event.action) {
                 MotionEvent.ACTION_MOVE, MotionEvent.ACTION_DOWN -> {
                     //get the touch position related to the center of the screen
                     touchCount++
                     val x = event.x
                     val y = event.y
                     floatList.add(x)
                     floatList.add(y)
                     /*if (touchCount == 10) {
                         //send the touch positions when collected 10 touch points
                         sendMessage(touchCount, floatList)
                         touchCount = 0
                         floatList.clear()
                     }*/
                 }
                 MotionEvent.ACTION_UP -> {
                     //send touch positions after the touch motion
                     sendMessage(touchCount, floatList)
                     touchCount = 0
                     floatList.clear()
                 }
             }
             true
         }

        //Exit using leave button
        val leaveButton: Button = findViewById(R.id.buttonLeaveExpert)
        leaveButton.setOnClickListener {
            finish()
        }
    }

    private fun sendMessage(touchCount: Int, floatList: List<Float>) {
        val motionByteArray = ByteArray(touchCount * 4 * 2)
        for (i in floatList.indices) {
            val curr = ByteBuffer.allocate(4).putFloat(floatList[i]).array()
            for (j in 0..3) {
                motionByteArray[i * 4 + j] = curr[j]
            }
        }
        mRtcEngine!!.sendStreamMessage(dataChannel, motionByteArray)
        //Toast.makeText(this, "data sent", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        removeLocalView()
        removeRemoteView()
        leaveChannel()
        RtcEngine.destroy()
    }

    private fun initUI() {
        mLocalContainer = findViewById(R.id.local_video_view_container)
        mRemoteContainer = findViewById(R.id.remote_video_view_container)
    }

    private fun checkSelfPermission(permission: String, requestCode: Int): Boolean {
        if (ContextCompat.checkSelfPermission(this, permission) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                REQUESTED_PERMISSIONS,
                requestCode
            )
            return false
        }
        return true
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String?>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            PERMISSION_REQ_ID -> {
                if (grantResults[0] != PackageManager.PERMISSION_GRANTED || grantResults[1] != PackageManager.PERMISSION_GRANTED) {
                    return
                }
                initEngineAndJoinChannel()
            }
            PERMISSION_REQ_ID_RECORD_AUDIO ->
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    checkSelfPermission(
                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        PERMISSION_REQ_ID_WRITE_EXTERNAL_STORAGE
                    )
                } else {
                    hasPermissions = false
                    showLongToast("No permission for " + Manifest.permission.RECORD_AUDIO)
                }
            PERMISSION_REQ_ID_WRITE_EXTERNAL_STORAGE ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    hasPermissions = true
                    startRecordingScreen()
                } else {
                    if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                        hasPermissions = true
                        //Permissions was provided
                        //Start screen recording
                        startRecordingScreen()

                    } else {
                        hasPermissions = false
                        showLongToast("No permission for " + Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    }
                }
        }
    }

    private fun initEngineAndJoinChannel() {
        initializeEngine()
        setUpLocalView()
        joinChannel()
    }

    private fun removeRemoteView() {
        if (mRemoteView != null) {
            mRemoteContainer!!.removeView(mRemoteView)
        }
        mRemoteView = null
    }

    private fun setUpRemoteView(uid: Int) {
        mRemoteView = RtcEngine.CreateRendererView(baseContext)
        mRemoteContainer!!.addView(mRemoteView)
        val remoteVideoCanvas = VideoCanvas(mRemoteView, VideoCanvas.RENDER_MODE_HIDDEN, uid)
        mRtcEngine!!.setupRemoteVideo(remoteVideoCanvas)
    }

    private fun initializeEngine() {
        try {
            mRtcEngine = RtcEngine.create(
                baseContext,
                getString(R.string.app_ID),
                mRtcEngineEventHandler
            )
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
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun setUpLocalView() {
        mRtcEngine!!.enableVideo()
        mLocalView = RtcEngine.CreateRendererView(baseContext)
        mLocalContainer!!.addView(mLocalView)
        mLocalView!!.setZOrderMediaOverlay(true)
        val localVideoCanvas = VideoCanvas(mLocalView, VideoCanvas.RENDER_MODE_HIDDEN, 0)
        mRtcEngine!!.setupLocalVideo(localVideoCanvas)
    }

    private fun joinChannel() {
        mRtcEngine!!.joinChannel(null, "channel", "", 0)
        mDataStream.ordered = false
        mDataStream.syncWithAudio = false
        dataChannel = mRtcEngine!!.createDataStream(mDataStream)
    }

    private fun leaveChannel() {
        mRtcEngine!!.leaveChannel()
    }

    private fun removeLocalView() {
        if (mLocalView != null) {
            mLocalContainer!!.removeView(mLocalView)
        }
        mLocalView = null
    }

    /**
     * RTM Code Starts
     */
    private fun doLogin() {
        mIsInChat = true
        mRtmClient!!.login(null, mUserId, object : ResultCallback<Void?> {
            override fun onSuccess(responseInfo: Void?) {
                Log.i(TAG, "login success")
                mChatManager!!.enableOfflineMessage(true)
            }

            override fun onFailure(errorInfo: ErrorInfo) {
                Log.i(TAG, "login failed: " + errorInfo.errorCode)
                runOnUiThread {
                    mIsInChat = false
                    showToast(getString(R.string.login_failed))
                }
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

    /**
     * Recording Code Starts
     */

    override fun HBRecorderOnStart() {
        Log.e("HBRecorder", "HBRecorderOnStart called")
    }

    override fun HBRecorderOnComplete() {
        showLongToast("Saved Successfully")
        //Update gallery depending on SDK Level
        if (hbRecorder!!.wasUriSet()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                updateGalleryUri()
            } else {
                refreshGalleryFile()
            }
        } else {
            refreshGalleryFile()
        }

    }

    override fun HBRecorderOnError(errorCode: Int, reason: String?) {
        // Error 38 happens when
        // - the selected video encoder is not supported
        // - the output format is not supported
        // - if another app is using the microphone

        //It is best to use device default

        if (errorCode == SETTINGS_ERROR) {
            showLongToast(getString(R.string.settings_not_supported_message))
        } else if (errorCode == MAX_FILE_SIZE_REACHED_ERROR) {
            showLongToast(getString(R.string.max_file_size_reached_message))
        } else {
            showLongToast(getString(R.string.general_recording_error_message))
            if (reason != null) {
                Log.e("HBRecorderOnError", reason)
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == CHAT_REQUEST_CODE) {
            if (resultCode == MessageUtil.ACTIVITY_RESULT_CONN_ABORTED) {
                finish()
            }
        }
        if (requestCode == SCREEN_RECORD_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                //Start screen recording
                setOutputPath();
                hbRecorder!!.startScreenRecording(data, resultCode, this)
            }
        }
    }

    private fun createFolder() {
        val f1 = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
            "HBRecorder"
        )
        if (!f1.exists()) {
            if (f1.mkdirs()) {
                Log.i("Folder ", "created")
            }
        }
    }

    private fun refreshGalleryFile() {
        MediaScannerConnection.scanFile(
            this, arrayOf(hbRecorder!!.filePath), null
        ) { path, uri ->
            Log.i("ExternalStorage", "Scanned $path:")
            Log.i("ExternalStorage", "-> uri=$uri")
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    private fun updateGalleryUri() {
        contentValues!!.clear()
        contentValues!!.put(MediaStore.Video.Media.IS_PENDING, 0)
        mUri?.let { contentResolver.update(it, contentValues, null, null) }
    }

    private fun startRecordingScreen() {
        hbRecorder!!.enableCustomSettings()
        customSettings()
        quickSettings()
        val mediaProjectionManager =
            getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val permissionIntent = mediaProjectionManager.createScreenCaptureIntent()
        startActivityForResult(permissionIntent, SCREEN_RECORD_REQUEST_CODE)
    }

    private fun customSettings() {
        hbRecorder!!.setVideoEncoder("H264")
        hbRecorder!!.setVideoFrameRate(30)
        //Output Format
        hbRecorder!!.setOutputFormat("MPEG_4")
    }

    //Get/Set the selected settings
    private fun quickSettings() {
        hbRecorder!!.setAudioBitrate(128000)
        hbRecorder!!.setAudioSamplingRate(44100)
        hbRecorder!!.recordHDVideo(wasHDSelected)
        hbRecorder!!.isAudioEnabled(isAudioEnabled)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P)
            hbRecorder!!.isAudioEnabled(false)
        //Customise Notification
        hbRecorder!!.setNotificationSmallIcon(R.drawable.icon)
        //hbRecorder.setNotificationSmallIconVector(R.drawable.ic_baseline_videocam_24);
        hbRecorder!!.setNotificationTitle(getString(R.string.stop_recording_notification_title))
        hbRecorder!!.setNotificationDescription(getString(R.string.stop_recording_notification_message))
    }


    private fun setOutputPath() {
        val filename = generateFileName()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            resolver = contentResolver
            contentValues = ContentValues()
            contentValues!!.put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/" + "HBRecorder")
            contentValues!!.put(MediaStore.Video.Media.TITLE, filename)
            contentValues!!.put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            contentValues!!.put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            mUri = resolver!!.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues)
            //FILE NAME SHOULD BE THE SAME
            hbRecorder!!.fileName = filename
            hbRecorder!!.setOutputUri(mUri)
        } else {
            createFolder()
            hbRecorder!!.setOutputPath(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
                    .toString() + "/HBRecorder"
            )
        }
    }

    //Generate a timestamp to be used as a file name
    private fun generateFileName(): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.getDefault())
        val curDate = Date(System.currentTimeMillis())
        return formatter.format(curDate).replace(" ", "")
    }

    //Show Toast
    private fun showLongToast(msg: String) {
        Toast.makeText(applicationContext, msg, Toast.LENGTH_LONG).show()
    }

    //drawable to byte[]
    private fun drawable2ByteArray(@DrawableRes drawableId: Int): ByteArray? {
        val icon = BitmapFactory.decodeResource(resources, drawableId)
        val stream = ByteArrayOutputStream()
        icon.compress(Bitmap.CompressFormat.PNG, 100, stream)
        return stream.toByteArray()
    }
}