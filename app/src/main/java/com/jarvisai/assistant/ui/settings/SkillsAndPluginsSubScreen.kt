package com.jarvisai.assistant.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.jarvisai.assistant.skills.BuildWithGooglePluginItemDto
import com.jarvisai.assistant.skills.InstalledPluginDto
import com.jarvisai.assistant.skills.SkillDefinitionDto
import com.jarvisai.assistant.skills.SkillsAndPluginsManager
import com.jarvisai.assistant.ui.components.bouncyOverscroll
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkillsAndPluginsSubScreen(
    onBack: () -> Unit,
    embedded: Boolean = false
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val skills by SkillsAndPluginsManager.skills.collectAsState()
    val installedPlugins by SkillsAndPluginsManager.installedPlugins.collectAsState()
    val googlePlugins by SkillsAndPluginsManager.googlePlugins.collectAsState()
    val filterScope by SkillsAndPluginsManager.skillsFilterScope.collectAsState()
    val isSkillsLoading by SkillsAndPluginsManager.isSkillsLoading.collectAsState()
    val isPluginsLoading by SkillsAndPluginsManager.isPluginsLoading.collectAsState()
    val installingId by SkillsAndPluginsManager.installingPluginId.collectAsState()
    val deletingId by SkillsAndPluginsManager.deletingPluginId.collectAsState()
    val statusMessage by SkillsAndPluginsManager.statusMessage.collectAsState()
    val errorMessage by SkillsAndPluginsManager.errorMessage.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) } // 0: Skills, 1: Plugins, 2: Store
    var selectedSkillForDetail by remember { mutableStateOf<SkillDefinitionDto?>(null) }
    var showGitHubInstaller by remember { mutableStateOf<Boolean?>(null) } // false = skill, true = plugin
    var showPluginReposSheet by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        SkillsAndPluginsManager.refreshSkills(context)
        SkillsAndPluginsManager.refreshInstalledPlugins(context)
        SkillsAndPluginsManager.refreshGooglePlugins(context)
    }

    val content: @Composable (Modifier) -> Unit = { containerMod ->
        Column(
            modifier = containerMod
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!errorMessage.isNullOrBlank()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = AgySettingsColors.Danger.copy(alpha = 0.12f)),
                    border = BorderStroke(1.dp, AgySettingsColors.Danger.copy(alpha = 0.35f))
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = AgySettingsColors.Danger, modifier = Modifier.size(18.dp))
                        Text(errorMessage.orEmpty(), fontSize = 12.5.sp, color = AgySettingsColors.Danger, modifier = Modifier.weight(1f))
                        IconButton(onClick = { SkillsAndPluginsManager.clearStatus() }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = AgySettingsColors.Danger, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            if (!statusMessage.isNullOrBlank()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = AgySettingsColors.QuotaGreen.copy(alpha = 0.12f)),
                    border = BorderStroke(1.dp, AgySettingsColors.QuotaGreen.copy(alpha = 0.35f))
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = AgySettingsColors.QuotaGreen, modifier = Modifier.size(18.dp))
                        Text(statusMessage.orEmpty(), fontSize = 12.5.sp, color = AgySettingsColors.QuotaGreen, modifier = Modifier.weight(1f))
                        IconButton(onClick = { SkillsAndPluginsManager.clearStatus() }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = AgySettingsColors.QuotaGreen, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            // AntiGem 3-Tab Header Row
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = AgySettingsColors.CardBg,
                border = BorderStroke(1.dp, AgySettingsColors.CardBorder)
            ) {
                SecondaryTabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color.Transparent,
                    contentColor = AgySettingsColors.GeminiBlue,
                    divider = {}
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        modifier = Modifier.testTag("skills_tab_btn"),
                        text = { Text("Skills (${skills.size})", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        modifier = Modifier.testTag("plugins_tab_btn"),
                        text = { Text("Plugins (${installedPlugins.size})", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1) }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        modifier = Modifier.testTag("store_tab_btn"),
                        text = { Text("Store", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1) }
                    )
                }
            }

            when (selectedTab) {
                0 -> SkillsListTab(
                    skills = skills,
                    isLoading = isSkillsLoading,
                    filterScope = filterScope,
                    onFilterChange = { SkillsAndPluginsManager.setSkillsFilterScope(it) },
                    onRefresh = { scope.launch { SkillsAndPluginsManager.refreshSkills(context) } },
                    onAddFromGitHub = { showGitHubInstaller = false },
                    onOpenSkill = { selectedSkillForDetail = it },
                    onDeleteSkill = { s -> scope.launch { SkillsAndPluginsManager.deleteSkill(context, s) } }
                )
                1 -> PluginsInstalledTab(
                    plugins = installedPlugins,
                    isLoading = isPluginsLoading,
                    deletingId = deletingId,
                    onRefresh = { scope.launch { SkillsAndPluginsManager.refreshInstalledPlugins(context) } },
                    onAddFromGitHub = { showGitHubInstaller = true },
                    onOpenRepoCatalog = { showPluginReposSheet = true },
                    onOpenStore = { selectedTab = 2 },
                    onUninstall = { p -> scope.launch { SkillsAndPluginsManager.deletePlugin(context, p.id, p.name) } }
                )
                2 -> GooglePluginsStoreTab(
                    catalog = googlePlugins,
                    installedIds = installedPlugins.map { it.id }.toSet(),
                    installingId = installingId,
                    onInstall = { item -> scope.launch { SkillsAndPluginsManager.installGooglePlugin(context, item.id) } },
                    onRefresh = { scope.launch { SkillsAndPluginsManager.refreshGooglePlugins(context) } }
                )
            }
        }
    }

    if (embedded) {
        content(Modifier)
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("Skills & Plugins", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = AgySettingsColors.TextPrimary)
                            Text("Agent SKILL.md, Google Store & GitHub Packages", fontSize = 11.sp, color = AgySettingsColors.TextSecondary)
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack, modifier = Modifier.size(48.dp).testTag("skills_plugins_back")) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = AgySettingsColors.TextPrimary)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = AgySettingsColors.BgCanvas)
                )
            },
            containerColor = AgySettingsColors.BgCanvas
        ) { innerPadding ->
            content(Modifier.padding(innerPadding))
        }
    }

    selectedSkillForDetail?.let { skill ->
        SkillMarkdownDetailDialog(skill = skill, onDismiss = { selectedSkillForDetail = null })
    }

    showGitHubInstaller?.let { asPlugin ->
        GitHubSkillOrPluginDialog(
            asPlugin = asPlugin,
            onDismiss = { showGitHubInstaller = null },
            onInstall = { repoInput ->
                scope.launch {
                    val res = SkillsAndPluginsManager.installFromGitHub(context, repoInput, asPlugin = asPlugin)
                    if (res.isSuccess) showGitHubInstaller = null
                }
            }
        )
    }

    if (showPluginReposSheet) {
        ModalBottomSheet(
            onDismissRequest = { showPluginReposSheet = false },
            containerColor = AgySettingsColors.BgCanvas
        ) {
            Column(modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp).padding(horizontal = 16.dp)) {
                Text("WebView Extension Repositories", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = AgySettingsColors.TextPrimary)
                Spacer(Modifier.height(8.dp))
                PluginRepositoriesTab(modifier = Modifier.fillMaxWidth().height(420.dp))
            }
        }
    }
}

