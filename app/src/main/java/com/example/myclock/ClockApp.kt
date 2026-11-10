package com.example.myclock

import android.content.Context
import android.media.RingtoneManager
import android.os.SystemClock
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

// =====================================================
// APP AND BOTTOM NAVIGATION
// =====================================================

@Composable
fun ClockApp() {
    var selectedTab by rememberSaveable {
        mutableStateOf("Clock")
    }

    var showAnalog by rememberSaveable {
        mutableStateOf(false)
    }

    var is24Hour by rememberSaveable {
        mutableStateOf(true)
    }

    Scaffold(
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(
                            horizontal = 6.dp,
                            vertical = 10.dp
                        ),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf(
                        "Clock",
                        "World Clock",
                        "Alarms",
                        "Timers",
                        "Stopwatch"
                    ).forEach { tab ->
                        val selected = selectedTab == tab

                        Button(
                            onClick = {
                                selectedTab = tab
                            },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 52.dp),
                            contentPadding = PaddingValues(
                                horizontal = 1.dp,
                                vertical = 8.dp
                            ),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.secondaryContainer
                                },
                                contentColor = if (selected) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    MaterialTheme.colorScheme.onSecondaryContainer
                                }
                            )
                        ) {
                            Text(
                                text = when (tab) {
                                    "World Clock" -> "World\nClock"
                                    "Stopwatch" -> "Stop\nwatch"
                                    else -> tab
                                },
                                fontSize = 10.sp,
                                lineHeight = 13.sp,
                                textAlign = TextAlign.Center,
                                maxLines = 2
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
        ) {
            when (selectedTab) {
                "Clock" -> ClockScreen(
                    showAnalog = showAnalog,
                    is24Hour = is24Hour,
                    onToggleAnalog = {
                        showAnalog = !showAnalog
                    },
                    onToggleTimeFormat = {
                        is24Hour = !is24Hour
                    }
                )

                "World Clock" -> WorldClockScreen()

                "Alarms" -> AlarmScreen()
            }

            // Keep timer running across tab changes.
            MyClockTimerPanel(
                visible = selectedTab == "Timers"
            )

            // Keep stopwatch running across tab changes.
            MyClockStopwatchPanel(
                visible = selectedTab == "Stopwatch"
            )
        }
    }
}

// =====================================================
// WORLD CLOCK
// =====================================================

private data class WorldCity(
    val name: String,
    val zoneId: String
)

private fun availableWorldCities(): List<WorldCity> {
    return TimeZone.getAvailableIDs()
        .filter { zoneId ->
            zoneId.contains("/") &&
                    !zoneId.startsWith("Etc/") &&
                    !zoneId.startsWith("SystemV/") &&
                    !zoneId.startsWith("posix/") &&
                    !zoneId.startsWith("right/")
        }
        .map { zoneId ->
            WorldCity(
                name = zoneId
                    .substringAfterLast("/")
                    .replace("_", " "),
                zoneId = zoneId
            )
        }
        .sortedBy {
            it.name.lowercase(Locale.ROOT)
        }
}

