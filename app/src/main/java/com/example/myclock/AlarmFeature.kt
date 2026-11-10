package com.example.myclock

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class SavedAlarm(
    val id: Int,
    val hour: Int,
    val minute: Int,
    val label: String,
    val enabled: Boolean,
    val triggerAt: Long
)

object MyAlarmStore {
    const val CHANNEL = "myclock_alarm_v1"
    const val FIRE = "com.example.myclock.FIRE_ALARM"
    const val STOP = "com.example.myclock.STOP_ALARM"

    private const val DATA = "alarms"

    private fun prefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(
            "myclock_alarms",
            Context.MODE_PRIVATE
        )
    }

    private fun manager(context: Context): AlarmManager {
        return context.getSystemService(AlarmManager::class.java)
    }

    fun ensureChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL,
            "MyClock alarms",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Bunyi penggera MyClock"
            enableVibration(true)

            setSound(
                RingtoneManager.getDefaultUri(
                    RingtoneManager.TYPE_ALARM
                ),
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(
                        AudioAttributes.CONTENT_TYPE_SONIFICATION
                    )
                    .build()
            )
        }

        context.getSystemService(
            NotificationManager::class.java
        ).createNotificationChannel(channel)
    }

    fun canNotify(context: Context): Boolean {
        val runtimeAllowed =
            Build.VERSION.SDK_INT < 33 ||
                    context.checkSelfPermission(
                        Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED

        val notifications = context.getSystemService(
            NotificationManager::class.java
        )

        val channel = notifications.getNotificationChannel(CHANNEL)

        val channelAllowed =
            channel == null ||
                    (
                            channel.importance >=
                                    NotificationManager.IMPORTANCE_DEFAULT &&
                                    channel.sound != null
                            )

        return runtimeAllowed &&
                notifications.areNotificationsEnabled() &&
                channelAllowed
    }

    fun canSchedule(context: Context): Boolean {
        return Build.VERSION.SDK_INT < 31 ||
                manager(context).canScheduleExactAlarms()
    }

    fun load(context: Context): List<SavedAlarm> {
        val array = JSONArray(
            prefs(context).getString(DATA, "[]") ?: "[]"
        )

        return (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)

            SavedAlarm(
                id = item.getInt("id"),
                hour = item.getInt("hour"),
                minute = item.getInt("minute"),
                label = item.getString("label"),
                enabled = item.getBoolean("enabled"),
                triggerAt = item.getLong("triggerAt")
            )
        }.sortedBy {
            it.hour * 60 + it.minute
        }
    }

    private fun write(
        context: Context,
        items: List<SavedAlarm>
    ) {
        val array = JSONArray()

        items.forEach { item ->
            array.put(
                JSONObject().apply {
                    put("id", item.id)
                    put("hour", item.hour)
                    put("minute", item.minute)
                    put("label", item.label)
                    put("enabled", item.enabled)
                    put("triggerAt", item.triggerAt)
                }
            )
        }

        check(
            prefs(context)
                .edit()
                .putString(DATA, array.toString())
                .commit()
        ) {
            "Tetapan alarm tidak dapat disimpan."
        }
    }

    fun nextTime(hour: Int, minute: Int): Long {
        val now = System.currentTimeMillis()

        return Calendar.getInstance(
            TimeZone.getTimeZone("Asia/Kuala_Lumpur")
        ).apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)

            // Jika masa sudah berlalu, jadualkan untuk esok.
            if (timeInMillis <= now) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }.timeInMillis
    }

    private fun pending(
        context: Context,
        id: Int
    ): PendingIntent {
        val intent = Intent(
            context,
            MyAlarmReceiver::class.java
        ).apply {
            action = FIRE
            putExtra("alarm_id", id)
        }

        return PendingIntent.getBroadcast(
            context,
            id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun schedule(
        context: Context,
        alarm: SavedAlarm
    ) {
        check(canSchedule(context)) {
            "Benarkan Alarms & reminders dahulu."
        }

        val openApp = PendingIntent.getActivity(
            context,
            alarm.id,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE
        )

        manager(context).setAlarmClock(
            AlarmManager.AlarmClockInfo(
                alarm.triggerAt,
                openApp
            ),
            pending(context, alarm.id)
        )
    }

    private fun cancel(context: Context, id: Int) {
        manager(context).cancel(pending(context, id))

        context.getSystemService(
            NotificationManager::class.java
        ).cancel(id)
    }

    private fun requireReady(context: Context) {
        ensureChannel(context)

        check(canNotify(context)) {
            "Benarkan notifikasi dan bunyi alarm dahulu."
        }

        check(canSchedule(context)) {
            "Benarkan Alarms & reminders dahulu."
        }
    }

    fun save(
        context: Context,
        id: Int?,
        hour: Int,
        minute: Int,
        label: String
    ) {
        requireReady(context)

        require(hour in 0..23 && minute in 0..59)

        val items = load(context)

        val alarmId = id ?: prefs(context)
            .getInt("next_id", 1000)
            .also { nextId ->
                check(
                    prefs(context)
                        .edit()
                        .putInt("next_id", nextId + 1)
                        .commit()
                )
            }

        val alarm = SavedAlarm(
            id = alarmId,
            hour = hour,
            minute = minute,
            label = label.trim().take(80).ifBlank {
                "Alarm"
            },
            enabled = true,
            triggerAt = nextTime(hour, minute)
        )

        write(
            context,
            items.filterNot { it.id == alarmId } + alarm
        )

        try {
            schedule(context, alarm)
        } catch (error: Exception) {
            write(context, items)
            throw error
        }
    }

    fun toggle(
        context: Context,
        alarm: SavedAlarm,
        enabled: Boolean
    ) {
        if (enabled) {
            save(
                context,
                alarm.id,
                alarm.hour,
                alarm.minute,
                alarm.label
            )
        } else {
            cancel(context, alarm.id)

            write(
                context,
                load(context).map {
                    if (it.id == alarm.id) {
                        it.copy(enabled = false)
                    } else {
                        it
                    }
                }
            )
        }
    }

    fun delete(context: Context, id: Int) {
        cancel(context, id)

        write(
            context,
            load(context).filterNot { it.id == id }
        )
    }

    fun markFired(context: Context, id: Int) {
        write(
            context,
            load(context).map {
                if (it.id == id) {
                    it.copy(enabled = false)
                } else {
                    it
                }
            }
        )
    }

    fun restore(
        context: Context,
        afterBoot: Boolean = false
    ) {
        if (!canSchedule(context)) return

        val now = System.currentTimeMillis()
        val items = load(context)

        val updated = items.map { alarm ->
            if (!alarm.enabled) {
                alarm
            } else if (alarm.triggerAt <= now) {
                // Beri ruang kepada broadcast yang baru tiba masanya.
                if (
                    afterBoot ||
                    alarm.triggerAt < now - 120_000L
                ) {
                    alarm.copy(enabled = false)
                } else {
                    alarm
                }
            } else {
                schedule(context, alarm)
                alarm
            }
        }

        if (updated != items) {
            write(context, updated)
        }
    }
}

// Android memanggil receiver ini apabila tiba masa alarm.
class MyAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent
    ) {
        val id = intent.getIntExtra("alarm_id", -1)
        if (id == -1) return

        val notifications = context.getSystemService(
            NotificationManager::class.java
        )

        if (intent.action == MyAlarmStore.STOP) {
            notifications.cancel(id)
            return
        }

        if (intent.action != MyAlarmStore.FIRE) return

        val alarm = MyAlarmStore.load(context).find {
            it.id == id && it.enabled
        } ?: return

        MyAlarmStore.markFired(context, id)
        MyAlarmStore.ensureChannel(context)

        if (!MyAlarmStore.canNotify(context)) return

        val stopIntent = Intent(
            context,
            MyAlarmReceiver::class.java
        ).apply {
            action = MyAlarmStore.STOP
            putExtra("alarm_id", id)
        }

        val stop = PendingIntent.getBroadcast(
            context,
            id,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE
        )

        val open = PendingIntent.getActivity(
            context,
            id,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE
        )

        val time = String.format(
            Locale.US,
            "%02d:%02d",
            alarm.hour,
            alarm.minute
        )

        val stopAction = Notification.Action.Builder(
            null,
            "Henti",
            stop
        ).build()

        val notification = Notification.Builder(
            context,
            MyAlarmStore.CHANNEL
        )
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("MyClock Alarm $time")
            .setContentText(alarm.label)
            .setCategory(Notification.CATEGORY_ALARM)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setContentIntent(open)
            .setDeleteIntent(stop)
            .setAutoCancel(true)
            .setTimeoutAfter(120_000L)
            .addAction(stopAction)
            .build()
            .apply {
                flags = flags or Notification.FLAG_INSISTENT
            }

        notifications.notify(id, notification)
    }
}