@Composable
private fun SkillsListTab(
    skills: List<SkillDefinitionDto>,
    isLoading: Boolean,
    filterScope: String,
    onFilterChange: (String) -> Unit,
    onRefresh: () -> Unit,
    onAddFromGitHub: () -> Unit,
    onOpenSkill: (SkillDefinitionDto) -> Unit,
    onDeleteSkill: (SkillDefinitionDto) -> Unit
) {
    val filtered = remember(skills, filterScope) {
        when (filterScope) {
            "GLOBAL" -> skills.filter { it.scope == "GLOBAL" }
            "WORKSPACE" -> skills.filter { it.scope == "WORKSPACE" }
            else -> skills
        }
    }

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("ALL" to "All", "GLOBAL" to "Global", "WORKSPACE" to "Workspace").forEach { (key, label) ->
                FilterChip(
                    selected = filterScope == key,
                    onClick = { onFilterChange(key) },
                    label = { Text(label, fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AgySettingsColors.GeminiBlue.copy(alpha = 0.2f),
                        selectedLabelColor = AgySettingsColors.GeminiBlue
                    )
                )
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onRefresh, modifier = Modifier.size(44.dp).testTag("refresh_skills_btn")) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = AgySettingsColors.GeminiBlue)
                } else {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh Skills", tint = AgySettingsColors.TextSecondary)
                }
            }
        }

        OutlinedButton(
            onClick = onAddFromGitHub,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("add_skill_github_btn"),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, AgySettingsColors.GeminiBlue.copy(alpha = 0.45f))
        ) {
            Icon(Icons.Default.Code, contentDescription = null, tint = AgySettingsColors.GeminiBlue, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(8.dp))
            Text("Install Skill from GitHub (owner/repo)", fontSize = 13.sp, color = AgySettingsColors.GeminiBlue, fontWeight = FontWeight.SemiBold)
        }

        if (filtered.isEmpty()) {
            EmptyStateCard(
                icon = Icons.Default.AutoAwesome,
                title = "No Skills in This Scope",
                subtitle = "Install a skill from GitHub or the Google Store tab to teach Agentra specialized workflows."
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().bouncyOverscroll(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(filtered, key = { it.identifier }) { skill ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onOpenSkill(skill) }
                            .testTag("skill_card_${skill.name}"),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = AgySettingsColors.CardBg),
                        border = BorderStroke(1.dp, AgySettingsColors.CardBorder)
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (skill.scope == "WORKSPACE") Icons.Default.Folder else Icons.Default.Public,
                                    contentDescription = null,
                                    tint = if (skill.scope == "WORKSPACE") AgySettingsColors.Warning else AgySettingsColors.GeminiBlue,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = skill.displayName,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AgySettingsColors.TextPrimary,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = AgySettingsColors.CardBgElevated
                                ) {
                                    Text(
                                        text = skill.scope,
                                        fontSize = 10.sp,
                                        color = AgySettingsColors.TextSecondary,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                IconButton(
                                    onClick = { onDeleteSkill(skill) },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = "Delete Skill", tint = AgySettingsColors.TextSecondary, modifier = Modifier.size(17.dp))
                                }
                            }
                            Text(
                                text = skill.description,
                                fontSize = 12.sp,
                                color = AgySettingsColors.TextSecondary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = skill.path,
                                fontSize = 10.5.sp,
                                fontFamily = FontFamily.Monospace,
                                color = AgySettingsColors.TextSecondary.copy(alpha = 0.7f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PluginsInstalledTab(
    plugins: List<InstalledPluginDto>,
    isLoading: Boolean,
    deletingId: String?,
    onRefresh: () -> Unit,
    onAddFromGitHub: () -> Unit,
    onOpenRepoCatalog: () -> Unit,
    onOpenStore: () -> Unit,
    onUninstall: (InstalledPluginDto) -> Unit
) {
    var pendingUninstall by remember { mutableStateOf<InstalledPluginDto?>(null) }

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = onAddFromGitHub,
                modifier = Modifier.weight(1f).heightIn(min = 46.dp).testTag("add_plugin_github_btn"),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, AgySettingsColors.AccentIndigo.copy(alpha = 0.5f))
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = AgySettingsColors.AccentIndigo, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(5.dp))
                Text("From GitHub", fontSize = 12.sp, color = AgySettingsColors.AccentIndigo)
            }
            OutlinedButton(
                onClick = onOpenRepoCatalog,
                modifier = Modifier.weight(1f).heightIn(min = 46.dp).testTag("open_webext_repos_btn"),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, AgySettingsColors.CardBorder)
            ) {
                Icon(Icons.Default.Extension, contentDescription = null, tint = AgySettingsColors.Info, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(5.dp))
                Text("WebExt Repos", fontSize = 12.sp, color = AgySettingsColors.Info)
            }
            IconButton(onClick = onRefresh, modifier = Modifier.size(46.dp)) {
                if (isLoading) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                else Icon(Icons.Default.Refresh, contentDescription = "Refresh Plugins", tint = AgySettingsColors.TextSecondary)
            }
        }

        if (plugins.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = AgySettingsColors.CardBg),
                border = BorderStroke(1.dp, AgySettingsColors.CardBorder)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Default.Extension, contentDescription = null, tint = AgySettingsColors.GeminiPurple, modifier = Modifier.size(38.dp))
                    Text("No Plugins Installed", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = AgySettingsColors.TextPrimary)
                    Text("Browse the Official Google Store or install a plugin repository directly from GitHub.", fontSize = 12.sp, color = AgySettingsColors.TextSecondary)
                    Button(onClick = onOpenStore, colors = ButtonDefaults.buttonColors(containerColor = AgySettingsColors.Terracotta)) {
                        Icon(Icons.Default.Storefront, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Open Store")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().bouncyOverscroll(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(plugins, key = { it.id }) { plugin ->
                    val isDeleting = deletingId == plugin.id
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = AgySettingsColors.CardBg),
                        border = BorderStroke(1.dp, AgySettingsColors.CardBorder)
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Extension, contentDescription = null, tint = AgySettingsColors.GeminiPurple, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("${plugin.name} v${plugin.version}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AgySettingsColors.TextPrimary)
                                    Text(plugin.pluginUri, fontSize = 10.5.sp, fontFamily = FontFamily.Monospace, color = AgySettingsColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                TextButton(
                                    onClick = { pendingUninstall = plugin },
                                    enabled = !isDeleting
                                ) {
                                    if (isDeleting) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                    } else {
                                        Text("Uninstall", color = AgySettingsColors.Danger, fontSize = 12.sp)
                                    }
                                }
                            }
                            if (plugin.description.isNotBlank()) {
                                Text(plugin.description, fontSize = 12.sp, color = AgySettingsColors.TextSecondary)
                            }
                            if (plugin.skills.isNotEmpty()) {
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    plugin.skills.forEach { sk ->
                                        Surface(shape = RoundedCornerShape(8.dp), color = AgySettingsColors.GeminiBlue.copy(alpha = 0.14f)) {
                                            Text(sk.displayName, fontSize = 11.sp, color = AgySettingsColors.GeminiBlue, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
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

    pendingUninstall?.let { plugin ->
        AlertDialog(
            onDismissRequest = { pendingUninstall = null },
            containerColor = AgySettingsColors.CardBg,
            title = { Text("Uninstall ${plugin.name}?", color = AgySettingsColors.TextPrimary, fontWeight = FontWeight.Bold) },
            text = { Text("This will remove all bundled skills and files for '${plugin.name}'.", color = AgySettingsColors.TextSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    onUninstall(plugin)
                    pendingUninstall = null
                }) { Text("Uninstall", color = AgySettingsColors.Danger) }
            },
            dismissButton = {
                TextButton(onClick = { pendingUninstall = null }) { Text("Cancel", color = AgySettingsColors.TextSecondary) }
            }
        )
    }
}

@Composable
private fun GooglePluginsStoreTab(
    catalog: List<BuildWithGooglePluginItemDto>,
    installedIds: Set<String>,
    installingId: String?,
    onInstall: (BuildWithGooglePluginItemDto) -> Unit,
    onRefresh: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().bouncyOverscroll(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Build with Google Marketplace", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AgySettingsColors.TextPrimary)
                    Text("Official verified skill bundles & MCP server integrations", fontSize = 11.5.sp, color = AgySettingsColors.TextSecondary)
                }
                IconButton(onClick = onRefresh, modifier = Modifier.size(44.dp)) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh Store", tint = AgySettingsColors.TextSecondary)
                }
            }
        }

        items(catalog, key = { it.id }) { item ->
            val isInstalled = item.id in installedIds
            val isInstalling = installingId == item.id
            val tint = Color(item.iconColorHex)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = AgySettingsColors.CardBg),
                border = BorderStroke(1.dp, if (isInstalled) AgySettingsColors.QuotaGreen.copy(alpha = 0.45f) else AgySettingsColors.CardBorder)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(
                            modifier = Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(tint.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Storefront, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(item.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AgySettingsColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                                Surface(shape = RoundedCornerShape(6.dp), color = AgySettingsColors.QuotaGreen.copy(alpha = 0.15f)) {
                                    Row(modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                        Icon(Icons.Default.Verified, contentDescription = null, tint = AgySettingsColors.QuotaGreen, modifier = Modifier.size(11.dp))
                                        Text(item.trustLevel, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = AgySettingsColors.QuotaGreen)
                                    }
                                }
                            }
                            Text("${item.category} · v${item.version} · ${item.bundledSkills.size} skill(s)${if (item.bundledMcpSpec != null) " + MCP" else ""}", fontSize = 11.sp, color = AgySettingsColors.TextSecondary)
                        }
                    }

                    Text(item.description, fontSize = 12.sp, color = AgySettingsColors.TextSecondary, lineHeight = 16.sp)

                    Button(
                        onClick = { onInstall(item) },
                        enabled = !isInstalling,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isInstalled) AgySettingsColors.QuotaGreen else AgySettingsColors.Terracotta
                        ),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 46.dp).testTag("store_install_${item.id}")
                    ) {
                        if (isInstalling) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                        } else {
                            Icon(if (isInstalled) Icons.Default.Check else Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (isInstalled) "Installed (Reinstall / Repair)" else "Install Plugin Bundle", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyStateCard(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = AgySettingsColors.CardBg),
        border = BorderStroke(1.dp, AgySettingsColors.CardBorder)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(icon, contentDescription = null, tint = AgySettingsColors.TextSecondary, modifier = Modifier.size(34.dp))
            Text(title, fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = AgySettingsColors.TextPrimary)
            Text(subtitle, fontSize = 12.sp, color = AgySettingsColors.TextSecondary)
        }
    }
}

