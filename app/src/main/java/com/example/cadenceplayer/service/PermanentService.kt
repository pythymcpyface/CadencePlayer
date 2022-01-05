package com.example.cadenceplayer.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.hardware.*
import android.location.Location
import android.location.LocationListener
import android.os.*
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationManagerCompat
import com.example.cadenceplayer.MainActivity
import com.example.cadenceplayer.CadencePlayer
import com.example.cadenceplayer.R
import com.example.cadenceplayer.broadcast.OnReceiveMetadata
import com.example.cadenceplayer.broadcast.OnReceivePlayback
import com.example.cadenceplayer.fft.Bpm
import com.example.cadenceplayer.model.Acceleration
import com.example.cadenceplayer.model.TrackFeatures
import com.example.cadenceplayer.repositories.BpmRepository
import com.example.cadenceplayer.repositories.SpotifyRepository
import com.example.cadenceplayer.room.BpmDatabase
import com.example.cadenceplayer.room.BpmEntry
import com.example.cadenceplayer.spotify.SpotifyAppRemoteApi
import com.example.cadenceplayer.spotify.toTrack
import com.google.android.gms.maps.model.LatLng
import com.spotify.protocol.types.PlayerState
import kotlinx.coroutines.*
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import java.net.SocketTimeoutException
import java.sql.Date
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow

@RequiresApi(Build.VERSION_CODES.KITKAT)
class PermanentService : Service(), SensorEventListener, LocationListener {

    private var wakeLock: PowerManager.WakeLock? = null
    private var isServiceStarted = false
    private lateinit var sensorManager: SensorManager
    var sensorListenerRegistered = false
    private var accelerations = mutableListOf<Acceleration>()
    private lateinit var initTimestamp: Date
    private var sampleRate: Int = 256
    private val activityName = this.javaClass.simpleName
    private lateinit var spotifyRepository: SpotifyRepository
    private lateinit var bpmRepository: BpmRepository
    private lateinit var connectionStatus: SpotifyAppRemoteApi.ConnectionStatus
    private lateinit var location: LatLng
    private lateinit var userId: String
    private var currentTrack: TrackFeatures? = null
    private var songChanged = false
    private var tracks: List<TrackFeatures?>? = null
    private var restartServiceIntent = Intent(this, PermanentService::class.java).also {
        it.setPackage(packageName)
        it.action = Actions.START.name
    }
    private var previousPlayerState: PlayerState? = null
    private var bpm: Float = 100F
    private var filteredRandomTrack: TrackFeatures? = null

