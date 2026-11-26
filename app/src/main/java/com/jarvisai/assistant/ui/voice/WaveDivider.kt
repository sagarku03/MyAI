package com.jarvisai.assistant.ui.voice

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Muted wavy-line divider with centered label and optional clickable action (e.g., Voice call marker).
 */
@Composable
fun WaveDivider(
    label: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    tint: Color? = null,
    onClick: (() -> Unit)? = null
) {
    val muted = Color.White.copy(alpha = 0.42f)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        WaveLine(muted, Modifier.weight(1f))
        if (icon != null || onClick != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                if (icon != null) {
                    Icon(icon, null, tint = tint ?: muted, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(5.dp))
                }
                Text(
                    text = label,
                    fontSize = 12.sp,
                    color = tint ?: muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 260.dp)
                )
            }
            WaveLine(muted, Modifier.weight(1f))
            return@Row
        }
        Text(
            text = label,
            fontSize = 12.sp,
            color = muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 260.dp)
        )
        WaveLine(muted, Modifier.weight(1f))
    }
}

@Composable
private fun WaveLine(color: Color, modifier: Modifier) {
    Canvas(modifier.widthIn(min = 32.dp).height(7.dp)) {
        val period = 14.dp.toPx()
        val amp = size.height * 0.42f
        val midY = size.height / 2f
        val path = Path().apply {
            moveTo(0f, midY)
            var x = 0f
            while (x < size.width + period) {
                quadraticTo(x + period * 0.25f, midY - amp, x + period * 0.5f, midY)
                quadraticTo(x + period * 0.75f, midY + amp, x + period, midY)
                x += period
            }
        }
        clipRect {
            drawPath(path, color = color, style = Stroke(width = 1.dp.toPx()))
        }
    }
}
