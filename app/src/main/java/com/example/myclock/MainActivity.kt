package com.example.myclock

import android.graphics.Paint
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myclock.ui.theme.MyClockTheme
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.cos
import kotlin.math.sin

// =====================================================
// MAIN ACTIVITY
// =====================================================

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        setContent {
            MyClockTheme {
                ClockApp()
            }
        }
    }
}

// =====================================================
// CLOCK SCREEN
// =====================================================

@Composable
fun ClockScreen(
    showAnalog: Boolean,
    is24Hour: Boolean,
    onToggleAnalog: () -> Unit,
    onToggleTimeFormat: () -> Unit
) {
    var currentTime by remember {
        mutableStateOf(Date())
    }

    val timeFormat = remember(is24Hour) {
        SimpleDateFormat(
            if (is24Hour) "HH:mm:ss" else "hh:mm:ss a",
            Locale.US
        ).apply {
            timeZone = TimeZone.getTimeZone(
                "Asia/Kuala_Lumpur"
            )
        }
    }

    val dateFormat = remember {
        SimpleDateFormat(
            "EEEE, d MMMM yyyy",
            Locale.forLanguageTag("ms-MY")
        ).apply {
            timeZone = TimeZone.getTimeZone(
                "Asia/Kuala_Lumpur"
            )
        }
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
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(
                id = R.drawable.myclock_logo
            ),
            contentDescription = "Logo MyClock",
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp)
        )

        Spacer(
            modifier = Modifier.height(24.dp)
        )

        if (showAnalog) {
            AnalogClock(
                currentTime = currentTime
            )
        } else {
            Text(
                text = timeFormat.format(currentTime),
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }

        Spacer(
            modifier = Modifier.height(20.dp)
        )

        Text(
            text = dateFormat.format(currentTime),
            fontSize = 18.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        Spacer(
            modifier = Modifier.height(8.dp)
        )

        Text(
            text = "Waktu Malaysia",
            fontSize = 16.sp
        )

        Spacer(
            modifier = Modifier.height(24.dp)
        )

        Button(
            onClick = onToggleAnalog
        ) {
            Text(
                text = if (showAnalog) {
                    "Papar jam digital"
                } else {
                    "Papar jam analog"
                }
            )
        }

        if (!showAnalog) {
            Spacer(
                modifier = Modifier.height(8.dp)
            )

            OutlinedButton(
                onClick = onToggleTimeFormat
            ) {
                Text(
                    text = if (is24Hour) {
                        "Tukar ke format 12 jam"
                    } else {
                        "Tukar ke format 24 jam"
                    }
                )
            }
        }
    }
}

// =====================================================
// ANALOG CLOCK
// =====================================================

@Composable
private fun AnalogClock(currentTime: Date) {
    val calendar = Calendar.getInstance(
        TimeZone.getTimeZone("Asia/Kuala_Lumpur")
    ).apply {
        time = currentTime
    }

    val hour = calendar.get(Calendar.HOUR)
    val minute = calendar.get(Calendar.MINUTE)
    val second = calendar.get(Calendar.SECOND)

    val faceColor = MaterialTheme.colorScheme.surface
    val dialColor = MaterialTheme.colorScheme.onSurface
    val handColor = MaterialTheme.colorScheme.primary
    val secondColor = Color(0xFFD32F2F)

    val numberPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG)
    }

    Canvas(
        modifier = Modifier
            .widthIn(max = 300.dp)
            .fillMaxWidth()
            .aspectRatio(1f)
    ) {
        val radius = size.minDimension * 0.46f
        val middle = center

        // Convert an angle into a position on the clock.
        fun point(
            angle: Double,
            length: Float
        ): Offset {
            val radians = Math.toRadians(angle - 90.0)

            return Offset(
                x = middle.x +
                        cos(radians).toFloat() * length,
                y = middle.y +
                        sin(radians).toFloat() * length
            )
        }

        // Clock face.
        drawCircle(
            color = faceColor,
            radius = radius,
            center = middle
        )

        // Clock border.
        drawCircle(
            color = handColor,
            radius = radius,
            center = middle,
            style = Stroke(
                width = radius * 0.025f
            )
        )

        // Minute and hour markers.
        for (tick in 0 until 60) {
            val majorTick = tick % 5 == 0

            val innerRadius = if (majorTick) {
                radius * 0.86f
            } else {
                radius * 0.91f
            }

            val tickWidth = if (majorTick) {
                radius * 0.025f
            } else {
                radius * 0.01f
            }

            drawLine(
                color = dialColor,
                start = point(
                    angle = tick * 6.0,
                    length = innerRadius
                ),
                end = point(
                    angle = tick * 6.0,
                    length = radius * 0.96f
                ),
                strokeWidth = tickWidth,
                cap = StrokeCap.Round
            )
        }

        // Clock numbers.
        numberPaint.color = dialColor.toArgb()
        numberPaint.textSize = radius * 0.17f
        numberPaint.textAlign = Paint.Align.CENTER

        val textOffset = -(
                numberPaint.fontMetrics.ascent +
                        numberPaint.fontMetrics.descent
                ) / 2f

        for (number in 1..12) {
            val position = point(
                angle = number * 30.0,
                length = radius * 0.72f
            )

            drawContext.canvas.nativeCanvas.drawText(
                number.toString(),
                position.x,
                position.y + textOffset,
                numberPaint
            )
        }

        // Draw a clock hand.
        fun drawHand(
            angle: Double,
            length: Float,
            color: Color,
            width: Float
        ) {
            drawLine(
                color = color,
                start = middle,
                end = point(
                    angle = angle,
                    length = radius * length
                ),
                strokeWidth = radius * width,
                cap = StrokeCap.Round
            )
        }

        // Hour hand.
        drawHand(
            angle = hour * 30.0 +
                    minute * 0.5 +
                    second / 120.0,
            length = 0.48f,
            color = dialColor,
            width = 0.055f
        )

        // Minute hand.
        drawHand(
            angle = minute * 6.0 +
                    second * 0.1,
            length = 0.66f,
            color = handColor,
            width = 0.035f
        )

        // Second hand.
        drawHand(
            angle = second * 6.0,
            length = 0.82f,
            color = secondColor,
            width = 0.015f
        )

        // Centre point.
        drawCircle(
            color = secondColor,
            radius = radius * 0.045f,
            center = middle
        )
    }
}