    override fun onBind(intent: Intent?): IBinder? {
        Log.i("StateChangeMyPermServ", "$activityName bound")
        return null
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i("StateChangeMyPermServ", "$activityName command started")

        restartServiceIntent = intent!!

        GlobalScope.launch(Dispatchers.IO) {

            val bundle: Bundle? = intent.getParcelableExtra("bundle")
            location = bpmRepository.getLastKnownLocation(this@PermanentService) ?: bundle?.getParcelable("lastLocation") ?: LatLng(0.0, 0.0)

            val playlistUri: String? = bundle?.getString("playlist")

            val application = (application as CadencePlayer)

            tracks = application.getMyData()

            userId = try {
                with(bundle?.getString("userId").toString()) {
                    if(this == "") {
                        runBlocking {
                            return@runBlocking withContext(Dispatchers.IO) {
                                spotifyRepository.getUserId()
                            }
                        }
                    } else {
                        this
                    }
                }
            } catch (e: SocketTimeoutException) {
                ""
            }
            val action = intent.action
            Log.i("StateChangeMyPermServ", "$activityName intent action is $action, userId = $userId, playlist = $playlistUri, location = $location, currentTrack = ${currentTrack?.playable}, tracks = $tracks")
            when (action) {
                Actions.START.name -> startService()
                Actions.STOP.name -> stopService()
            }
        }
        // by returning this we make sure the service is restarted if the system kills the service
        return START_STICKY
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate() {
        Log.i("StateChangeMyPermServ", "$activityName created")
        super.onCreate()
        initTimestamp = Date(System.currentTimeMillis())

        val foregroundNotification = createForegroundNotification()

        startForeground(1, foregroundNotification)

        spotifyRepository = SpotifyRepository(application)

        spotifyRepository.connectPlayer(this)

        EventBus.getDefault().register(this)

        sampleRate = applicationContext.resources.getInteger(R.integer.sample_rate)

        val bpmEntryDao =
            BpmDatabase.getDatabase(applicationContext, GlobalScope).bpmEntryDao()
        bpmRepository = BpmRepository(bpmEntryDao, sampleRate)

    }

    override fun onDestroy() {
        Log.i("StateChangeMyPermServ", "$activityName destroyed")
        spotifyRepository.disconnectPlayer()
        EventBus.getDefault().unregister(this)
        restartService()
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent) {
        restartService()
    }

    @DelicateCoroutinesApi
    @RequiresApi(Build.VERSION_CODES.O)
    private fun startService() {
        Log.i("StateChangeMyPermServ", "starting service ${!isServiceStarted}")
        if (isServiceStarted) return
        isServiceStarted = true
        ServiceTracker().setServiceState(this, ServiceState.STARTED)

        sensorManager = this.getSystemService(Context.SENSOR_SERVICE) as SensorManager

        val accSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val sensitivity = SensorManager.SENSOR_DELAY_UI
        registerSensorListener(sensorManager, accSensor, sensitivity)

        GlobalScope.launch(Dispatchers.Main) {
            observePlayerState()
            observeConnectionStatus()
            bpmRepository.requestLocationUpdates(this@PermanentService, this@PermanentService)
        }

        // we're starting a loop in a coroutine
        GlobalScope.launch(Dispatchers.IO) {
            Log.i("StateChangeMyPermServ", "Coroutine running")

            while (isServiceStarted) {

                try {

                    if (!accelerations.isNullOrEmpty() && accelerations.size > sampleRate) {
                        Log.i("StateChangeMyPermServ", "Accelerations filled $accelerations")

                        with(calculateBpm(accelerations)) {
                            if (this != null) {
                                bpm = this
                            }
                        }

                        location =
                            bpmRepository.getLastKnownLocation(this@PermanentService) ?: LatLng(0.0,
                                0.0)

                        if (currentTrack == null) {
                            spotifyRepository.connectPlayer(this@PermanentService)
                            filteredRandomTrack?.playable?.uri?.let {
                                spotifyRepository.playPlayableItem(it)
                            }
                        }

                        Log.i("StateChangeMyPermServ",
                            "UserId = $userId, location = $location, currentTrack = ${currentTrack?.playable?.asTrack?.name}, tempo = ${currentTrack?.audioFeatures?.tempo}, tracks = $tracks")

                        val updateNotification = createUpdateNotification(bpm, currentTrack)

//                        val bpmEntry = BpmEntry(
//                            userId = userId,
//                            timestamp = Date(System.currentTimeMillis()),
//                            bpm = bpm,
//                            latitude = location.latitude,
//                            longitude = location.longitude,
//                            trackTitle = currentTrack?.playable?.asTrack?.name ?: "",
//                            trackArtist = currentTrack?.playable?.asTrack?.artists?.joinToString(
//                                ", ") ?: "",
//                            trackId = currentTrack?.playable?.id ?: "",
//                            trackTempo = currentTrack?.audioFeatures?.tempo ?: 0F
//                        )

//                        bpmRepository.insert(bpmEntry)

                        with(NotificationManagerCompat.from(applicationContext)) {
                            // notificationId is a unique int for each notification that you must define
                            notify(2, updateNotification)
                        }

//                        stopService()
                        accelerations.clear()
//                        delay(1 * 60 * 1000)
                    }
                } catch (e: Exception) {
                    Log.i("StateChangeMyPermServ", "error in bpm $e")
//                    stopService()
                    accelerations.clear()
                }
            }
        }
    }

    private fun stopService() {
        Log.i("StateChangeMyPermServ", "Stopping service")
        try {
            wakeLock?.let {
                if (it.isHeld) {
                    it.release()
                }
            }
            unregisterSensorListener(sensorManager)
            spotifyRepository.disconnectPlayer()
            stopForeground(true)
            stopSelf()
        } catch (e: Exception) {
        }
        isServiceStarted = false
        ServiceTracker().setServiceState(this, ServiceState.STOPPED)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createForegroundNotification(): Notification {
        val notificationChannelId = "PERMANENT SERVICE CHANNEL"

        // depending on the Android API that we're dealing with we will have
        // to use a specific method to create the notification
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                notificationChannelId,
                "Permanent Service notifications channel",
                NotificationManager.IMPORTANCE_LOW
            ).let {
                it.description = "Permanent Service channel"
                it.enableLights(true)
                it.lightColor = Color.RED
                it.enableVibration(true)
                it.vibrationPattern = longArrayOf(
                    100, 100
//                    100, 200, 300, 400, 500, 400, 300, 200, 400
                )
                it
            }
            notificationManager.createNotificationChannel(channel)
        }

        val pendingIntent: PendingIntent =
            Intent(this, MainActivity::class.java).let { notificationIntent ->
                PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)
            }