@Composable
private fun WorldClockScreen() {
    val context = LocalContext.current

    val preferences = remember(context) {
        context.getSharedPreferences(
            "myclock_world_clock",
            Context.MODE_PRIVATE
        )
    }

    val allCities = remember {
        availableWorldCities()
    }

    var selectedZones by remember(preferences) {
        val saved = preferences.getString(
            "selected_zones",
            null
        )

        val initialZones = if (saved == null) {
            listOf(
                "Asia/Kuala_Lumpur",
                "Asia/Tokyo",
                "Europe/London",
                "America/New_York"
            )
        } else {
            saved.split("|").filter {
                it.isNotBlank()
            }
        }

        mutableStateOf(
            initialZones.filter { zone ->
                allCities.any {
                    it.zoneId == zone
                }
            }.distinct()
        )
    }

    var currentTime by remember {
        mutableStateOf(Date())
    }

    var isEditing by rememberSaveable {
        mutableStateOf(false)
    }

    var showAddDialog by rememberSaveable {
        mutableStateOf(false)
    }

    var searchText by rememberSaveable {
        mutableStateOf("")
    }

    fun saveCities(updatedZones: List<String>) {
        selectedZones = updatedZones.distinct()

        preferences.edit()
            .putString(
                "selected_zones",
                selectedZones.joinToString("|")
            )
            .apply()
    }

    LaunchedEffect(Unit) {
        while (true) {
            currentTime = Date()
            delay(1000L)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = {
                    isEditing = !isEditing
                }
            ) {
                Text(
                    text = if (isEditing) "Done" else "Edit"
                )
            }

            OutlinedButton(
                onClick = {
                    searchText = ""
                    showAddDialog = true
                }
            ) {
                Text(
                    text = "+",
                    fontSize = 26.sp
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Text(
            text = "World Clock",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(6.dp))

        Text(
            text = if (isEditing) {
                "Tekan Padam untuk membuang bandar."
            } else {
                "Format 24 jam"
            },
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(16.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (selectedZones.isEmpty()) {
                Text(
                    text = "Belum ada bandar. Tekan + untuk tambah bandar.",
                    modifier = Modifier.padding(vertical = 24.dp)
                )
            }

            selectedZones.forEach { zoneId ->
                key(zoneId) {
                    val cityName = allCities
                        .firstOrNull {
                            it.zoneId == zoneId
                        }
                        ?.name
                        ?: zoneId

                    WorldCityCard(
                        cityName = cityName,
                        zoneId = zoneId,
                        currentTime = currentTime,
                        isEditing = isEditing,
                        onDelete = {
                            saveCities(
                                selectedZones.filterNot {
                                    it == zoneId
                                }
                            )
                        }
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }

    if (showAddDialog) {
        val query = searchText.trim()

        val matchingCities = allCities.filter { city ->
            city.zoneId !in selectedZones &&
                    (
                            city.name.contains(
                                query,
                                ignoreCase = true
                            ) ||
                                    city.zoneId.contains(
                                        query,
                                        ignoreCase = true
                                    )
                            )
        }

        AlertDialog(
            onDismissRequest = {
                showAddDialog = false
            },
            title = {
                Text("Tambah bandar")
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = searchText,
                        onValueChange = {
                            searchText = it
                        },
                        label = {
                            Text("Cari bandar")
                        },
                        placeholder = {
                            Text("Contoh: Jakarta")
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text(
                        text = "Tekan nama bandar untuk tambah.",
                        fontSize = 12.sp
                    )

                    if (matchingCities.isEmpty()) {
                        Text(
                            text = "Tiada hasil. Cuba nama bandar lain."
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 320.dp)
                        ) {
                            items(
                                items = matchingCities,
                                key = {
                                    it.zoneId
                                }
                            ) { city ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            saveCities(
                                                selectedZones + city.zoneId
                                            )
                                            showAddDialog = false
                                            searchText = ""
                                        }
                                        .padding(
                                            horizontal = 8.dp,
                                            vertical = 12.dp
                                        )
                                ) {
                                    Text(
                                        text = city.name,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Medium
                                    )

                                    Text(
                                        text = city.zoneId,
                                        fontSize = 12.sp,
                                        color = MaterialTheme
                                            .colorScheme
                                            .onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showAddDialog = false
                    }
                ) {
                    Text("Tutup")
                }
            }
        )
    }
}

@Composable
private fun WorldCityCard(
    cityName: String,
    zoneId: String,
    currentTime: Date,
    isEditing: Boolean,
    onDelete: () -> Unit
) {
    val timeFormat = remember(zoneId) {
        SimpleDateFormat(
            "HH:mm:ss",
            Locale.US
        ).apply {
            timeZone = TimeZone.getTimeZone(zoneId)
        }
    }

    val dateFormat = remember(zoneId) {
        SimpleDateFormat(
            "d MMMM yyyy",
            Locale.forLanguageTag("ms-MY")
        ).apply {
            timeZone = TimeZone.getTimeZone(zoneId)
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = cityName,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )

                if (isEditing) {
                    TextButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Padam")
                    }
                }
            }

            Spacer(Modifier.height(6.dp))

            Text(
                text = timeFormat.format(currentTime),
                fontSize = 34.sp,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(Modifier.height(4.dp))

            Text(
                text = dateFormat.format(currentTime),
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// =====================================================
// TIMERS
// =====================================================

@Composable
private fun MyClockTimerPanel(visible: Boolean) {
    val context = LocalContext.current

    var hours by rememberSaveable {
        mutableStateOf("0")
    }

    var minutes by rememberSaveable {
        mutableStateOf("0")
    }

    var seconds by rememberSaveable {
        mutableStateOf("10")
    }

    var totalMillis by rememberSaveable {
        mutableStateOf(0L)
    }

    var remainingMillis by rememberSaveable {
        mutableStateOf(0L)
    }

    var deadlineMillis by rememberSaveable {
        mutableStateOf(0L)
    }

    var running by rememberSaveable {
        mutableStateOf(false)
    }

    var finished by rememberSaveable {
        mutableStateOf(false)
    }

    var error by rememberSaveable {
        mutableStateOf("")
    }

    val hasTimer = totalMillis > 0L

    val inputSeconds =
        (hours.toLongOrNull() ?: 0L) * 3600L +
                (minutes.toLongOrNull() ?: 0L) * 60L +
                (seconds.toLongOrNull() ?: 0L)

    LaunchedEffect(running, deadlineMillis) {
        if (running) {
            while (true) {
                remainingMillis = (
                        deadlineMillis - SystemClock.elapsedRealtime()
                        ).coerceAtLeast(0L)

                if (remainingMillis == 0L) {
                    running = false
                    finished = true
                    break
                }

                delay(100L)
            }
        }
    }

    DisposableEffect(finished) {
        val ringtone = if (finished) {
            runCatching {
                val uri = RingtoneManager.getDefaultUri(
                    RingtoneManager.TYPE_ALARM
                ) ?: RingtoneManager.getDefaultUri(
                    RingtoneManager.TYPE_NOTIFICATION
                )

                RingtoneManager.getRingtone(
                    context,
                    uri
                )?.also {
                    it.play()
                }
            }.getOrNull()
        } else {
            null
        }

        onDispose {
            runCatching {
                ringtone?.stop()
            }
        }
    }

    if (finished) {
        AlertDialog(
            onDismissRequest = {
                finished = false
            },
            title = {
                Text("Masa tamat!")
            },
            text = {
                Text("Timer MyClock anda telah selesai.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        finished = false
                    }
                ) {
                    Text("Hentikan bunyi")
                }
            }
        )
    }

    if (!visible) return

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Timers",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.Start)
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "Tetapkan masa kira detik",
            modifier = Modifier.align(Alignment.Start),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(32.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MyClockTimerInput(
                value = hours,
                onChange = {
                    hours = it
                    error = ""
                },
                label = "Jam",
                maximum = 99,
                enabled = !hasTimer,
                modifier = Modifier.weight(1f)
            )

            MyClockTimerInput(
                value = minutes,
                onChange = {
                    minutes = it
                    error = ""
                },
                label = "Minit",
                maximum = 59,
                enabled = !hasTimer,
                modifier = Modifier.weight(1f)
            )

            MyClockTimerInput(
                value = seconds,
                onChange = {
                    seconds = it
                    error = ""
                },
                label = "Saat",
                maximum = 59,
                enabled = !hasTimer,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(40.dp))

        val displayedSeconds = if (hasTimer) {
            (remainingMillis + 999L) / 1000L
        } else {
            inputSeconds
        }

        Text(
            text = myClockTimerText(displayedSeconds),
            fontSize = 44.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1
        )

        Spacer(Modifier.height(12.dp))

        Text(
            text = when {
                running -> "Timer sedang berjalan"
                hasTimer && remainingMillis > 0L -> "Timer dijeda"
                hasTimer -> "Timer selesai"
                else -> "Sedia untuk bermula"
            }
        )

        if (error.isNotBlank()) {
            Spacer(Modifier.height(12.dp))

            Text(
                text = error,
                color = MaterialTheme.colorScheme.error
            )
        }

        Spacer(Modifier.height(32.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = {
                    when {
                        running -> {
                            remainingMillis = (
                                    deadlineMillis -
                                            SystemClock.elapsedRealtime()
                                    ).coerceAtLeast(0L)

                            running = false

                            if (remainingMillis == 0L) {
                                finished = true
                            }
                        }

                        hasTimer && remainingMillis > 0L -> {
                            deadlineMillis =
                                SystemClock.elapsedRealtime() +
                                        remainingMillis

                            running = true
                        }

                        else -> {
                            val duration = if (hasTimer) {
                                totalMillis
                            } else {
                                inputSeconds * 1000L
                            }

                            if (duration <= 0L) {
                                error = "Masukkan masa lebih daripada 0 saat."
                            } else {
                                error = ""
                                finished = false
                                totalMillis = duration
                                remainingMillis = duration
                                deadlineMillis =
                                    SystemClock.elapsedRealtime() + duration
                                running = true
                            }
                        }
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
            ) {
                Text(
                    text = when {
                        running -> "Pause"
                        hasTimer && remainingMillis > 0L -> "Resume"
                        hasTimer -> "Restart"
                        else -> "Start"
                    }
                )
            }

            OutlinedButton(
                onClick = {
                    running = false
                    finished = false
                    totalMillis = 0L
                    remainingMillis = 0L
                    deadlineMillis = 0L
                    error = ""
                },
                enabled = hasTimer,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
            ) {
                Text("Reset")
            }
        }

        Spacer(Modifier.height(24.dp))

        Text(
            text = "Tekan Reset untuk menukar tempoh timer.",
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(12.dp))

        Text(
            text = "Biarkan aplikasi terbuka semasa menggunakan timer.",
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun MyClockTimerInput(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    maximum: Int,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = { newValue ->
            if (
                newValue.length <= 2 &&
                newValue.all { it in '0'..'9' }
            ) {
                val number = newValue.toIntOrNull()

                if (
                    newValue.isEmpty() ||
                    (number != null && number <= maximum)
                ) {
                    onChange(newValue)
                }
            }
        },
        label = {
            Text(label)
        },
        enabled = enabled,
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Number
        ),
        modifier = modifier
    )
}

private fun myClockTimerText(totalSeconds: Long): String {
    return String.format(
        Locale.US,
        "%02d:%02d:%02d",
        totalSeconds / 3600L,
        (totalSeconds % 3600L) / 60L,
        totalSeconds % 60L
    )
}

// =====================================================
// STOPWATCH
// =====================================================

@Composable
private fun MyClockStopwatchPanel(visible: Boolean) {
    var running by rememberSaveable {
        mutableStateOf(false)
    }

    var accumulatedMillis by rememberSaveable {
        mutableStateOf(0L)
    }

    var startedAtMillis by rememberSaveable {
        mutableStateOf(0L)
    }

    var savedLaps by rememberSaveable {
        mutableStateOf("")
    }

    var displayedMillis by remember {
        mutableStateOf(accumulatedMillis)
    }

    val lapTotals = remember(savedLaps) {
        savedLaps.split("|")
            .mapNotNull {
                it.toLongOrNull()
            }
    }

    fun currentElapsedMillis(): Long {
        return if (running) {
            accumulatedMillis +
                    (
                            SystemClock.elapsedRealtime() - startedAtMillis
                            ).coerceAtLeast(0L)
        } else {
            accumulatedMillis
        }
    }

    LaunchedEffect(
        running,
        startedAtMillis,
        accumulatedMillis
    ) {
        if (running) {
            while (true) {
                displayedMillis = currentElapsedMillis()
                delay(32L)
            }
        } else {
            displayedMillis = accumulatedMillis
        }
    }

    if (!visible) return

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Stopwatch",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.Start)
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "Catat masa dan pusingan",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.Start)
        )

        Spacer(Modifier.height(36.dp))

        Text(
            text = myClockStopwatchText(displayedMillis),
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "Jam : Minit : Saat . Perseratus saat",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(12.dp))

        Text(
            text = when {
                running -> "Stopwatch sedang berjalan"
                accumulatedMillis > 0L -> "Stopwatch dijeda"
                else -> "Sedia untuk bermula"
            },
            fontSize = 14.sp
        )

        Spacer(Modifier.height(28.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = {
                    if (running) {
                        accumulatedMillis = currentElapsedMillis()
                        displayedMillis = accumulatedMillis
                        running = false
                    } else {
                        startedAtMillis = SystemClock.elapsedRealtime()
                        running = true
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
            ) {
                Text(
                    text = when {
                        running -> "Pause"
                        accumulatedMillis > 0L -> "Resume"
                        else -> "Start"
                    }
                )
            }

            OutlinedButton(
                onClick = {
                    if (running) {
                        val lapTotal = currentElapsedMillis()

                        savedLaps = if (savedLaps.isEmpty()) {
                            lapTotal.toString()
                        } else {
                            "$savedLaps|$lapTotal"
                        }
                    } else {
                        accumulatedMillis = 0L
                        startedAtMillis = 0L
                        displayedMillis = 0L
                        savedLaps = ""
                    }
                },
                enabled = running ||
                        accumulatedMillis > 0L ||
                        lapTotals.isNotEmpty(),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
            ) {
                Text(
                    text = if (running) "Lap" else "Reset"
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        if (lapTotals.isEmpty()) {
            Text(
                text = "Tekan Lap semasa stopwatch berjalan untuk merekod pusingan.",
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Lap",
                    modifier = Modifier.weight(0.6f),
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )

                Text(
                    text = "Tempoh",
                    modifier = Modifier.weight(1f),
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    textAlign = TextAlign.End
                )

                Text(
                    text = "Jumlah",
                    modifier = Modifier.weight(1f),
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    textAlign = TextAlign.End
                )
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    items = lapTotals.indices.reversed().toList(),
                    key = {
                        it
                    }
                ) { index ->
                    val total = lapTotals[index]

                    val previousTotal = if (index == 0) {
                        0L
                    } else {
                        lapTotals[index - 1]
                    }

                    val lapDuration = total - previousTotal

                    Card(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(
                                horizontal = 10.dp,
                                vertical = 14.dp
                            ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${index + 1}",
                                modifier = Modifier.weight(0.6f),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )

                            Text(
                                text = myClockStopwatchText(lapDuration),
                                modifier = Modifier.weight(1f),
                                fontSize = 11.sp,
                                textAlign = TextAlign.End
                            )

                            Text(
                                text = myClockStopwatchText(total),
                                modifier = Modifier.weight(1f),
                                fontSize = 11.sp,
                                textAlign = TextAlign.End,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun myClockStopwatchText(milliseconds: Long): String {
    val safeMillis = milliseconds.coerceAtLeast(0L)

    val hours = safeMillis / 3_600_000L
    val minutes = (safeMillis / 60_000L) % 60L
    val seconds = (safeMillis / 1000L) % 60L
    val hundredths = (safeMillis / 10L) % 100L

    return String.format(
        Locale.US,
        "%02d:%02d:%02d.%02d",
        hours,
        minutes,
        seconds,
        hundredths
    )
}