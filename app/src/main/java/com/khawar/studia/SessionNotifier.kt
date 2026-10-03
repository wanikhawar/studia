package com.khawar.studia

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.khawar.studia.data.RunState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Notifications for a running session, so it works with the app closed:
 * an ongoing one with a live countdown, and one when the time is up (posted by
 * an alarm, even if Android has stopped the app). The session itself is logged
 * the next time the app opens, from the saved start time.
 */
object SessionNotifier {
    private const val CHANNEL_RUNNING = "session_running"
    private const val CHANNEL_DONE = "session_done"
    private const val ID_RUNNING = 1
    private const val ID_DONE = 2

    /** True while the app is on screen; the "time's up" notification is skipped then. */
    @Volatile var appVisible = false

    fun canPost(ctx: Context) =
        Build.VERSION.SDK_INT < 33 || ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun channels(ctx: Context): NotificationManager {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL_RUNNING, "Session in progress", NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel(CHANNEL_DONE, "Session finished", NotificationManager.IMPORTANCE_HIGH))
        return nm
    }

    private fun openApp(ctx: Context) = PendingIntent.getActivity(
        ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun alarmIntent(ctx: Context, what: String = "", minutes: Int = 0) = PendingIntent.getBroadcast(
        ctx, 0,
        Intent(ctx, SessionAlarmReceiver::class.java).putExtra("what", what).putExtra("minutes", minutes),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** Show the countdown and set the alarm for the end. Safe to call again (e.g. after +5 min). */
    fun start(ctx: Context, run: RunState, what: String, zone: ZoneId) {
        val nm = channels(ctx)
        if (canPost(ctx)) {
            val ends = DateTimeFormatter.ofPattern("h:mm a", Locale.US).format(Instant.ofEpochMilli(run.endsAt).atZone(zone))
            val n = Notification.Builder(ctx, CHANNEL_RUNNING)
                .setSmallIcon(R.drawable.ic_stat_timer)
                .setContentTitle("Studying $what")
                .setContentText("Ends at $ends")
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
                .setWhen(run.endsAt)
                .setShowWhen(true)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_STOPWATCH)
                .setContentIntent(openApp(ctx))
                .build()
            nm.notify(ID_RUNNING, n)
        }
        val am = ctx.getSystemService(AlarmManager::class.java)
        val pi = alarmIntent(ctx, what, run.minutes + run.extra)
        // Exact if Android allows it; otherwise as close as the system permits.
        if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, run.endsAt, pi)
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, run.endsAt, pi)
    }

    /** The session ended or was finished early: remove the countdown and the alarm. */
    fun stop(ctx: Context) {
        ctx.getSystemService(NotificationManager::class.java).cancel(ID_RUNNING)
        ctx.getSystemService(AlarmManager::class.java).cancel(alarmIntent(ctx))
    }

    fun timeUp(ctx: Context, what: String, minutes: Int) {
        val nm = channels(ctx)
        nm.cancel(ID_RUNNING)
        if (appVisible || !canPost(ctx)) return
        val n = Notification.Builder(ctx, CHANNEL_DONE)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle("Session complete")
            .setContentText("$minutes min of $what. Open Studia to see your progress.")
            .setAutoCancel(true)
            .setContentIntent(openApp(ctx))
            .build()
        nm.notify(ID_DONE, n)
    }
}

class SessionAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        SessionNotifier.timeUp(context, intent.getStringExtra("what") ?: "your subject", intent.getIntExtra("minutes", 0))
    }
}