        val builder: Notification.Builder =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Notification.Builder(
                this,
                notificationChannelId
            ) else Notification.Builder(this, notificationChannelId)

        return builder
            .setContentTitle("Permanent Service")
            .setContentText("This is your favorite permanent service working")
            .setContentIntent(pendingIntent)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setTicker("Ticker text")
            .build()
    }

    // Register sensor listener
    private fun registerSensorListener(
        sensorManager: SensorManager,
        accSensor: Sensor,
        sensitivity: Int,
    ) {
        if (sensorListenerRegistered) {
            return
        } else {
            if (sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null) {
                sensorManager.registerListener(this, accSensor, sensitivity)
            }

            sensorListenerRegistered = true
        }
    }

    // Unregister sensor listener
    private fun unregisterSensorListener(sensorManager: SensorManager) {
        if (!sensorListenerRegistered) {
            return
        } else {
            sensorManager.unregisterListener(this)

            sensorListenerRegistered = false
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        // Read acceleration values from sensor
        val accX = event?.values?.get(0)
        val accY = event?.values?.get(1)
        val accZ = event?.values?.get(2)

        try {
            accelerations.add(
                Acceleration(
                    Date(System.currentTimeMillis()).time.minus(initTimestamp.time).toInt(),
                    accX,
                    accY,
                    accZ
                )
            )
        } catch (e: Exception) {
            Log.i("StateChangeMyPermServ", "Error in acc sensor $e")
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        return
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createUpdateNotification(
        bpm: Float,
        currentTrack: TrackFeatures?,
    ): Notification {
        Log.i("StateChangeMyPermServ", "accelerations size = ${accelerations.size}")
        val notificationChannelId = "UPDATE CHANNEL"

        val simpleDateFormat = SimpleDateFormat("HH:mm", Locale.ENGLISH)
        val timeString = simpleDateFormat.format(System.currentTimeMillis())

        // depending on the Android API that we're dealing with we will have
        // to use a specific method to create the notification
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                notificationChannelId,
                "Update notifications channel",
                NotificationManager.IMPORTANCE_LOW
            ).let {
                it.description = "Update channel"
                it.enableLights(true)
                it.lightColor = Color.RED
                it.enableVibration(true)
                it.vibrationPattern = longArrayOf(
                    100, 100, 100
//                    , 200, 300, 400, 500, 400, 300, 200, 400
                )
                it
            }
            notificationManager.createNotificationChannel(channel)
        }

        val pendingIntent: PendingIntent =
            Intent(this, MainActivity::class.java).let { notificationIntent ->
                PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)
            }

        val builder: Notification.Builder =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Notification.Builder(
                this,
                notificationChannelId
            ) else Notification.Builder(this, notificationChannelId)

//        connectionStatus = spotifyRepository.getPlayerConnectionState().value.toString()

//        if (spotifyRepository.getPlayerConnectionState().value == SpotifyAppRemoteApi.ConnectionStatus.DISCONNECTED) {
//            spotifyRepository.connectPlayer(this@PermanentService)
//        }

        val currentTrackArtists = if (currentTrack != null) {
            currentTrack.playable.asTrack?.artists?.joinToString(", ") { simpleArtist ->
                simpleArtist.name
            }
        } else {
            ""
        }

        val currentTrackName = currentTrack?.playable?.asTrack?.name ?: ""
        val currentTrackTempo = currentTrack?.audioFeatures?.tempo ?: 0F

        return builder
            .setContentTitle("Update")
            .setContentText("Spotify Remote $connectionStatus")
            .setContentIntent(pendingIntent)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setStyle(Notification.BigTextStyle()
                .bigText("Bpm at $timeString was $bpm. Song is " +
                        "$currentTrackName by " +
                        "$currentTrackArtists with tempo " +
                        "of ${currentTrackTempo}bpm. " +
                        "Location is $location. User is $userId"))
            .setTicker("Ticker text")
            .build()
    }

    private fun calculateBpm(accelerations: List<Acceleration>): Float? {
        val accelerationsSize = accelerations.size

        var accelerationsTrimmed = accelerations

        val power = ln(accelerationsSize.toDouble()) / ln(2.0)

        Log.i("StateChangeMyPermServ",
            "power = $power, size = $accelerationsSize, accelerationsTrimmed = $accelerationsTrimmed")

        if (accelerationsSize != (1 shl (power).toInt())) {
            accelerationsTrimmed =
                accelerations.subList(0, 2.0.pow(floor((power))).toInt()).toMutableList()
            Log.i("StateChangeMyPermServ",
                "wasn't 2^n, rounded down = ${
                    2.0.pow(floor(power)).toInt()
                }, accelerationsTrimmed = $accelerationsTrimmed")
        }

        var currentBpm: Float? = null

        try {
            currentBpm = Bpm(300, 20).calculateBpm(accelerations)
        } catch (e: ConcurrentModificationException) {
            Log.i("StateChangeMyPermServ", "error in bpm $e")

        }
        Log.i("StateChangeMyPermServ", "currentBpm calculated as $currentBpm")

        return currentBpm
    }

    override fun onLocationChanged(location: Location) {
        Log.i("StateChangeMyPermServ", "Location changed, $location")
        this@PermanentService.location = LatLng(location.latitude, location.longitude)
    }

    private fun restartService() {

        isServiceStarted = false
        ServiceTracker().setServiceState(this, ServiceState.STOPPED)

        if (restartServiceIntent.action == Actions.START.name) {
            Log.i("StateChangeMyPermServ", "$activityName service restarting")
            val restartServicePendingIntent: PendingIntent =
                PendingIntent.getService(this, 1, restartServiceIntent, PendingIntent.FLAG_IMMUTABLE)
            val alarmService: AlarmManager =
                this.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmService.set(AlarmManager.ELAPSED_REALTIME,
                SystemClock.elapsedRealtime() + 1000,
                restartServicePendingIntent)
        } else {
            Log.i("StateChangeMyPermServ", "$activityName service not restarting")
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    @Subscribe
    fun onMetadataReceived(metadata: OnReceiveMetadata) {

        Log.i("StateChangeMyPermServ", "metadata received in service at ${metadata.timeSentInMs}, songChange = $songChanged")

//        if (!songChanged) {
//
//            val bpm = bpmRepository.allBpmEntries.value?.first()?.bpm ?: 100F
//            val filteredRandomTrack = spotifyRepository.getFilteredRandomTrack(this, tracks, bpm)
//
//            Log.i("StateChangeMyPermServ", "bpm = $bpm, filteredTrack = ${filteredRandomTrack?.playable?.asTrack?.name}")
//
//            if (filteredRandomTrack != null) {
//                spotifyRepository.playPlayableItem(filteredRandomTrack.playable.uri)
//
//                val bpmDatabase: BpmDatabase = BpmDatabase.getDatabase(this@MainActivity, GlobalScope)
//                val sampleRate = this@MainActivity.resources.getInteger(R.integer.sample_rate)
//                val bpmRepository = BpmRepository(bpmDatabase.bpmEntryDao(), sampleRate)
//                val location = bpmRepository.getLastKnownLocation(this@MainActivity) ?: LatLng(0.0, 0.0)
//
//                bpmViewModel.insert(
//                    BpmEntry(
//                        userId = spotifyViewModel.getUserId(),
//                        timestamp = Date(System.currentTimeMillis()),
//                        bpm = bpm,
//                        latitude = location.latitude,
//                        longitude = location.longitude,
//                        trackTitle = filteredRandomTrack.playable.asTrack?.name ?: "",
//                        trackArtist = filteredRandomTrack.playable.asTrack?.artists?.joinToString(
//                            ", ") ?: "",
//                        trackId = filteredRandomTrack.playable.id ?: "",
//                        trackTempo = filteredRandomTrack.audioFeatures.tempo
//                    )
//                )
//            } else {
//                spotifyRepository.playPlayableItem(spotifyViewModel.recentTracks.last())
//            }
//        }

//        songChanged = !songChanged

    }

    @Subscribe
    fun onPlaybackReceived(playback: OnReceivePlayback) {

        Log.i("SpotifyBroadcast", "playback received in service at $playback")
    }

    private fun observePlayerState() {
        spotifyRepository.getPlayerState().observeForever { playerState ->
            GlobalScope.launch(Dispatchers.IO) {
                withContext(Dispatchers.IO) {

                    if (playerState != previousPlayerState) {
                        Log.i("StateChangeMyPermServ", "PlayerChanged, $playerState")
                        previousPlayerState = playerState

                        if (playerState.track != null) {
                            currentTrack = runBlocking {
                                return@runBlocking withContext(Dispatchers.IO) {
                                    spotifyRepository.getTrackFeatures(playerState.track.toTrack())

                                }
                            }
                        }

                        if (!playerState.isPaused && playerState.playbackPosition < 500L) {
                            Log.i("StateChangeMyPermServ", "PlayerChanged, new song $playerState")
                            val filteredRandomTrack =
                                spotifyRepository.getFilteredRandomTrack(this@PermanentService, tracks, bpm)

                            songChanged = if (!songChanged && filteredRandomTrack != null) {
                                Log.i("StateChangeMyPermServ",
                                    "PlayerChanged, changing song with tempo of ${filteredRandomTrack.audioFeatures.tempo} to match bpm of $bpm: ${filteredRandomTrack.playable}, playerState = $playerState")
                                spotifyRepository.playPlayableItem(filteredRandomTrack.playable.uri)

                                val bpmEntry = BpmEntry(
                                    userId = userId,
                                    timestamp = Date(System.currentTimeMillis()),
                                    bpm = bpm,
                                    latitude = location.latitude,
                                    longitude = location.longitude,
                                    trackTitle = filteredRandomTrack.playable.asTrack?.name ?: "",
                                    trackArtist = filteredRandomTrack.playable.asTrack?.artists?.joinToString(
                                        ", ") ?: "",
                                    trackId = filteredRandomTrack.playable.id ?: "",
                                    trackTempo = filteredRandomTrack.audioFeatures.tempo
                                )

                                bpmRepository.insert(bpmEntry)

                                true
                            } else {
                                delay(2000L)
                                false
                            }
                        }
                    }
                }
            }
        }
    }

    private fun observeConnectionStatus() {
        spotifyRepository.getPlayerConnectionState().observeForever { connectionStatus ->
            this.connectionStatus = connectionStatus
        }
    }

    private fun listenToChanges() {
//        spotifyRepository.subscribeToPlayerChanges { playerState ->
//            if (playerState != previousPlayerState) {
//                Log.i("StateChangeMyPermServ", "PlayerChanged, $playerState")
//                previousPlayerState = playerState
//
//                val audioFeatures = playerState.track.getAudioFeatures()
//
//                Log.i("StateChangeMyPermServ", "PlayerChanged, $audioFeatures")
//
//                val trackFeatures = runBlocking {
//                    Log.i("StateChangeMyPermServ", "PlayerChanged, getting track in runblocking")
//                    return@runBlocking withContext(Dispatchers.IO) {
//                        TrackFeatures(
//                            playerState.track.toSimpleTrack().toFullTrack()!!.asTrack!!,
//                            audioFeatures
//                        )
//                    }
//                }
//
//                currentTrack = runBlocking {
//                    Log.i("StateChangeMyPermServ", "PlayerChanged, getting track in runblocking")
//                    return@runBlocking withContext(Dispatchers.IO) {
//                        Log.i("StateChangeMyPermServ", "PlayerChanged, getting track in coroutine ${spotifyRepository.getTrackFeatures(playerState.track.toSimpleTrack())}")
//                        spotifyRepository.getTrackFeatures(playerState.track.toSimpleTrack())
//                    }
//                }
//
////                if (playerState.track != null) {
////                    val bpmDatabase: BpmDatabase = BpmDatabase.getDatabase(this, GlobalScope)
////                    val sampleRate = this.resources.getInteger(R.integer.sample_rate)
////                    val bpmRepository = BpmRepository(bpmDatabase.bpmEntryDao(), sampleRate)
////                    val playbackPosition = playerState.playbackPosition
////                    val isPaused = playerState.isPaused
////                    val playbackSpeed = playerState.playbackSpeed
//////                    val tracks = spotifyViewModel.tracks
//////                    val recentTracks = spotifyViewModel.recentTracks
//////                    if (recentTracks.size >= tracks.size) {
//////                        spotifyViewModel.recentTracks = mutableListOf()
//////                    }
//////                    val bpm = bpmRepository.allBpmEntries.value?.first()?.bpm ?: 100F
//                    val filteredRandomTrack = spotifyRepository.getFilteredRandomTrack(this, tracks, bpm)
//                    this.filteredRandomTrack = filteredRandomTrack
//////                    newTrack = filteredRandomTrack?.playable?.uri?.id.toString()
////                    Log.i("StateChangeMyPermServ", "track pos = $playbackPosition, paused = $isPaused, speed = $playbackSpeed, currenttrack = ${playerState.track.uri}, filteredtrack = ${filteredRandomTrack?.playable?.uri?.id}, songChanged = $songChanged")
////
////                    if (filteredRandomTrack != null) {
////                        Log.i("StateChangeMyPermServ", "filteredTrack is not null ${filteredRandomTrack.playable.asTrack?.name}")
////                    }
//////                    if (playFlag) {
//////                        Log.i("StateChangeMyPermServ", "playFlag is true $playFlag")
//////                    }
////                    if (songChanged) {
////                        Log.i("StateChangeMyPermServ", "songChanged is true $songChanged")
////                    }
////                    if (playbackPosition == 0L && !isPaused && playbackSpeed == 0F) {
////                        Log.i("StateChangeMyPermServ", "track pos is 0 = ($playbackPosition), is not paused = ($isPaused), speed is 0 = ($playbackSpeed))")
////                    }
////                    if (playbackPosition <= 100L && !isPaused && playbackSpeed == 1F) {
////                        Log.i("StateChangeMyPermServ", "track pos < 100 = ($playbackPosition), is not paused = ($isPaused), speed is 1 = ($playbackSpeed))")
////                    }
////
////                    if (filteredRandomTrack != null
////                        && ((playbackPosition == 0L && !isPaused && playbackSpeed == 0F))
////                        && !songChanged
////                    ) {
////                        Log.i("StateChangeMyPermServ", "playing a new song ${filteredRandomTrack.playable.asTrack?.name} with tempo of ${filteredRandomTrack.audioFeatures.tempo} to match bpm $bpm because filtered track isn't null (${filteredRandomTrack != null}), track isn't paused ($isPaused), posn is < 100 and speed is 1 (${(playbackPosition <= 100L && !isPaused && playbackSpeed == 1F)}), songChanged equals false ($songChanged)")
////                        spotifyRepository.playPlayableItem(filteredRandomTrack.playable.uri)
//////                        spotifyViewModel.recentTracks.add(filteredRandomTrack.playable.uri)
////
////                        GlobalScope.launch(Dispatchers.IO) {
//                            bpmRepository.insert(
//                                BpmEntry(
//                                    userId = userId,
//                                    timestamp = Date(System.currentTimeMillis()),
//                                    bpm = bpm,
//                                    latitude = location.latitude,
//                                    longitude = location.longitude,
//                                    trackTitle = filteredRandomTrack.playable.asTrack?.name ?: "",
//                                    trackArtist = filteredRandomTrack.playable.asTrack?.artists?.joinToString(
//                                        ", ") ?: "",
//                                    trackId = filteredRandomTrack.playable.id ?: "",
//                                    trackTempo = filteredRandomTrack.audioFeatures.tempo
//                                )
//                            )
////                        }
////                    } else if (filteredRandomTrack != null
////                        && ((playbackPosition == 0L && !isPaused && playbackSpeed == 0F))
////                    )
////                        {
////                        songChanged = !songChanged
////                    }
////                }
//                    }
//            }
        }
}