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
import com.example.cadenceplayer.R
import com.example.cadenceplayer.fft.Bpm
import com.example.cadenceplayer.model.Acceleration
import com.example.cadenceplayer.model.TrackFeatures
import com.example.cadenceplayer.repositories.BpmRepository
import com.example.cadenceplayer.repositories.SpotifyRepository
import com.example.cadenceplayer.room.BpmDatabase
import com.example.cadenceplayer.room.BpmEntry
import com.example.cadenceplayer.spotify.*
import com.google.android.gms.maps.model.LatLng
import com.spotify.protocol.types.*
import kotlinx.coroutines.*
import java.sql.Date
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow

@RequiresApi(Build.VERSION_CODES.KITKAT)
class PermanentService2 : Service(), SensorEventListener, LocationListener {

    private var wakeLock: PowerManager.WakeLock? = null
    private var isServiceStarted = false
    private lateinit var sensorManager: SensorManager
    var sensorListenerRegistered = false
    private var accelerations = mutableListOf<Acceleration>()
    private val activityName = this.javaClass.simpleName
    private lateinit var spotifyRepository: SpotifyRepository
    private lateinit var bpmRepository: BpmRepository
    private lateinit var location: LatLng
    private lateinit var initTimestamp: Date
    private var sampleRate: Int = 256
    private var currentTrack: TrackFeatures? = null
    private var tracks: List<TrackFeatures?>? = null
    private var remoteConnected = false
    private var filteredRandomTrack: TrackFeatures? = null

