package com.example.cadenceplayer.broadcast

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.annotation.RequiresApi
import com.example.cadenceplayer.service.Actions
import com.example.cadenceplayer.service.PermanentService
import com.google.android.gms.maps.model.LatLng

@RequiresApi(Build.VERSION_CODES.N)
class AlarmReceiver : BroadcastReceiver() {

    private var wakeLock: PowerManager.WakeLock? = null
    private var alarmMgr: AlarmManager? = null
    private val activityName = this.javaClass.simpleName

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onReceive(context: Context?, intent: Intent?) {

        Log.i("StateChange", "$activityName received")

        // we need this lock so our service gets not affected by Doze Mode
        if (intent != null && context != null) {
            alarmMgr = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            Log.i("AlarmReceiver", "Acquiring wakelock")

            wakeLock =
                (context.getSystemService(Context.POWER_SERVICE) as PowerManager).run {
                    newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PermanentService::lock").apply {
                        if (!this.isHeld) {
                            acquire(24*60*60*1000L /*24 hours*/)
                        }
                    }
                }

//            setNewAlarm(context, intent)

            startService(context, intent)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun startService(context: Context, intent: Intent) {

        val action = intent.action
        val bundle: Bundle? = intent.getParcelableExtra("bundle")

        val lastKnownLocation: LatLng? = bundle?.getParcelable("lastLocation")
        val userId: String? = bundle?.getString("userId")
        val playlistUri = bundle?.getString("playlist")
//        val playlistTracks = bundle?.getSerializable("playlistTracks")

        Log.i("AlarmReceiver", "Starting service, userId = $userId, location = $lastKnownLocation, playlist = $playlistUri")

        val serviceArgs = Bundle()

        serviceArgs.putParcelable("lastLocation", lastKnownLocation)
        serviceArgs.putString("userId", userId)
        serviceArgs.putString("playlist", playlistUri)
//        serviceArgs.putSerializable("playlistTracks", playlistTracks)

        Intent(context, PermanentService::class.java).also {
            it.action = action
            it.putExtra("bundle", serviceArgs)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(it)
                return
            }
            context.startService(it)
        }
    }

    private fun setNewAlarm(context: Context, intent: Intent) {

        val action = intent.action
        when (action) {
            Actions.START.name -> {
                val bundle: Bundle? = intent.getParcelableExtra("bundle")

                val lastKnownLocation: LatLng? = bundle?.getParcelable("lastLocation")
                val userId: String? = bundle?.getString("userId")

                val alarmMgr = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

                val args = Bundle()

                args.putParcelable("lastLocation", lastKnownLocation)
                args.putString("userId", userId)

                val alarmIntent = Intent(context, AlarmReceiver::class.java).also {
                    it.addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                    it.putExtra("requestCode", 100)
                    it.putExtra("bundle", args)
                    it.action = action
                }.let { pendingIntent ->
                    PendingIntent.getBroadcast(context, 100, pendingIntent, PendingIntent.FLAG_IMMUTABLE)
                }

                val SIXTY_SECONDS = (1000 * 60).toLong()

                alarmMgr.setExactAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    SystemClock.elapsedRealtime() + SIXTY_SECONDS,
                    alarmIntent
                )
            }
            Actions.STOP.name -> {}
        }
    }
}