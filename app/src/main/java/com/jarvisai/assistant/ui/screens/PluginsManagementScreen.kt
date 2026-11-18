package com.jarvisai.assistant.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.jarvisai.assistant.browser.BrowserDataStore
import com.jarvisai.assistant.extension.BrowserExtension
import com.jarvisai.assistant.extension.BrowserExtensionManager
import com.jarvisai.assistant.ui.screens.browser.DeveloperExtensionsScreen
import com.jarvisai.assistant.ui.screens.browser.ExtensionDetailsDialog
import com.jarvisai.assistant.ui.screens.browser.ExtensionPopupSheet
import com.jarvisai.assistant.ui.screens.browser.ExtensionWorkerInspectorDialog

@Composable
fun PluginsManagementScreen(onNavigateBack: () -> Unit = {}) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        BrowserDataStore.init(context)
        BrowserExtensionManager.init(context)
    }

    val extensions by BrowserExtensionManager.extensions.collectAsState()
    var inspectTarget by remember { mutableStateOf<BrowserExtension?>(null) }
    var detailsTarget by remember { mutableStateOf<BrowserExtension?>(null) }
    var popupTarget by remember { mutableStateOf<BrowserExtension?>(null) }

    DeveloperExtensionsScreen(
        extensions = extensions,
        onBack = onNavigateBack,
        onInspectWorker = { inspectTarget = it },
        onShowDetails = { detailsTarget = it },
        onOpenPopup = { ext ->
            BrowserExtensionManager.dispatchActionClickedToWorker(ext.id)
            popupTarget = ext
        }
    )

    popupTarget?.let { ext ->
        ExtensionPopupSheet(
            extension = ext,
            activeTabId = "default",
            onDismiss = { popupTarget = null }
        )
    }

    inspectTarget?.let { ext ->
        ExtensionWorkerInspectorDialog(
            extension = ext,
            onDismiss = { inspectTarget = null }
        )
    }

    detailsTarget?.let { ext ->
        ExtensionDetailsDialog(
            extension = ext,
            onDismiss = { detailsTarget = null }
        )
    }
}
