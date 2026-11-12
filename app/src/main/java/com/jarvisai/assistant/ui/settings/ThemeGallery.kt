package com.jarvisai.assistant.ui.settings

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvisai.assistant.theme.ThemeTerminal
import com.jarvisai.assistant.ui.components.bouncyOverscroll

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeGallery(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var activePaletteName by remember {
        mutableStateOf(ThemeTerminal.currentPalette.name)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "UI-UX Pro Max & PTY Theme Gallery",
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = Color(0xFFF8FAFC),
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Text(
                            text = "Live 16-Color ANSI Terminal & Scientific Markdown Palettes",
                            style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF38BDF8))
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("theme_gallery_back")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color(0xFFF8FAFC)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0B0F19))
            )
        },
        containerColor = Color(0xFF0B0F19)
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .bouncyOverscroll(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            items(ThemeTerminal.ALL_PALETTES, key = { it.name }) { palette ->
                val isSelected = activePaletteName == palette.name
                val bgColor = remember(palette.background) {
                    Color(android.graphics.Color.parseColor(palette.background))
                }
                val cursorColor = remember(palette.cursor) {
                    Color(android.graphics.Color.parseColor(palette.cursor))
                }

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 64.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable {
                            ThemeTerminal.applyPalette(palette)
                            activePaletteName = palette.name
                            context.getSharedPreferences("jarvis_theme_prefs", Context.MODE_PRIVATE)
                                .edit()
                                .putString("terminal_palette", palette.name)
                                .apply()
                        }
                        .testTag("theme_palette_${palette.name}"),
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF111827),
                    border = BorderStroke(
                        width = if (isSelected) 1.5.dp else 1.dp,
                        color = if (isSelected) Color(0xFF38BDF8) else Color(0xFF1E293B)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Palette,
                                    contentDescription = null,
                                    tint = cursorColor,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = palette.name,
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        color = Color(0xFFF8FAFC),
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }

                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Active Theme",
                                    tint = Color(0xFF10B981),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        // Terminal Swatch Preview
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            color = bgColor,
                            border = BorderStroke(1.dp, Color(0xFF334155))
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "jarvis@termux:~/workspace $ claude --dangerously-skip-permissions",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = cursorColor,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp
                                    )
                                )
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    palette.ansiColors.take(8).forEach { hex ->
                                        val swatch = Color(android.graphics.Color.parseColor(hex))
                                        Box(
                                            modifier = Modifier
                                                .size(18.dp)
                                                .clip(CircleShape)
                                                .background(swatch)
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
