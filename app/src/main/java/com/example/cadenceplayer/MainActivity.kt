package com.example.cadenceplayer

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.cadenceplayer.broadcast.*
import com.example.cadenceplayer.databinding.ActivityMainBinding
import com.example.cadenceplayer.infrastructure.MediaSessionHandler
import com.example.cadenceplayer.model.TrackFeatures
import com.example.cadenceplayer.permissions.PermissionsHandler
import com.example.cadenceplayer.recyclerview.RecyclerType
import com.example.cadenceplayer.recyclerview.TrackRecyclerAdapter
import com.example.cadenceplayer.repositories.AlarmRepository
import com.example.cadenceplayer.repositories.BpmRepository
import com.example.cadenceplayer.room.Alarm
import com.example.cadenceplayer.room.AlarmDatabase
import com.example.cadenceplayer.room.BpmDatabase
import com.example.cadenceplayer.room.BpmEntry
import com.example.cadenceplayer.service.Actions
import com.example.cadenceplayer.spotify.PlayableItem
import com.example.cadenceplayer.spotify.SpotifyAppRemoteApi
import com.example.cadenceplayer.viewmodel.BpmViewModel
import com.example.cadenceplayer.viewmodel.BpmViewModelFactory
import com.example.cadenceplayer.viewmodel.SpotifyViewModel
import com.example.cadenceplayer.viewmodel.SpotifyViewModelFactory
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.*
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import java.sql.Date