    override fun onBind(intent: Intent?): IBinder? {
        Log.i("StateChangeMyPermServ", "$activityName bound")
        return null
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i("StateChangeMyPermServ", "$activityName command started")

        if (intent != null) {
            GlobalScope.launch(Dispatchers.IO) {
                val bundle: Bundle? = intent.getParcelableExtra("bundle")

                val playlistUri: String? = bundle?.getString("playlist")

                tracks = if(playlistUri != null && playlistUri != "Saved Tracks") {
                    runBlocking {
                        return@runBlocking withContext(Dispatchers.IO) {
                            spotifyRepository.getPlaylistTracks(playlistUri)
                        }
                    }
                } else {
                    spotifyRepository.getSavedTracks()
                }

                val action = intent.action

                Log.i("StateChangeMyPermServ", "$activityName intent action is $action, playlist = $playlistUri, currentTrack = ${currentTrack?.playable?.asTrack?.name}, tracks = $tracks")

                when (action) {
                    Actions.START.name -> startService()
                    Actions.STOP.name -> stopService()
                }
            }
        }
        // by returning this we make sure the service is restarted if the system kills the service
        return START_STICKY
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate() {
        Log.i("StateChangeMyPermServ", "$activityName created")
        super.onCreate()

//        GlobalScope.launch(Dispatchers.Main) {
            spotifyRepository = SpotifyRepository(application)

            initTimestamp = Date(System.currentTimeMillis())

            val foregroundNotification = createForegroundNotification()

            startForeground(1, foregroundNotification)

            connectRemote()

            sampleRate = applicationContext.resources.getInteger(R.integer.sample_rate)

            val bpmEntryDao = BpmDatabase.getDatabase(applicationContext, GlobalScope).bpmEntryDao()

            bpmRepository = BpmRepository(bpmEntryDao, sampleRate)
//        }
    }

    override fun onDestroy() {
        Log.i("StateChangeMyPermServ", "$activityName destroyed")
        disconnectRemote()
        restartService()
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent) {
        Log.i("StateChangeMyPermServ", "$activityName task removed")
        restartService()
    }

    @DelicateCoroutinesApi
    @RequiresApi(Build.VERSION_CODES.O)
    private fun startService() {
        if (isServiceStarted) return
        isServiceStarted = true
        ServiceTracker().setServiceState(this, ServiceState.STARTED)

        sensorManager = this.getSystemService(Context.SENSOR_SERVICE) as SensorManager

        bpmRepository.requestLocationUpdates(this, this)


        val accSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val sensitivity = SensorManager.SENSOR_DELAY_UI
        registerSensorListener(sensorManager, accSensor, sensitivity)

        // we're starting a loop in a coroutine
        Log.i("StateChangeMyPermServ", "Coroutine running")
        GlobalScope.launch(Dispatchers.IO) {
            while (isServiceStarted) {

                try {


                    if (!accelerations.isNullOrEmpty() && accelerations.size == sampleRate) {

                        listenToChanges()
                        Log.i("StateChangeMyPermServ", "Accelerations filled $accelerations")

                        val bpm = calculateBpm(accelerations)

                        filteredRandomTrack =
                            spotifyRepository.getFilteredRandomTrack(this@PermanentService2,
                                tracks,
                                bpm)

                        location = bpmRepository.getLastKnownLocation(this@PermanentService2)
                            ?: LatLng(0.0, 0.0)

                        if (currentTrack == null && filteredRandomTrack != null) {
                            connectRemote()
                            filteredRandomTrack?.playable?.uri?.let {
                                spotifyRepository.playPlayableItem(it)
                            }
                        }

                        Log.i("StateChangeMyPermServ",
                            "Location = $location, currentTrack = ${currentTrack?.playable?.asTrack?.name}, tempo = ${currentTrack?.audioFeatures?.tempo}, tracks = $tracks")

                        val updateNotification = createUpdateNotification(bpm, currentTrack)

                        val bpmEntry = BpmEntry(
                            userId = "",
                            timestamp = Date(System.currentTimeMillis()),
                            bpm = bpm,
                            latitude = location.latitude,
                            longitude = location.longitude,
                            trackTitle = currentTrack?.playable?.asTrack?.name ?: "",
                            trackArtist = currentTrack?.playable?.asTrack?.artists?.joinToString(
                                ", ") ?: "",
                            trackId = currentTrack?.playable?.uri?.id ?: "",
                            trackTempo = currentTrack?.audioFeatures?.tempo ?: 0F
                        )

                        bpmRepository.insert(bpmEntry)

                        with(NotificationManagerCompat.from(applicationContext)) {
                            // notificationId is a unique int for each notification that you must define
                            notify(2, updateNotification)
                        }

                        accelerations.clear()
                    }
                } catch (e: ConcurrentModificationException) {
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

        val simpleDateFormat = SimpleDateFormat("HH:mm", Locale.ENGLISH)
        val timeString = simpleDateFormat.format(System.currentTimeMillis())

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
//                   , 200, 300, 400, 500, 400, 300, 200, 400
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
            .setContentText("Service started at $timeString")
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

        var currentTrackArtists: String? = ""
        var currentTrackName: String? = ""
        var currentTrackTempo: Float? = 0F

        if (currentTrack != null) {
            currentTrackArtists = currentTrack.playable.asTrack?.artists?.joinToString(", ") { simpleArtist ->
                simpleArtist.name
            }
            currentTrackName = currentTrack.playable.asTrack?.name
            currentTrackTempo = currentTrack.audioFeatures.tempo
        }

        return builder
            .setContentTitle("Update")
            .setContentText("Service Update")
            .setContentIntent(pendingIntent)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setStyle(Notification.BigTextStyle()
                .bigText("Bpm at $timeString was $bpm. Song is " +
                        "$currentTrackName by " +
                        "$currentTrackArtists with tempo " +
                        "of ${currentTrackTempo}bpm. " +
                        "Location is $location"))
            .setTicker("Ticker text")
            .build()
    }

    private fun calculateBpm(accelerations: List<Acceleration>): Float {
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

        var currentBpm = 100F

        try {
            currentBpm = Bpm(300, 20).calculateBpm(accelerations) ?: 100F
        } catch (e: ConcurrentModificationException) {

        }

        return currentBpm
    }

    override fun onLocationChanged(location: Location) {
        Log.i("StateChangeMyPermServ", "Location changed, $location")
        this@PermanentService2.location = LatLng(location.latitude, location.longitude)
    }

    private fun restartService() {
        Log.i("StateChangeMyPermServ", "$activityName service restarting")
        val restartServiceIntent = Intent(this, PermanentService2::class.java).also {
            it.setPackage(packageName)
            it.action = Actions.START.name
        }
        val restartServicePendingIntent: PendingIntent =
            PendingIntent.getService(this, 1, restartServiceIntent, PendingIntent.FLAG_IMMUTABLE)
        val alarmService: AlarmManager =
            this.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmService.set(AlarmManager.ELAPSED_REALTIME,
            SystemClock.elapsedRealtime() + 1000,
            restartServicePendingIntent)
    }

    private fun connectRemote() {
//        if (remoteConnected) {
//            return
//        } else {
            spotifyRepository.connectPlayer(this)
//            remoteConnected = true
//        }
    }

    private fun disconnectRemote() {
//        if (!remoteConnected) {
//            return
//        } else {
            spotifyRepository.disconnectPlayer()
//            remoteConnected = false
//        }
    }

    private fun listenToChanges() {
//        spotifyRepository.subscribeToPlayerChanges { playerState ->
//
//            currentTrack = runBlocking {
//                return@runBlocking withContext(Dispatchers.IO) {
//                        spotifyRepository.getTrackFeatures(playerState.track.toSimpleTrack())
//                }
//            }
//            Log.i("StateChangeMyPermServ",
//                "playerState changed ${currentTrack?.playable?.asTrack}")
//        }
    }
}