// Pulihkan jadual selepas telefon dihidupkan semula.
class MyAlarmBootReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent
    ) {
        val bootCompleted =
            intent.action == Intent.ACTION_BOOT_COMPLETED

        val permissionGranted =
            intent.action ==
                    AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED

        if (bootCompleted || permissionGranted) {
            runCatching {
                MyAlarmStore.restore(
                    context,
                    afterBoot = bootCompleted
                )
            }
        }
    }
}

@Composable
fun AlarmScreen() {
    val context = LocalContext.current

    var alarms by remember {
        mutableStateOf(MyAlarmStore.load(context))
    }

    var canNotify by remember {
        mutableStateOf(MyAlarmStore.canNotify(context))
    }

    var canSchedule by remember {
        mutableStateOf(MyAlarmStore.canSchedule(context))
    }

    var editingId by rememberSaveable {
        mutableStateOf<Int?>(null)
    }

    var pickedHour by rememberSaveable {
        mutableStateOf(0)
    }

    var pickedMinute by rememberSaveable {
        mutableStateOf(0)
    }

    var label by rememberSaveable {
        mutableStateOf("")
    }

    var showLabelDialog by rememberSaveable {
        mutableStateOf(false)
    }

    fun message(text: String) {
        Toast.makeText(
            context,
            text,
            Toast.LENGTH_LONG
        ).show()
    }

    val notificationPermission =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) {
            canNotify = MyAlarmStore.canNotify(context)

            if (!canNotify) {
                message(
                    "Notifikasi belum dibenarkan. Semak Settings telefon."
                )
            }
        }

    val settingsResult =
        rememberLauncherForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) {
            canNotify = MyAlarmStore.canNotify(context)
            canSchedule = MyAlarmStore.canSchedule(context)

            runCatching {
                MyAlarmStore.restore(context)
            }.onFailure {
                message(
                    it.message ?: "Alarm tidak dapat dipulihkan."
                )
            }

            alarms = MyAlarmStore.load(context)
        }

    // Segarkan senarai dan status kebenaran semasa tab dibuka.
    LaunchedEffect(Unit) {
        MyAlarmStore.ensureChannel(context)

        runCatching {
            MyAlarmStore.restore(context)
        }.onFailure {
            message(
                it.message ?: "Alarm tidak dapat dipulihkan."
            )
        }

        while (true) {
            alarms = MyAlarmStore.load(context)
            canNotify = MyAlarmStore.canNotify(context)
            canSchedule = MyAlarmStore.canSchedule(context)
            delay(1000)
        }
    }

    fun chooseTime(existing: SavedAlarm? = null) {
        if (
            !MyAlarmStore.canNotify(context) ||
            !MyAlarmStore.canSchedule(context)
        ) {
            message(
                "Benarkan kedua-dua kebenaran alarm di atas dahulu."
            )
            return
        }

        val now = Calendar.getInstance(
            TimeZone.getTimeZone("Asia/Kuala_Lumpur")
        ).apply {
            add(Calendar.MINUTE, 1)
        }

        editingId = existing?.id
        label = existing?.label ?: ""

        val initialHour =
            existing?.hour ?: now.get(Calendar.HOUR_OF_DAY)

        val initialMinute =
            existing?.minute ?: now.get(Calendar.MINUTE)

        TimePickerDialog(
            context,
            { _, hour, minute ->
                pickedHour = hour
                pickedMinute = minute
                showLabelDialog = true
            },
            initialHour,
            initialMinute,
            true
        ).show()
    }

    val dateFormat = remember {
        SimpleDateFormat(
            "d MMM yyyy, HH:mm",
            Locale.US
        ).apply {
            timeZone = TimeZone.getTimeZone(
                "Asia/Kuala_Lumpur"
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "Alarms",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )

        Text("Waktu Malaysia • Penggera sekali sahaja")

        if (!canNotify) {
            Text(
                "Benarkan notifikasi supaya alarm boleh berbunyi."
            )

            OutlinedButton(
                onClick = {
                    if (
                        Build.VERSION.SDK_INT >= 33 &&
                        context.checkSelfPermission(
                            Manifest.permission.POST_NOTIFICATIONS
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        notificationPermission.launch(
                            Manifest.permission.POST_NOTIFICATIONS
                        )
                    } else {
                        settingsResult.launch(
                            Intent(
                                Settings.ACTION_APP_NOTIFICATION_SETTINGS
                            ).putExtra(
                                Settings.EXTRA_APP_PACKAGE,
                                context.packageName
                            )
                        )
                    }
                }
            ) {
                Text("Benarkan notifikasi")
            }

            TextButton(
                onClick = {
                    settingsResult.launch(
                        Intent(
                            Settings.ACTION_APP_NOTIFICATION_SETTINGS
                        ).putExtra(
                            Settings.EXTRA_APP_PACKAGE,
                            context.packageName
                        )
                    )
                }
            ) {
                Text("Buka tetapan notifikasi")
            }
        }

        if (!canSchedule) {
            Text(
                "Benarkan Alarms & reminders untuk penggera tepat pada masanya."
            )

            OutlinedButton(
                onClick = {
                    if (Build.VERSION.SDK_INT >= 31) {
                        settingsResult.launch(
                            Intent(
                                Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                Uri.parse(
                                    "package:${context.packageName}"
                                )
                            )
                        )
                    }
                }
            ) {
                Text("Benarkan Alarms & reminders")
            }
        }

        Button(
            onClick = { chooseTime() }
        ) {
            Text("+ Tambah alarm")
        }

        if (alarms.isEmpty()) {
            Text(
                "Belum ada alarm. Tekan + Tambah alarm."
            )
        }

        alarms.forEach { alarm ->
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment =
                            Alignment.CenterVertically,
                        horizontalArrangement =
                            Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = String.format(
                                Locale.US,
                                "%02d:%02d",
                                alarm.hour,
                                alarm.minute
                            ),
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Switch(
                            checked = alarm.enabled,
                            onCheckedChange = { enabled ->
                                runCatching {
                                    MyAlarmStore.toggle(
                                        context,
                                        alarm,
                                        enabled
                                    )
                                }.onFailure {
                                    message(
                                        it.message
                                            ?: "Alarm tidak dapat diubah."
                                    )
                                }

                                alarms = MyAlarmStore.load(
                                    context
                                )
                            }
                        )
                    }

                    Text(alarm.label)

                    Text(
                        text = if (alarm.enabled) {
                            "Dijadualkan: ${
                                dateFormat.format(
                                    Date(alarm.triggerAt)
                                )
                            }"
                        } else {
                            "Tidak aktif"
                        }
                    )

                    Row(
                        horizontalArrangement =
                            Arrangement.spacedBy(12.dp)
                    ) {
                        TextButton(
                            onClick = {
                                chooseTime(alarm)
                            }
                        ) {
                            Text("Ubah")
                        }

                        TextButton(
                            onClick = {
                                runCatching {
                                    MyAlarmStore.delete(
                                        context,
                                        alarm.id
                                    )
                                }.onFailure {
                                    message(
                                        it.message
                                            ?: "Alarm tidak dapat dipadam."
                                    )
                                }

                                alarms = MyAlarmStore.load(
                                    context
                                )
                            }
                        ) {
                            Text("Padam")
                        }
                    }
                }
            }
        }

        Text(
            "Apabila berbunyi, buka notifikasi MyClock dan tekan Henti."
        )
    }

    if (showLabelDialog) {
        AlertDialog(
            onDismissRequest = {
                showLabelDialog = false
            },
            title = {
                Text("Label alarm")
            },
            text = {
                OutlinedTextField(
                    value = label,
                    onValueChange = {
                        label = it.take(80)
                    },
                    label = {
                        Text("Contoh: Bangun pagi")
                    },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        runCatching {
                            MyAlarmStore.save(
                                context,
                                editingId,
                                pickedHour,
                                pickedMinute,
                                label
                            )
                        }.onSuccess {
                            alarms = MyAlarmStore.load(
                                context
                            )

                            showLabelDialog = false

                            message(
                                "Alarm disimpan. Semak tarikh dan masa pada kad alarm."
                            )
                        }.onFailure {
                            message(
                                it.message
                                    ?: "Alarm tidak dapat disimpan."
                            )
                        }
                    }
                ) {
                    Text("Simpan")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showLabelDialog = false
                    }
                ) {
                    Text("Batal")
                }
            }
        )
    }
}