class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val activityName = this.javaClass.simpleName
    private val spotifyBroadcastReceiver = SpotifyBroadcastReceiver()
    private var songChanged = false
    private val spotifyIntentFilter = IntentFilter()
    private lateinit var spotifyViewModel: SpotifyViewModel
    private lateinit var bpmViewModel: BpmViewModel
    private var newTrack: String = ""
    private var metadataChanged = false
    private var playbackChanged = false

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onCreate(savedInstanceState: Bundle?) {
        Log.i("StateChangeMyPermServ", "$activityName created")
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        PermissionsHandler(this).requestPermission(this)

//        MediaSessionHandler(this).getMediaSession()

        prepareSpotifyViewModel()

//        spotifyIntentFilter.addAction(SpotifyBroadcastReceiver.BroadcastTypes.PLAYBACK_STATE_CHANGED)
//        spotifyIntentFilter.addAction(SpotifyBroadcastReceiver.BroadcastTypes.METADATA_CHANGED)
//        this.registerReceiver(spotifyBroadcastReceiver, spotifyIntentFilter)

    }

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onDestroy() {
        Log.i("StateChangeMyPermServ", "$activityName destroyed")
//        this.unregisterReceiver(SpotifyBroadcastReceiver())
        EventBus.getDefault().unregister(this)
//        spotifyViewModel.closeSpotifyRemote()
//      TODO: Add some saveState code to save tracklist for user when reopening
        super.onDestroy()
    }

    override fun onStart() {
        spotifyViewModel.openSpotifyRemote()
        super.onStart()
        Log.i("StateChangeMyPermServ", "$activityName started")
    }

    override fun onStop() {
        spotifyViewModel.closeSpotifyRemote()
        super.onStop()
        Log.i("StateChangeMyPermServ", "$activityName stopped")
    }

    @RequiresApi(Build.VERSION_CODES.M)
    fun initButtons() {
        val startButton = binding.startButton
        val stopButton = binding.stopButton
        val remoteConnectionStatusButton = binding.remoteConnectionStatusButton

        startButton.setOnClickListener {
            Log.i("StateChangeMyPermServ", "$activityName start pressed")
            setAlarms(Actions.START)
        }

        stopButton.setOnClickListener {
            Log.i("StateChangeMyPermServ", "$activityName stop pressed")
            cancelAlarms()
        }

        remoteConnectionStatusButton.setOnClickListener {
            Log.i("StateChangeMyPermServ", "$activityName connect button pressed")
            spotifyViewModel.openSpotifyRemote()
        }

        startButton.keepScreenOn = true
    }

    @RequiresApi(Build.VERSION_CODES.M)
    fun setAlarms(action: Actions) {
        GlobalScope.launch(Dispatchers.Main) {

            if (!spotifyViewModel.tracks.isNullOrEmpty()) {
                val randomTrack = spotifyViewModel.tracks.random()
                if (randomTrack != null) {
                    spotifyViewModel.play(randomTrack.playable.uri)
                }
            }

            val alarmMgr = this@MainActivity.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val bpmDatabase: BpmDatabase = BpmDatabase.getDatabase(this@MainActivity, GlobalScope)
            val alarmDatabase: AlarmDatabase = AlarmDatabase.getDatabase(this@MainActivity, GlobalScope)
            val sampleRate = this@MainActivity.resources.getInteger(R.integer.sample_rate)
            val bpmRepository = BpmRepository(bpmDatabase.bpmEntryDao(), sampleRate)
            val alarmRepository = AlarmRepository(alarmDatabase.alarmDao())
            val lastKnownLocation = bpmRepository.getLastKnownLocation(this@MainActivity) ?: LatLng(0.0, 0.0)
            val userId = spotifyViewModel.getUserId()
            val playlistUri = spotifyViewModel.playlist?.uri
            val alarmList = mutableListOf<Alarm>()
//            val playlistTracks = Json.encodeToString(spotifyViewModel.tracks as ArrayList<TrackFeatures?>)

            val application = (application as CadencePlayer)
            application.setMyData(spotifyViewModel.tracks)
            Log.i("AlarmReceiver", "userId = $userId, location = $lastKnownLocation")

            val args = Bundle()

            args.putParcelable("lastLocation", lastKnownLocation)
            args.putString("userId", userId)
            args.putString("playlist", playlistUri)
//            args.putSerializable("playlistTracks", playlistTracks)

//            for (i in (0 until 1)) {
//                val SIXTY_SECONDS = (i * 1000 * 60).toLong()
                val alarmIntent = Intent(this@MainActivity, AlarmReceiver::class.java).also {
                    it.addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                    it.putExtra("requestCode", 100)
                    it.putExtra("bundle", args)
                    it.action = action.name
                }.let { intent ->
                    PendingIntent.getBroadcast(this@MainActivity, 100, intent, PendingIntent.FLAG_IMMUTABLE)
                }

                val alarm = Alarm(
                    requestCode = 100,
                    timestamp = Date(System.currentTimeMillis()),
                    status = Alarm.AlarmStatus.QUEUED,
                    location = lastKnownLocation,
                    userId = userId
                )

                Log.i("AlarmReceiver", "Creating intent $alarmIntent, alarm = $alarm")

                alarmList.add(alarm)

                alarmMgr.setExactAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    SystemClock.elapsedRealtime(),
                    alarmIntent
                )
//            }

            alarmRepository.insertAll(alarmList)
        }

    }

    @RequiresApi(Build.VERSION_CODES.M)
    fun cancelAlarms() {

        GlobalScope.launch(Dispatchers.Main) {
            val alarmMgr = this@MainActivity.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val alarmDatabase: AlarmDatabase = AlarmDatabase.getDatabase(this@MainActivity, GlobalScope)
            val alarmRepository = AlarmRepository(alarmDatabase.alarmDao())
            val alarmList = alarmRepository.getAllAlarms()
            for (alarm in alarmList) {

                val requestCode = alarm.requestCode
                val lastKnownLocation = alarm.location
                val userId = alarm.userId
                val args = Bundle()

                args.putParcelable("lastLocation", lastKnownLocation)
                args.putString("userId", userId)

                val intent = Intent(this@MainActivity, AlarmReceiver::class.java).also {
                    it.putExtra("requestCode", requestCode)
                    it.putExtra("bundle", args)
                    it.action = Actions.START.name
                }.let { intent ->
                    PendingIntent.getBroadcast(this@MainActivity, requestCode, intent, PendingIntent.FLAG_IMMUTABLE)
                }

                Log.i("AlarmReceiver", "Cancelling $intent, alarm = $alarm")

                alarmMgr.cancel(intent)
            }

            alarmRepository.deleteAll()

            setAlarms(Actions.STOP)

        }

        Toast.makeText(this, "Stopping service", Toast.LENGTH_SHORT).show()
    }

    @RequiresApi(Build.VERSION_CODES.M)
    @Subscribe
    fun onMetadataReceived(metadata: OnReceiveMetadata) {

        Log.i("StateChangeMyPermServ", "metadata received at ${metadata.timeSentInMs}, songChange = $songChanged")

        if (!metadataChanged && !playbackChanged) {

            val tracks = spotifyViewModel.tracks
            val bpm = bpmViewModel.allBpmEntries.value?.first()?.bpm ?: 100F
            val filteredRandomTrack = spotifyViewModel.getFilteredRandomTrack(this@MainActivity, tracks, bpm)

            Log.i("StateChangeMyPermServ", "bpm = $bpm, filteredTrack = ${filteredRandomTrack?.playable?.asTrack?.name}")

            if (filteredRandomTrack != null) {
                spotifyViewModel.play(filteredRandomTrack.playable.uri)
                spotifyViewModel.recentTracks.add(filteredRandomTrack.playable.uri)


                val bpmDatabase: BpmDatabase = BpmDatabase.getDatabase(this@MainActivity, GlobalScope)
                val sampleRate = this@MainActivity.resources.getInteger(R.integer.sample_rate)
                val bpmRepository = BpmRepository(bpmDatabase.bpmEntryDao(), sampleRate)
                val location = bpmRepository.getLastKnownLocation(this@MainActivity) ?: LatLng(0.0, 0.0)

                bpmViewModel.insert(
                    BpmEntry(
                        userId = spotifyViewModel.getUserId(),
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
                )
            } else if (!spotifyViewModel.recentTracks.isNullOrEmpty()) {
                spotifyViewModel.play(spotifyViewModel.recentTracks.last())
            }
        }

        metadataChanged = !metadataChanged

    }

    @Subscribe
    fun onPlaybackReceived(playback: OnReceivePlayback) {

        Log.i("SpotifyBroadcast", "playback received at $playback")
    }

    private fun observeConnectionStatus() {
//        withContext(Dispatchers.Main) {
            val remoteConnectionStatusButton = binding.remoteConnectionStatusButton

            spotifyViewModel.connectionState.observe(this@MainActivity, { state ->
                with(remoteConnectionStatusButton) {
                    when (state) {
                        SpotifyAppRemoteApi.ConnectionStatus.CONNECTED -> {
                            this.setBackgroundResource(R.color.colorNoError)
                            this.setText(R.string.connected)
                            this.isEnabled = false
                        }
                        SpotifyAppRemoteApi.ConnectionStatus.DISCONNECTED -> {
                            this.setBackgroundResource(R.color.colorError)
                            this.setText(R.string.disconnected)
                            this.isEnabled = true
                        }
                        SpotifyAppRemoteApi.ConnectionStatus.CONNECTING -> {
                            this.setBackgroundResource(R.color.colorError)
                            this.setText(R.string.connecting)
                            this.isEnabled = false
                        } else -> {
                        }
                    }
                }
            })
//        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun observePlaylists() {
        spotifyViewModel.getPlaylists().observe(this@MainActivity, {playlistList ->
            spotifyViewModel.isLoadingLiveData.postValue(false)

            spotifyViewModel.playlistList = playlistList as MutableList<PlayableItem>

            spotifyViewModel.playlistList.add(0, PlayableItem(
                type = PlayableItem.Type.SIMPLE_PLAYLIST,
                name = "Saved Tracks",
                authors = listOf(spotifyViewModel.getUserId()),
                uri = "",
                href = ""
            )
            )

            loadPlaylists(spotifyViewModel.playlistList)
        })
    }

    private fun observeBpmDatabase() {
//        withContext(Dispatchers.Main) {
            bpmViewModel.allBpmEntries.observeForever { bpmEntry ->
                if (!bpmEntry.isNullOrEmpty()) {
                    val name = bpmEntry.first().trackTitle
                    val tempo = bpmEntry.first().trackTempo
                    val bpm = bpmEntry.first().bpm
                    val location = LatLng(bpmEntry.first().latitude, bpmEntry.first().longitude)
                    val userId = bpmEntry.first().userId
                    Log.i("StateChangeMyPermServ",
                        "bpmentry: title = $name, tempo = $tempo, bpm = $bpm, location = $location, userId = $userId")
                }
            }
//        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun observePlayerState() {
//        withContext(Dispatchers.Main) {
            spotifyViewModel.playerState.observeForever { playerState ->
//                Log.i("StateChangeMyPermServ", "playerstate $playerState")
//                if (playerState.track != null) {
//                    val bpmDatabase: BpmDatabase = BpmDatabase.getDatabase(this@MainActivity, GlobalScope)
//                    val sampleRate = this@MainActivity.resources.getInteger(R.integer.sample_rate)
//                    val bpmRepository = BpmRepository(bpmDatabase.bpmEntryDao(), sampleRate)
//                    val playbackPosition = playerState.playbackPosition
//                    val isPaused = playerState.isPaused
//                    val playbackSpeed = playerState.playbackSpeed
//                    val tracks = spotifyViewModel.tracks
//                    val recentTracks = spotifyViewModel.recentTracks
//                    if (recentTracks.size >= tracks.size) {
//                        spotifyViewModel.recentTracks = mutableListOf()
//                    }
//                    val location = bpmRepository.getLastKnownLocation(this@MainActivity) ?: LatLng(0.0, 0.0)
//                    val bpm = bpmViewModel.allBpmEntries.value?.first()?.bpm ?: 100F
//                    val filteredRandomTrack = spotifyViewModel.getFilteredRandomTrack(this@MainActivity, tracks, bpm)
//                    newTrack = filteredRandomTrack?.playable?.uri?.id.toString()
//                    Log.i("StateChangeMyPermServ", "track pos = $playbackPosition, paused = $isPaused, speed = $playbackSpeed, currenttrack = ${playerState.track.uri}, newTrack = ${newTrack}, filteredtrack = ${filteredRandomTrack?.playable?.uri?.id}, songChanged = $songChanged")
//
//                    if (filteredRandomTrack != null) {
//                        Log.i("StateChangeMyPermServ", "filteredTrack is not null ${filteredRandomTrack.playable.asTrack?.name}")
//                    }
////                    if (playFlag) {
////                        Log.i("StateChangeMyPermServ", "playFlag is true $playFlag")
////                    }
//                    if (songChanged) {
//                        Log.i("StateChangeMyPermServ", "songChanged is true $songChanged")
//                    }
//                    if (playbackPosition == 0L && !isPaused && playbackSpeed == 0F) {
//                        Log.i("StateChangeMyPermServ", "track pos is 0 = ($playbackPosition), is not paused = ($isPaused), speed is 0 = ($playbackSpeed))")
//                    }
//                    if (playbackPosition <= 100L && !isPaused && playbackSpeed == 1F) {
//                        Log.i("StateChangeMyPermServ", "track pos < 100 = ($playbackPosition), is not paused = ($isPaused), speed is 1 = ($playbackSpeed))")
//                    }
//
//                    if (filteredRandomTrack != null
//                        && ((playbackPosition <= 100L && !isPaused && playbackSpeed == 1F))
//                        && !playbackChanged
//                        && !metadataChanged
//                    ) {
//                        Log.i("StateChangeMyPermServ", "playing a new song ${filteredRandomTrack.playable.asTrack?.name} with tempo of ${filteredRandomTrack.audioFeatures.tempo} to match bpm $bpm because filtered track isn't null (${filteredRandomTrack != null}), track isn't paused ($isPaused), posn is < 100 and speed is 1 (${(playbackPosition <= 100L && !isPaused && playbackSpeed == 1F)}), songChanged equals false ($songChanged)")
//                        spotifyViewModel.play(filteredRandomTrack.playable.uri)
//                        spotifyViewModel.recentTracks.add(filteredRandomTrack.playable.uri)
//
//                        bpmViewModel.insert(
//                            BpmEntry(
//                                userId = spotifyViewModel.getUserId(),
//                                timestamp = Date(System.currentTimeMillis()),
//                                bpm = bpm,
//                                latitude = location.latitude,
//                                longitude = location.longitude,
//                                trackTitle = filteredRandomTrack.playable.asTrack?.name ?: "",
//                                trackArtist = filteredRandomTrack.playable.asTrack?.artists?.joinToString(
//                                    ", ") ?: "",
//                                trackId = filteredRandomTrack.playable.id ?: "",
//                                trackTempo = filteredRandomTrack.audioFeatures.tempo
//                            )
//                        )
//                    } else if (filteredRandomTrack != null
//                        && ((playbackPosition <= 100L && !isPaused && playbackSpeed == 1F))) {
//                            playbackChanged = !playbackChanged
//                    }
//                }
            }
//        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun prepareSpotifyViewModel() {
//        GlobalScope.launch(Dispatchers.Main) {
            Log.i("StateChangeMyPermServ", "$activityName preparing viewmodel")

//            async {

                spotifyViewModel = ViewModelProvider(
                    this@MainActivity,
                    SpotifyViewModelFactory(application)
                ).get(SpotifyViewModel::class.java)

                spotifyViewModel.isLoadingLiveData.postValue(true)

                EventBus.getDefault().register(this@MainActivity)

//                spotifyViewModel.openSpotifyRemote()

                bpmViewModel = ViewModelProvider(
                    this@MainActivity,
                    BpmViewModelFactory(application)
                ).get(BpmViewModel::class.java)

                observeConnectionStatus()

                observePlaylists()

                observeBpmDatabase()

                observePlayerState()

                observeLoadingIndicator()

                initButtons()
//            }
//        }
    }

    private fun observeLoadingIndicator() {
            spotifyViewModel.isLoadingLiveData.observe(this@MainActivity, { loadingIndicator ->
                Log.i("StateChangeMyPermServ", "loading $loadingIndicator")
                runOnUiThread {
                    run {
                        binding.progressBar.visibility = if (loadingIndicator) {
                            Log.i("StateChangeMyPermServ", "progressbar visible")
                            View.VISIBLE
                        } else {
                            Log.i("StateChangeMyPermServ", "progressbar gone")
                            View.GONE
                        }

                        binding.trackRecyclerView.visibility = if (loadingIndicator) {
                            Log.i("StateChangeMyPermServ", "recyclerview gone")
                            View.INVISIBLE
                        } else {
                            Log.i("StateChangeMyPermServ", "recyclerview visible")
                            View.VISIBLE
                        }
                    }
                }
            })
//        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun playlistClicked(playlist: PlayableItem) {

        spotifyViewModel.isLoadingLiveData.value = true

        runOnUiThread {
            run {
                Log.i("StateChangeMyPermServ", "updating progressBar visibility")
                this.binding.progressBar.visibility = View.VISIBLE
                this.binding.trackRecyclerView.visibility = View.INVISIBLE
            }
        }

        GlobalScope.launch(Dispatchers.IO) {

            Log.i("StateChangeMyPermServ", "$playlist clicked")
            val trackList: List<TrackFeatures?> = if(playlist.name != "Saved Tracks") {
                spotifyViewModel.getPlaylistTracks(playlist.uri)
            } else {
                spotifyViewModel.getSavedTracks().mapNotNull { it }
            }
            spotifyViewModel.tracks = trackList as MutableList<TrackFeatures?>

            if(playlist.name != "Saved Tracks") {
                spotifyViewModel.playlist = playlist
            }
            Log.i("StateChangeMyPermServ", "$playlist tracks are $trackList")

            runOnUiThread {
                run {
                    val trackRecyclerView = binding.trackRecyclerView

                    trackRecyclerView.apply {
                        layoutManager = LinearLayoutManager(this@MainActivity)
                        val recyclerAdapter = TrackRecyclerAdapter(
                            RecyclerType.Track,
                            trackList as ArrayList<TrackFeatures>,
                            arrayListOf(),
                            { _, trackFeatures ->
                                spotifyViewModel.play(trackFeatures.playable.uri)
                                spotifyViewModel.recentTracks.add(trackFeatures.playable.uri)
                                newTrack = trackFeatures.playable.uri.id
                            },
                            { _, playlist -> }
                        )
                        adapter = recyclerAdapter
                        recyclerAdapter.notifyDataSetChanged()
                }
            }


                spotifyViewModel.isLoadingLiveData.postValue(false)

                Log.i("StateChangeMyPermServ", "$playlist loaded")
            }
        }

    }

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onBackPressed() {
        spotifyViewModel.recentTracks.clear()
        loadPlaylists(spotifyViewModel.playlistList)
    }

    @RequiresApi(Build.VERSION_CODES.M)
    fun loadPlaylists(playlistList: List<PlayableItem>) {

        val trackRecyclerView = binding.trackRecyclerView

        trackRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            val recyclerAdapter = TrackRecyclerAdapter(
                RecyclerType.Playlist,
                arrayListOf(),
                playlistList as ArrayList<PlayableItem>,
                { _, trackFeatures -> },
                { _, playlist ->
                    playlistClicked(playlist)
                }
            )
            adapter = recyclerAdapter
            recyclerAdapter.notifyDataSetChanged()
        }
    }
}