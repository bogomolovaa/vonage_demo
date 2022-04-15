package com.tokbox.sample.basicvideochat

import android.Manifest
import android.content.res.Resources
import android.net.Uri
import android.opengl.GLSurfaceView
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.database.ExoDatabaseProvider
import com.google.android.exoplayer2.source.BaseMediaSource
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.source.hls.HlsMediaSource
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.ui.PlayerView
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.google.android.exoplayer2.upstream.cache.CacheDataSource
import com.google.android.exoplayer2.upstream.cache.LeastRecentlyUsedCacheEvictor
import com.google.android.exoplayer2.upstream.cache.SimpleCache
import com.google.android.exoplayer2.util.MimeTypes
import com.google.android.exoplayer2.util.Util
import com.google.android.flexbox.FlexboxLayout
import com.opentok.android.BaseVideoRenderer
import com.opentok.android.OpentokError
import com.opentok.android.Publisher
import com.opentok.android.PublisherKit
import com.opentok.android.PublisherKit.PublisherListener
import com.opentok.android.Session
import com.opentok.android.Session.SessionListener
import com.opentok.android.Stream
import com.opentok.android.Subscriber
import com.opentok.android.SubscriberKit
import com.opentok.android.SubscriberKit.SubscriberListener
import com.tokbox.sample.basicvideochat.network.APIService
import com.tokbox.sample.basicvideochat.network.GetSessionResponse
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import pub.devrel.easypermissions.AfterPermissionGranted
import pub.devrel.easypermissions.EasyPermissions
import pub.devrel.easypermissions.EasyPermissions.PermissionCallbacks
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

class MainActivity : AppCompatActivity(), PermissionCallbacks {
    private var retrofit: Retrofit? = null
    private var apiService: APIService? = null
    private var session: Session? = null
    private var publisher: Publisher? = null
    private var subscribers = mutableMapOf<String, View>()
    private lateinit var subscriberViewContainer: FlexboxLayout
    private var cameraBtn: ImageView? = null
    private var microphoneBtn: ImageView? = null
    private var myMutedIcon: ImageView? = null
    private var callBtn: ImageView? = null
    private var progress: View? = null
    private var playerView: PlayerView? = null
    private var cameraEnabled = true
    private var microphoneEnabled = true
    private var connected = true
    private var exoPlayer: ExoPlayer? = null
    private val publisherListener: PublisherListener = object : PublisherListener {
        override fun onStreamCreated(publisherKit: PublisherKit, stream: Stream) {
            Log.d(TAG, "onStreamCreated: Publisher Stream Created. Own stream ${stream.streamId}")
        }

        override fun onStreamDestroyed(publisherKit: PublisherKit, stream: Stream) {
            Log.d(TAG, "onStreamDestroyed: Publisher Stream Destroyed. Own stream ${stream.streamId}")
        }

        override fun onError(publisherKit: PublisherKit, opentokError: OpentokError) {
            finishWithMessage("PublisherKit onError: ${opentokError.message}")
        }
    }
    private val sessionListener: SessionListener = object : SessionListener {
        override fun onConnected(session: Session) {
            Log.d(TAG, "onConnected: Connected to session: ${session.sessionId}")
            publisher = Publisher.Builder(this@MainActivity).build()
            publisher?.setPublisherListener(publisherListener)
            publisher?.renderer?.setStyle(BaseVideoRenderer.STYLE_VIDEO_SCALE, BaseVideoRenderer.STYLE_VIDEO_FILL)
            publisher?.view?.let {
                myMutedIcon = addVideoView(it).second
                myMutedIcon?.visibility = if (microphoneEnabled) View.INVISIBLE else View.VISIBLE
            }
            if (publisher?.view is GLSurfaceView) {
                //(publisher?.view as GLSurfaceView).setZOrderOnTop(true)
            }
            publisher?.publishVideo = cameraEnabled
            publisher?.publishAudio = microphoneEnabled
            session.publish(publisher)
            restoreControls()
        }

        override fun onDisconnected(session: Session) {
            Log.d(TAG, "onDisconnected: Disconnected from session: ${session.sessionId}")
            restoreControls()
        }

        override fun onStreamReceived(session: Session, stream: Stream) {
            Log.d(TAG, "onStreamReceived: New Stream Received ${stream.streamId} in session: ${session.sessionId}")
            if (subscribers.containsKey(stream.streamId)) return
            val subscriber = Subscriber.Builder(this@MainActivity, stream).build().also {
                it.renderer?.setStyle(
                        BaseVideoRenderer.STYLE_VIDEO_SCALE,
                        BaseVideoRenderer.STYLE_VIDEO_FILL
                )

                it.setSubscriberListener(subscriberListener)
            }
            session.subscribe(subscriber)
            subscriber?.view?.let {
                val pair = addVideoView(it)
                val container = pair.first
                val mutedIcon = pair.second
                val audioBuffer = AudioBuffer(10)
                subscriber.setAudioLevelListener { subscriberKit, value ->
                    Log.d(TAG, "TEST audio $value")
                    audioBuffer.add(value)
                    if (audioBuffer.size > 3) mutedIcon.visibility = if (audioBuffer.getSum() > 0) View.INVISIBLE else View.VISIBLE
                }
                subscribers.put(stream.streamId, container)
            }
        }

        override fun onStreamDropped(session: Session, stream: Stream) {
            Log.d(TAG, "onStreamDropped: Stream Dropped: ${stream.streamId} in session: ${session.sessionId}")
            subscribers[stream.streamId]?.let {
                subscribers.remove(stream.streamId)
                subscriberViewContainer.removeView(it)
            }
        }

        override fun onError(session: Session, opentokError: OpentokError) {
            finishWithMessage("Session error: ${opentokError.message}")
        }
    }
    var subscriberListener: SubscriberListener = object : SubscriberListener {
        override fun onConnected(subscriberKit: SubscriberKit) {
            Log.d(TAG, "onConnected: Subscriber connected. Stream: ${subscriberKit.stream.streamId}")
        }

        override fun onDisconnected(subscriberKit: SubscriberKit) {
            Log.d(TAG, "onDisconnected: Subscriber disconnected. Stream: ${subscriberKit.stream.streamId}")
        }

        override fun onError(subscriberKit: SubscriberKit, opentokError: OpentokError) {
            finishWithMessage("SubscriberKit onError: ${opentokError.message}")
        }
    }

