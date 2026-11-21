package com.jarvisai.assistant.crash

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

/**
 * Isolated `:crash` process recovery UI shown when Agentra encounters an unhandled exception.
 */
class CrashActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val report = runCatching { CrashHandler.reportFile(this).readText() }.getOrNull()
            ?.takeIf { it.isNotBlank() } ?: "No crash report was saved."
        val errorLine = report.substringAfter("\n\n", report)
            .lineSequence()
            .firstOrNull { it.isNotBlank() }
            .orEmpty()

        setContent {
            val dark = isSystemInDarkTheme()
            MaterialTheme(
                colorScheme = if (dark) {
                    darkColorScheme(
                        primary = AgentraCoral,
                        onPrimary = Color.White,
                        background = Color(0xFF0A0E17),
                        surface = Color(0xFF111827)
                    )
                } else {
                    lightColorScheme(
                        primary = AgentraCoral,
                        onPrimary = Color.White
                    )
                }
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    CrashScreen(
                        errorLine = errorLine,
                        report = report,
                        onCopy = {
                            copyReport(report)
                            toast("Crash report copied to clipboard")
                        },
                        onShare = { shareReport(report) },
                        onRestart = { restartApp() },
                        onClose = { finishAndRemoveTask() }
                    )
                }
            }
        }
    }

    private fun copyReport(text: String) {
        runCatching {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("Agentra crash report", text))
        }
    }

    private fun shareReport(text: String) {
        runCatching {
            startActivity(
                Intent.createChooser(
                    Intent(Intent.ACTION_SEND)
                        .setType("text/plain")
                        .putExtra(Intent.EXTRA_SUBJECT, "Agentra Crash Report")
                        .putExtra(Intent.EXTRA_TEXT, text),
                    "Share crash report"
                )
            )
        }.onFailure { toast("No app available to share crash report") }
    }

    private fun restartApp() {
        packageManager.getLaunchIntentForPackage(packageName)?.let {
            startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        }
        finishAndRemoveTask()
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
    }
}

private val AgentraCoral = Color(0xFFD97757)
private val AgentraCyan = Color(0xFF38BDF8)

@Composable
private fun CrashScreen(
    errorLine: String,
    report: String,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onRestart: () -> Unit,
    onClose: () -> Unit
) {
    var showDetails by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 600.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(8.dp))
            DizzyAgentraMascot(Modifier.size(164.dp))
            Spacer(Modifier.height(8.dp))

            Text(
                text = "Agentra hit an unexpected error",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Your workspace, files, and local Linux rootfs are safe. Copy or share the diagnostic trace, or restart Agentra.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            if (errorLine.isNotBlank()) {
                Spacer(Modifier.height(16.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.28f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.45f))
                ) {
                    Text(
                        text = errorLine,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Medium
                        ),
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            Button(
                onClick = onRestart,
                colors = ButtonDefaults.buttonColors(containerColor = AgentraCoral, contentColor = Color.White),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .testTag("crash_restart_button")
            ) {
                Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Restart Agentra", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onCopy,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("crash_copy_button")
                ) {
                    Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Copy Log", fontWeight = FontWeight.SemiBold)
                }

                OutlinedButton(
                    onClick = onShare,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("crash_share_button")
                ) {
                    Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Share", fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(Modifier.height(8.dp))

            TextButton(
                onClick = { showDetails = !showDetails },
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("crash_toggle_details_button")
            ) {
                Icon(
                    imageVector = if (showDetails) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(if (showDetails) "Hide Technical Stacktrace" else "Show Technical Stacktrace")
            }

            AnimatedVisibility(visible = showDetails) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Text(
                        text = report,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.5.sp,
                            lineHeight = 15.sp
                        ),
                        modifier = Modifier
                            .background(Color.Transparent)
                            .padding(12.dp)
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            TextButton(
                onClick = onClose,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("crash_close_button")
            ) {
                Text("Close App", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun DizzyAgentraMascot(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "crash_orbit")
    val orbitAngle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orbit"
    )

    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height * 0.56f
        val r = size.minDimension * 0.28f

        // Halo glow
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(AgentraCoral.copy(alpha = 0.25f), Color.Transparent),
                center = Offset(cx, cy),
                radius = r * 1.9f
            ),
            radius = r * 1.9f,
            center = Offset(cx, cy)
        )

        // Agent core body
        drawCircle(
            brush = Brush.linearGradient(listOf(AgentraCoral, Color(0xFFE05260))),
            radius = r,
            center = Offset(cx, cy)
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.22f),
            radius = r,
            center = Offset(cx, cy),
            style = Stroke(width = 3f)
        )

        // X eyes
        val eyeY = cy - r * 0.14f
        val eyeOffsetX = r * 0.34f
        val eyeSize = r * 0.14f
        listOf(cx - eyeOffsetX, cx + eyeOffsetX).forEach { ex ->
            drawLine(
                color = Color.White,
                start = Offset(ex - eyeSize, eyeY - eyeSize),
                end = Offset(ex + eyeSize, eyeY + eyeSize),
                strokeWidth = 5f,
                cap = StrokeCap.Round
            )
            drawLine(
                color = Color.White,
                start = Offset(ex + eyeSize, eyeY - eyeSize),
                end = Offset(ex - eyeSize, eyeY + eyeSize),
                strokeWidth = 5f,
                cap = StrokeCap.Round
            )
        }

        // Wavy mouth
        val mouthPath = Path().apply {
            val my = cy + r * 0.34f
            moveTo(cx - r * 0.28f, my)
            cubicTo(
                cx - r * 0.1f, my - r * 0.12f,
                cx + r * 0.1f, my + r * 0.12f,
                cx + r * 0.28f, my
            )
        }
        drawPath(
            path = mouthPath,
            color = Color.White,
            style = Stroke(width = 4.5f, cap = StrokeCap.Round)
        )

        // Orbiting stars above head
        for (i in 0 until 3) {
            val angleRad = Math.toRadians((orbitAngle + i * 120f).toDouble())
            val sx = cx + cos(angleRad).toFloat() * r * 0.9f
            val sy = cy - r * 1.25f + sin(angleRad).toFloat() * r * 0.22f
            drawStar(
                x = sx,
                y = sy,
                radius = r * (0.12f + 0.03f * sin(angleRad).toFloat()),
                color = if (i % 2 == 0) Color(0xFFFFC94D) else AgentraCyan
            )
        }
    }
}

private fun DrawScope.drawStar(x: Float, y: Float, radius: Float, color: Color) {
    val path = Path()
    for (i in 0 until 10) {
        val rr = if (i % 2 == 0) radius else radius * 0.45f
        val a = Math.toRadians(-90.0 + i * 36.0)
        val px = x + cos(a).toFloat() * rr
        val py = y + sin(a).toFloat() * rr
        if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
    }
    path.close()
    drawPath(path, color)
}
