package com.jarvisai.assistant.ui.drawer

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvisai.assistant.antigravity.AntigravityArtifact
import com.jarvisai.assistant.antigravity.AntigravityArtifactStatus
import com.jarvisai.assistant.antigravity.AntigravityArtifactType
import com.jarvisai.assistant.ui.antigravity.AntigravityPalette

data class GeneratedArtifact(
    val id: String,
    val title: String,
    val fileType: String, // code, markdown, diff, svg
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)

private enum class ArtifactHubFilter(val label: String) {
    ALL("All"),
    PLANS("Plans"),
    TASKS("Tasks"),
    DIFFS("Diffs"),
    CODE("Code")
}

/**
 * Slide-out Artifacts Hub displaying structured Antigravity 2.0 Artifacts
 * (`implementation_plan.md`, `task.md`, `walkthrough.md`, `diff`) + code snippets.
 */
@Composable
fun ArtifactsDrawerContent(
    artifacts: List<GeneratedArtifact>,
    structuredArtifacts: List<AntigravityArtifact> = emptyList(),
    onApprovePlan: ((AntigravityArtifact) -> Unit)? = null,
    onOpenInIde: (GeneratedArtifact) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current
    var activeFilter by remember { mutableStateOf(ArtifactHubFilter.ALL) }

    val filteredStructured = remember(structuredArtifacts, activeFilter) {
        when (activeFilter) {
            ArtifactHubFilter.ALL -> structuredArtifacts
            ArtifactHubFilter.PLANS -> structuredArtifacts.filter {
                it.type == AntigravityArtifactType.IMPLEMENTATION_PLAN || it.type == AntigravityArtifactType.WALKTHROUGH
            }
            ArtifactHubFilter.TASKS -> structuredArtifacts.filter { it.type == AntigravityArtifactType.TASK_LIST }
            ArtifactHubFilter.DIFFS -> structuredArtifacts.filter { it.type == AntigravityArtifactType.CODE_DIFF }
            ArtifactHubFilter.CODE -> structuredArtifacts.filter { it.type == AntigravityArtifactType.CODE_SNIPPET }
        }
    }

    val filteredLegacy = remember(artifacts, structuredArtifacts, activeFilter) {
        if (structuredArtifacts.isNotEmpty() && activeFilter != ArtifactHubFilter.CODE && activeFilter != ArtifactHubFilter.ALL) {
            emptyList()
        } else {
            val structuredBodies = structuredArtifacts.map { it.content.take(80) }.toSet()
            artifacts.filterNot { it.content.take(80) in structuredBodies }
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxHeight()
            .width(330.dp)
            .testTag("artifacts_drawer_content"),
        color = AntigravityPalette.Canvas,
        tonalElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = null,
                        tint = AntigravityPalette.ElectricBlue,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Artifacts Hub",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = AntigravityPalette.TextPrimary
                        )
                        Text(
                            text = "Plans • Tasks • Diffs • Walkthroughs",
                            fontSize = 10.5.sp,
                            color = AntigravityPalette.TextSecondary
                        )
                    }
                }
                IconButton(onClick = onClose) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = Color.LightGray)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Typed Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ArtifactHubFilter.entries.forEach { filter ->
                    val selected = filter == activeFilter
                    Surface(
                        onClick = { activeFilter = filter },
                        shape = RoundedCornerShape(9.dp),
                        color = if (selected) AntigravityPalette.ElectricBlue.copy(alpha = 0.2f) else AntigravityPalette.SurfaceCard,
                        border = BorderStroke(
                            1.dp,
                            if (selected) AntigravityPalette.ElectricBlue else AntigravityPalette.BorderHairline
                        )
                    ) {
                        Text(
                            text = filter.label,
                            color = if (selected) AntigravityPalette.ElectricBlue else AntigravityPalette.TextSecondary,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (filteredStructured.isEmpty() && filteredLegacy.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No artifacts yet in this category.\nTry /plan or ask Antigravity to inspect or edit files.",
                        fontSize = 12.sp,
                        color = AntigravityPalette.TextMuted
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredStructured, key = { it.id }) { artifact ->
                        val badgeColor = when (artifact.type) {
                            AntigravityArtifactType.TASK_LIST -> AntigravityPalette.ElectricBlue
                            AntigravityArtifactType.IMPLEMENTATION_PLAN -> AntigravityPalette.IndigoAccent
                            AntigravityArtifactType.WALKTHROUGH -> AntigravityPalette.VerifiedGreen
                            AntigravityArtifactType.CODE_DIFF -> AntigravityPalette.ReviewAmber
                            AntigravityArtifactType.CODE_SNIPPET -> Color(0xFF00E5FF)
                        }
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = AntigravityPalette.SurfaceCard,
                            border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.35f))
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = artifact.title,
                                        fontWeight = FontWeight.Bold,
                                        color = AntigravityPalette.TextPrimary,
                                        fontSize = 13.sp,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(5.dp),
                                        color = badgeColor.copy(alpha = 0.16f)
                                    ) {
                                        Text(
                                            text = artifact.type.badgeLabel,
                                            fontSize = 9.sp,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = badgeColor,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                Text(
                                    text = if (artifact.content.length > 160) artifact.content.take(160) + "..." else artifact.content,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = AntigravityPalette.TextSecondary
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (
                                        artifact.type == AntigravityArtifactType.IMPLEMENTATION_PLAN &&
                                        artifact.status == AntigravityArtifactStatus.PENDING_REVIEW &&
                                        onApprovePlan != null
                                    ) {
                                        Button(
                                            onClick = {
                                                onApprovePlan(artifact)
                                                onClose()
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = AntigravityPalette.ElectricBlue),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Text("Proceed", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    } else {
                                        Spacer(modifier = Modifier.width(1.dp))
                                    }

                                    Row {
                                        IconButton(onClick = {
                                            clipboard.setText(AnnotatedString(artifact.content))
                                            Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                                        }) {
                                            Icon(
                                                imageVector = Icons.Default.ContentCopy,
                                                contentDescription = "Copy",
                                                tint = Color.LightGray,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                        IconButton(onClick = {
                                            onOpenInIde(
                                                GeneratedArtifact(
                                                    id = artifact.id,
                                                    title = artifact.type.fileName,
                                                    fileType = artifact.language,
                                                    content = artifact.content,
                                                    timestamp = artifact.timestampMs
                                                )
                                            )
                                        }) {
                                            Icon(
                                                imageVector = Icons.Default.Code,
                                                contentDescription = "Open in IDE",
                                                tint = AntigravityPalette.ElectricBlue,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    items(filteredLegacy, key = { it.id }) { artifact ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            color = AntigravityPalette.SurfaceCard,
                            border = BorderStroke(1.dp, AntigravityPalette.BorderHairline)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = artifact.title,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = Color(0xFF00E5FF).copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            text = artifact.fileType.uppercase(),
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF00E5FF),
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                Text(
                                    text = if (artifact.content.length > 120) artifact.content.take(120) + "..." else artifact.content,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = Color(0xFF94A3B8)
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    IconButton(onClick = {
                                        clipboard.setText(AnnotatedString(artifact.content))
                                        Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                                    }) {
                                        Icon(
                                            imageVector = Icons.Default.ContentCopy,
                                            contentDescription = "Copy",
                                            tint = Color.LightGray,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    IconButton(onClick = { onOpenInIde(artifact) }) {
                                        Icon(
                                            imageVector = Icons.Default.Code,
                                            contentDescription = "Open in IDE",
                                            tint = AntigravityPalette.ElectricBlue,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