    private fun addVideoView(view: View): Pair<View, ImageView> {
        val container = FrameLayout(this)
        subscriberViewContainer.addView(container)
        container.updateLayoutParams<ViewGroup.LayoutParams> {
            width = subscriberViewContainer.width / 3
            height = subscriberViewContainer.height / 3
        }
        container.addView(view)
        return Pair(
                container,
                ImageView(this).apply {
                    visibility = View.INVISIBLE
                    setImageResource(R.drawable.muted)
                    container.addView(this)
                    updateLayoutParams<FrameLayout.LayoutParams> {
                        width = 24f.dp
                        height = 24f.dp
                        gravity = Gravity.BOTTOM or Gravity.END
                        z = 1.0f
                    }
                }
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        subscriberViewContainer = findViewById(R.id.subscriber_container)
        cameraBtn = findViewById(R.id.camera)
        microphoneBtn = findViewById(R.id.microphone)
        callBtn = findViewById(R.id.call)
        progress = findViewById(R.id.progress)
        playerView = findViewById(R.id.playerView)
        requestPermissions()
        cameraBtn?.setOnClickListener {
            if (!connected) return@setOnClickListener
            if (cameraEnabled) {
                cameraEnabled = false
                cameraBtn?.setImageResource(R.drawable.no_video)
            } else {
                cameraEnabled = true
                cameraBtn?.setImageResource(R.drawable.video)
            }
            publisher?.publishVideo = cameraEnabled
        }
        microphoneBtn?.setOnClickListener {
            if (!connected) return@setOnClickListener
            if (microphoneEnabled) {
                microphoneEnabled = false
                microphoneBtn?.setImageResource(R.drawable.no_sound)
            } else {
                microphoneEnabled = true
                microphoneBtn?.setImageResource(R.drawable.sound)
            }
            myMutedIcon?.visibility = if (microphoneEnabled) View.INVISIBLE else View.VISIBLE
            publisher?.publishAudio = microphoneEnabled
        }
        callBtn?.setOnClickListener {
            disableControls()
            if (connected) {
                connected = false
                callBtn?.setImageResource(R.drawable.call_connect)
                disconnect()
            } else {
                connected = true
                callBtn?.setImageResource(R.drawable.call_disconnect)
                connect()
            }
        }
        disableControls()
        initializePlayer()
    }

    private fun disableControls() {
        progress?.visibility = View.VISIBLE
        cameraBtn?.alpha = 0.5f
        microphoneBtn?.alpha = 0.5f
        callBtn?.alpha = 0.5f
    }

    private fun restoreControls() {
        cameraBtn?.alpha = 1.0f
        microphoneBtn?.alpha = 1.0f
        callBtn?.alpha = 1.0f
        progress?.visibility = View.GONE
    }

    private fun connect() {
        requestPermissions()
    }

    private fun disconnect() {
        session?.disconnect()
        subscribers.clear()
        subscriberViewContainer.removeAllViews()
    }

    override fun onStart() {
        super.onStart()
        if (Build.VERSION.SDK_INT > 23) {
            initializePlayer()
            playerView?.onResume()
        }
    }

    override fun onResume() {
        super.onResume()
        session?.onResume()
        if (Build.VERSION.SDK_INT <= 23 || exoPlayer == null) {
            initializePlayer()
            playerView?.onResume()
        }
    }

    override fun onPause() {
        super.onPause()
        session?.onPause()
        if (Build.VERSION.SDK_INT <= 23) {
            playerView?.onPause()
            releasePlayer()
        }
    }

    override fun onStop() {
        super.onStop()
        if (Build.VERSION.SDK_INT > 23) {
            playerView?.onPause()
            releasePlayer()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this)
    }

    override fun onPermissionsGranted(requestCode: Int, perms: List<String>) {
        Log.d(TAG, "onPermissionsGranted:$requestCode: $perms")
    }

    override fun onPermissionsDenied(requestCode: Int, perms: List<String>) {
        finishWithMessage("onPermissionsDenied: $requestCode: $perms")
    }

    @AfterPermissionGranted(PERMISSIONS_REQUEST_CODE)
    private fun requestPermissions() {
        val perms = arrayOf(Manifest.permission.INTERNET, Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
        if (EasyPermissions.hasPermissions(this, *perms)) {
            if (ServerConfig.hasChatServerUrl()) {
                // Custom server URL exists - retrieve session config
                if (!ServerConfig.isValid) {
                    finishWithMessage("Invalid chat server url: ${ServerConfig.CHAT_SERVER_URL}")
                    return
                }
                initRetrofit()
                getSession()
            } else {
                // Use hardcoded session config
                if (!OpenTokConfig.isValid) {
                    finishWithMessage("Invalid OpenTokConfig. ${OpenTokConfig.description}")
                    return
                }
                initializeSession(OpenTokConfig.API_KEY, OpenTokConfig.SESSION_ID, OpenTokConfig.TOKEN)
            }
        } else {
            EasyPermissions.requestPermissions(
                    this,
                    getString(R.string.rationale_video_app),
                    PERMISSIONS_REQUEST_CODE,
                    *perms
            )
        }
    }

    /* Make a request for session data */
    private fun getSession() {
        Log.i(TAG, "getSession")

        apiService?.session?.enqueue(object : Callback<GetSessionResponse?> {
            override fun onResponse(call: Call<GetSessionResponse?>, response: Response<GetSessionResponse?>) {
                response.body()?.also {
                    initializeSession(it.apiKey, it.sessionId, it.token)
                }
            }

            override fun onFailure(call: Call<GetSessionResponse?>, t: Throwable) {
                throw RuntimeException(t.message)
            }
        })
    }

    private fun initializeSession(apiKey: String, sessionId: String, token: String) {
        Log.i(TAG, "apiKey: $apiKey")
        Log.i(TAG, "sessionId: $sessionId")
        Log.i(TAG, "token: $token")

        /*
        The context used depends on the specific use case, but usually, it is desired for the session to
        live outside of the Activity e.g: live between activities. For a production applications,
        it's convenient to use Application context instead of Activity context.
         */
        session = Session.Builder(this, apiKey, sessionId).build().also {
            it.setSessionListener(sessionListener)
            it.connect(token)
        }
    }

    private fun initRetrofit() {
        val logging = HttpLoggingInterceptor()
        logging.setLevel(HttpLoggingInterceptor.Level.BODY)
        val client: OkHttpClient = OkHttpClient.Builder()
                .addInterceptor(logging)
                .build()

        retrofit = Retrofit.Builder()
                .baseUrl(ServerConfig.CHAT_SERVER_URL)
                .addConverterFactory(MoshiConverterFactory.create())
                .client(client)
                .build().also {
                    apiService = it.create(APIService::class.java)
                }
    }

    private fun finishWithMessage(message: String) {
        Log.e(TAG, message)
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        finish()
    }

    private fun initializePlayer() {
        val trackSelector = DefaultTrackSelector(this)
        trackSelector.setParameters(trackSelector.buildUponParameters().setMaxVideoSizeSd())
        exoPlayer = SimpleExoPlayer.Builder(this)
                .setBandwidthMeter(bandwidthMeter)
                .setTrackSelector(trackSelector)
                .setLoadControl(DefaultLoadControl())
                .build().apply {
                    repeatMode = Player.REPEAT_MODE_ALL
                    playWhenReady = true
                    setForegroundMode(true)
                    val mediaSource = buildMediaSource(STREAM_URL)
                    setMediaSource(mediaSource, true)
                    volume = 0f
                    playWhenReady = true
                    playerView?.player = this
                    prepare()
                    play()
                    println("TEST play")
                }
    }

    private fun releasePlayer() {
        exoPlayer?.release()
        exoPlayer = null
    }

    private fun buildMediaSource(streamUrl: String): BaseMediaSource {
        val builder = mediaItemBuilder(streamUrl)
        return when {
            isHlsStream(streamUrl) -> {
                HlsMediaSource.Factory(defaultDataSourceFactory()).createMediaSource(builder.build())
            }
            else -> {
                ProgressiveMediaSource.Factory(
                        CacheDataSource.Factory().setCache(cache)
                                .setUpstreamDataSourceFactory(defaultDataSourceFactory())
                                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
                ).createMediaSource(builder.build())
            }
        }
    }

    private val cache: SimpleCache by lazy {
        SimpleCache(
                cacheDir,
                LeastRecentlyUsedCacheEvictor(100L * 1024 * 1024),
                ExoDatabaseProvider(this)
        )
    }

    private val bandwidthMeter by lazy { DefaultBandwidthMeter.Builder(this).build() }

    private val agent by lazy { Util.getUserAgent(this, "user_agent") }

    private fun defaultDataSourceFactory(): DefaultDataSourceFactory =
            DefaultDataSourceFactory(this, agent, bandwidthMeter)

    private fun mediaItemBuilder(streamUrl: String) = MediaItem.Builder().setUri(Uri.parse(streamUrl)).apply {
        when {
            isHlsStream(streamUrl) -> setMimeType(MimeTypes.APPLICATION_M3U8)
            else -> setMimeType(MimeTypes.APPLICATION_MP4)
        }
    }

    private fun isHlsStream(streamUrl: String): Boolean = streamUrl.endsWith(".m3u8")

    companion object {
        private val TAG = MainActivity::class.java.simpleName
        private const val PERMISSIONS_REQUEST_CODE = 124
        private val STREAM_URL = "https://vod-progressive.akamaized.net/exp=1650027561~acl=%2Fvimeo-prod-skyfire-std-us%2F01%2F3417%2F4%2F117086260%2F424092932.mp4~hmac=82d64bb94cdf49a787f7db1fb452226389098dac838e5e7da171991d16713120/vimeo-prod-skyfire-std-us/01/3417/4/117086260/424092932.mp4"
    }
}

inline fun <reified T : ViewGroup.LayoutParams> View.updateLayoutParams(block: T.() -> Unit) {
    val params = layoutParams as T
    block(params)
    layoutParams = params
}

internal val Float.dp: Int
    get() = (this * Resources.getSystem().displayMetrics.density).toInt()