package com.predictengines.app.reminders

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.predictengines.app.MainActivity
import com.predictengines.app.data.DateUtils
import com.predictengines.app.data.ListPicksStore
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** Settings for the daily "picks coming up" reminder, kept with the app's other preferences. */
object ReminderPrefs {
    private const val FILE = "predict_engines"
    private const val ENABLED = "reminder_enabled"
    private const val HOUR = "reminder_hour"

    fun enabled(c: Context) = c.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(ENABLED, false)
    fun hour(c: Context) = c.getSharedPreferences(FILE, Context.MODE_PRIVATE).getInt(HOUR, 9)

    fun setEnabled(c: Context, on: Boolean) =
        c.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean(ENABLED, on).apply()

    fun setHour(c: Context, hour: Int) =
        c.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putInt(HOUR, hour.coerceIn(0, 23)).apply()
}

object ReminderScheduler {
    private const val REQUEST = 4711
    const val CHANNEL = "priwin_reminders"

    private fun pending(c: Context): PendingIntent =
        PendingIntent.getBroadcast(
            c, REQUEST, Intent(c, ReminderReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    /** Sets (or clears) the next alarm to match the saved settings. Safe to call as often as you like. */
    fun sync(c: Context) {
        val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (!ReminderPrefs.enabled(c)) {
            am.cancel(pending(c))
            return
        }
        val now = LocalDateTime.now()
        var next = now.toLocalDate().atTime(ReminderPrefs.hour(c), 0)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val at = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        // Not exact to the minute (the phone may delay it a little to save battery), and needs no special permission.
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending(c))
    }

    fun canNotify(c: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(c, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** Builds the reminder text for today and tomorrow from the recorded list picks; null when there is nothing. */
    fun message(c: Context, now: LocalDateTime = LocalDateTime.now()): Pair<String, String>? {
        val today = now.toLocalDate()
        val hidden = c.getSharedPreferences("predict_engines", Context.MODE_PRIVATE)
            .getString("hidden_leagues", "").orEmpty().split('\n').filter { it.isNotBlank() }.toSet()
        val picks = ListPicksStore(c).load().values.filter { it.leagueId !in hidden }
        fun on(day: LocalDate) = picks.filter { e ->
            DateUtils.parse(e.date) == day && !DateUtils.isPlayed(e.date, e.time, now)
        }
        val t = on(today)
        val m = on(today.plusDays(1))
        if (t.isEmpty() && m.isEmpty()) return null
        fun describe(list: List<ListPicksStore.Entry>): String {
            val bySource = list.groupBy { it.source }
            return ListPicksStore.SOURCES.filter { it in bySource }.joinToString(", ") { "$it ${bySource[it]!!.size}" }
        }
        val short = buildString {
            if (t.isNotEmpty()) append("Today: ${t.size} ${if (t.size == 1) "pick" else "picks"}")
            if (t.isNotEmpty() && m.isNotEmpty()) append(" · ")
            if (m.isNotEmpty()) append("Tomorrow: ${m.size} ${if (m.size == 1) "pick" else "picks"}")
        }
        val long = buildString {
            if (t.isNotEmpty()) append("Today (${describe(t)})\n")
            t.sortedBy { it.time }.take(5).forEach { append("• ${it.time.take(5)} ${it.home} v ${it.away}: ${it.label}\n") }
            if (m.isNotEmpty()) append("Tomorrow (${describe(m)})\n")
            m.sortedBy { it.time }.take(5).forEach { append("• ${it.time.take(5)} ${it.home} v ${it.away}: ${it.label}\n") }
        }.trimEnd()
        return short to long
    }

    /** Shows the reminder now. With [force] a short message appears even when no picks are coming up (for testing). */
    fun notify(c: Context, force: Boolean = false): Boolean {
        if (!canNotify(c)) return false
        val msg = message(c) ?: if (force) "No picks kick off today or tomorrow." to "Nothing on your Top 20, Handicap or Over 1.5 lists kicks off today or tomorrow." else return false
        val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Pick reminders", NotificationManager.IMPORTANCE_DEFAULT))
        }
        val open = PendingIntent.getActivity(
            c, 0, Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(c, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Priwin · picks coming up")
            .setContentText(msg.first)
            .setStyle(NotificationCompat.BigTextStyle().bigText(msg.second))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        return try {
            NotificationManagerCompat.from(c).notify(1001, n)
            true
        } catch (e: SecurityException) {
            false
        }
    }
}

/** Fires at the chosen time: shows the reminder (if anything is coming up) and sets the next day's alarm. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (ReminderPrefs.enabled(context)) ReminderScheduler.notify(context)
        ReminderScheduler.sync(context)
    }
}

/** After a restart the alarm is gone, so it is set again. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        ReminderScheduler.sync(context)
    }
}