@Composable
private fun SkillMarkdownDetailDialog(skill: SkillDefinitionDto, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.94f).heightIn(max = 650.dp),
            shape = RoundedCornerShape(20.dp),
            color = AgySettingsColors.CardBg,
            border = BorderStroke(1.dp, AgySettingsColors.CardBorder)
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(skill.displayName, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = AgySettingsColors.TextPrimary)
                        Text(skill.path, fontSize = 10.5.sp, fontFamily = FontFamily.Monospace, color = AgySettingsColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = AgySettingsColors.TextSecondary)
                    }
                }
                Surface(
                    modifier = Modifier.weight(1f, fill = false).fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = Color.Black.copy(alpha = 0.28f)
                ) {
                    Text(
                        text = skill.content,
                        fontSize = 12.5.sp,
                        fontFamily = FontFamily.Monospace,
                        color = AgySettingsColors.TextPrimary,
                        modifier = Modifier.padding(12.dp).verticalScroll(rememberScrollState())
                    )
                }
            }
        }
    }
}

@Composable
private fun GitHubSkillOrPluginDialog(
    asPlugin: Boolean,
    onDismiss: () -> Unit,
    onInstall: (String) -> Unit
) {
    var repoText by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AgySettingsColors.CardBg,
        title = {
            Text(
                text = if (asPlugin) "Install Plugin from GitHub" else "Install Skill from GitHub",
                color = AgySettingsColors.TextPrimary,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Enter a public GitHub repository (`owner/repo` or full GitHub URL) containing `SKILL.md` or `skills/*/SKILL.md`.",
                    fontSize = 12.5.sp,
                    color = AgySettingsColors.TextSecondary
                )
                OutlinedTextField(
                    value = repoText,
                    onValueChange = { repoText = it },
                    placeholder = { Text("e.g. anthropics/skills or https://github.com/...", fontSize = 12.sp) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AgySettingsColors.GeminiBlue,
                        unfocusedBorderColor = AgySettingsColors.CardBorder,
                        focusedTextColor = AgySettingsColors.TextPrimary,
                        unfocusedTextColor = AgySettingsColors.TextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("github_repo_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (repoText.isNotBlank()) onInstall(repoText) },
                enabled = repoText.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = AgySettingsColors.GeminiBlue)
            ) {
                Text("Fetch & Install")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = AgySettingsColors.TextSecondary)
            }
        }
    )
}
