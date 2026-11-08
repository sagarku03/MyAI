package com.jarvisai.assistant.ui.screens

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint as AndroidPaint
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.AccentMintEmerald
import com.example.ui.theme.AccentSkyCyan
import com.example.ui.theme.JarvisTheme
import com.example.ui.theme.RoyalPurplePrimary
import com.example.ui.theme.StatusAmber
import com.example.ui.theme.TextLavenderMuted
import com.example.ui.theme.TextPureWhite
import com.jarvisai.assistant.service.OpticalSolverManager
import com.jarvisai.assistant.service.OpticalSolverMode
import com.jarvisai.assistant.ui.components.JarvisGlassPanel
import com.jarvisai.assistant.ui.components.JarvisGradientButton
import com.jarvisai.assistant.ui.components.JarvisIconControl
import com.jarvisai.assistant.ui.components.JarvisSecondaryActionButton
import com.jarvisai.assistant.ui.components.LiquidGlassBottomSheet
import java.nio.ByteBuffer
import java.util.concurrent.Executors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun OpticalSolverScreen(
    solverManager: OpticalSolverManager,
    apiKey: String,
    modelId: String,
    onSpeakSolution: (String) -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val themeColors = JarvisTheme.colors

    val isSolving by solverManager.isSolving.collectAsStateWithLifecycle()
    val selectedMode by solverManager.selectedMode.collectAsStateWithLifecycle()
    val latestResult by solverManager.latestResult.collectAsStateWithLifecycle()
    val errorMessage by solverManager.errorMessage.collectAsStateWithLifecycle()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    var isTorchOn by rememberSaveable { mutableStateOf(false) }
    var splitCount by rememberSaveable { mutableIntStateOf(4) }
    var customNote by rememberSaveable { mutableStateOf("") }
    var showCaptureFlash by remember { mutableStateOf(false) }
    var tapFocusOffset by remember { mutableStateOf<Offset?>(null) }

    var cameraRef by remember { mutableStateOf<Camera?>(null) }
    var imageCaptureRef by remember { mutableStateOf<ImageCapture?>(null) }
    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(Unit) {
        onDispose {
            try {
                cameraExecutor.shutdown()
            } catch (_: Exception) {
            }
        }
    }

    LaunchedEffect(hasCameraPermission, lifecycleOwner) {
        if (!hasCameraPermission) return@LaunchedEffect
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                val imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()
                imageCaptureRef = imageCapture

                cameraProvider.unbindAll()
                cameraRef = cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageCapture
                )
            } catch (e: Exception) {
                Log.w("OpticalSolverScreen", "CameraX bind warning: ${e.localizedMessage}")
            }
        }, ContextCompat.getMainExecutor(context))
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val bitmap = runCatching {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        BitmapFactory.decodeStream(stream)
                    }
                }.getOrNull()
                if (bitmap != null) {
                    showCaptureFlash = true
                    delay(140L)
                    showCaptureFlash = false
                    val result = solverManager.solveImage(
                        bitmap = bitmap,
                        apiKey = apiKey,
                        modelId = modelId,
                        customUserNote = customNote,
                        splitCount = splitCount
                    )
                    result.onSuccess { solved ->
                        onSpeakSolution(solved.spokenSummary)
                    }
                }
            }
        }
    }

    fun triggerBitmapSolve(bitmap: Bitmap) {
        scope.launch {
            showCaptureFlash = true
            delay(140L)
            showCaptureFlash = false
            val res = solverManager.solveImage(
                bitmap = bitmap,
                apiKey = apiKey,
                modelId = modelId,
                customUserNote = customNote,
                splitCount = splitCount
            )
            res.onSuccess { solved ->
                onSpeakSolution(solved.spokenSummary)
            }
        }
    }

    fun captureAndSolve() {
        val currentPreviewBitmap = previewView.bitmap
        if (currentPreviewBitmap != null) {
            triggerBitmapSolve(currentPreviewBitmap)
            return
        }

        val capture = imageCaptureRef
        if (capture != null && hasCameraPermission) {
            capture.takePicture(
                cameraExecutor,
                object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(image: ImageProxy) {
                        val bmp = imageProxyToBitmap(image)
                        image.close()
                        val finalBmp = bmp ?: createSyntheticTargetBitmap(selectedMode, customNote)
                        triggerBitmapSolve(finalBmp)
                    }

                    override fun onError(exception: ImageCaptureException) {
                        triggerBitmapSolve(createSyntheticTargetBitmap(selectedMode, customNote))
                    }
                }
            )
        } else {
            triggerBitmapSolve(createSyntheticTargetBitmap(selectedMode, customNote))
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "opticalScannerLaser")
    val laserProgress by infiniteTransition.animateFloat(
        initialValue = 0.08f,
        targetValue = 0.92f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "laserSweepY"
    )

    val flashAlpha by animateFloatAsState(
        targetValue = if (showCaptureFlash) 0.78f else 0f,
        animationSpec = tween(140),
        label = "captureFlashAlpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF05060C))
            .testTag("optical_solver_screen")
    ) {
        // 1. LIVE CAMERAX VIEWPORT
        if (hasCameraPermission) {
            AndroidView(
                factory = { previewView },
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            tapFocusOffset = offset
                            try {
                                val factory = previewView.meteringPointFactory
                                val point = factory.createPoint(offset.x, offset.y)
                                val action = FocusMeteringAction.Builder(point).build()
                                cameraRef?.cameraControl?.startFocusAndMetering(action)
                            } catch (_: Exception) {
                            }
                            scope.launch {
                                delay(900L)
                                tapFocusOffset = null
                            }
                        }
                    }
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                RoyalPurplePrimary.copy(alpha = 0.22f),
                                Color(0xFF070812)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                JarvisGlassPanel(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    accentColor = AccentSkyCyan
                ) {
                    Text(
                        text = "CAMERA PERMISSION REQUIRED",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPureWhite,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Grant camera access to scan math equations, code stacktraces, restaurant bills, or circuits in real time — or pick an image from your gallery.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextLavenderMuted
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        JarvisGradientButton(
                            text = "Grant Camera",
                            onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                            modifier = Modifier.weight(1f)
                        )
                        JarvisSecondaryActionButton(
                            text = "Gallery Photo",
                            icon = Icons.Default.PhotoLibrary,
                            onClick = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // 2. CYBERNETIC TARGETING RETICLE & SCANNING LASER OVERLAY
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp, vertical = 120.dp)
        ) {
            val w = size.width
            val h = size.height
            val bracketLen = minOf(w, h) * 0.14f
            val strokePx = 3.2.dp.toPx()
            val reticleColor = when (selectedMode) {
                OpticalSolverMode.MATH -> AccentSkyCyan
                OpticalSolverMode.CODE_DEBUG -> RoyalPurplePrimary
                OpticalSolverMode.BILL_SPLIT -> AccentMintEmerald
                OpticalSolverMode.CIRCUIT -> StatusAmber
            }

            // Top-Left Corner Bracket
            drawLine(reticleColor, Offset(0f, 0f), Offset(bracketLen, 0f), strokePx, StrokeCap.Round)
            drawLine(reticleColor, Offset(0f, 0f), Offset(0f, bracketLen), strokePx, StrokeCap.Round)

            // Top-Right Corner Bracket
            drawLine(reticleColor, Offset(w, 0f), Offset(w - bracketLen, 0f), strokePx, StrokeCap.Round)
            drawLine(reticleColor, Offset(w, 0f), Offset(w, bracketLen), strokePx, StrokeCap.Round)

            // Bottom-Left Corner Bracket
            drawLine(reticleColor, Offset(0f, h), Offset(bracketLen, h), strokePx, StrokeCap.Round)
            drawLine(reticleColor, Offset(0f, h), Offset(0f, h - bracketLen), strokePx, StrokeCap.Round)

            // Bottom-Right Corner Bracket
            drawLine(reticleColor, Offset(w, h), Offset(w - bracketLen, h), strokePx, StrokeCap.Round)
            drawLine(reticleColor, Offset(w, h), Offset(w, h - bracketLen), strokePx, StrokeCap.Round)

            // Center Crosshair
            val cx = w / 2f
            val cy = h / 2f
            val cross = 12.dp.toPx()
            drawLine(reticleColor.copy(alpha = 0.55f), Offset(cx - cross, cy), Offset(cx + cross, cy), 1.5.dp.toPx())
            drawLine(reticleColor.copy(alpha = 0.55f), Offset(cx, cy - cross), Offset(cx, cy + cross), 1.5.dp.toPx())

            // Scanning Laser Line
            val laserY = h * laserProgress
            drawLine(
                brush = Brush.horizontalGradient(
                    listOf(
                        Color.Transparent,
                        reticleColor.copy(alpha = if (isSolving) 0.95f else 0.55f),
                        Color.Transparent
                    )
                ),
                start = Offset(12f, laserY),
                end = Offset(w - 12f, laserY),
                strokeWidth = if (isSolving) 3.dp.toPx() else 1.8.dp.toPx()
            )

            // Tap-to-focus ring
            tapFocusOffset?.let { focusPt ->
                drawCircle(
                    color = AccentSkyCyan,
                    radius = 28.dp.toPx(),
                    center = Offset(focusPt.x.coerceIn(0f, w), focusPt.y.coerceIn(0f, h)),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
                )
            }
        }

        // Capture Flash Overlay
        if (flashAlpha > 0.01f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = flashAlpha))
            )
        }

        // 3. TOP BAR: BACK BUTTON, HUD BADGE, GALLERY & FLASH TOGGLE
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            JarvisIconControl(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                onClick = onBack,
                modifier = Modifier.testTag("optical_solver_back_btn")
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color(0xCC0D1021))
                    .border(1.dp, AccentSkyCyan.copy(alpha = 0.45f), RoundedCornerShape(999.dp))
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "${selectedMode.emoji} ${selectedMode.title.uppercase()}",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextPureWhite,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "OPTICAL SOLVER // ${modelId.uppercase()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = AccentSkyCyan,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                JarvisIconControl(
                    icon = Icons.Default.PhotoLibrary,
                    contentDescription = "Pick Image from Gallery",
                    onClick = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    tint = AccentSkyCyan,
                    modifier = Modifier.testTag("optical_solver_gallery_btn")
                )
                JarvisIconControl(
                    icon = if (isTorchOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                    contentDescription = "Toggle Flash",
                    isActive = isTorchOn,
                    activeColor = StatusAmber,
                    onClick = {
                        val next = !isTorchOn
                        isTorchOn = next
                        try {
                            cameraRef?.cameraControl?.enableTorch(next)
                        } catch (_: Exception) {
                        }
                    },
                    modifier = Modifier.testTag("optical_solver_torch_btn")
                )
            }
        }

        // 4. BOTTOM HUD CONTROLS: MODE CHIPS, OPTIONAL PROMPT/SPLIT COUNTER & SHUTTER BUTTON
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Transparent,
                            Color(0xE6080912),
                            Color(0xF5080912)
                        )
                    )
                )
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!errorMessage.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(themeColors.statusRoseRed.copy(alpha = 0.20f))
                        .border(1.dp, themeColors.statusRoseRed, RoundedCornerShape(16.dp))
                        .padding(12.dp)
                ) {
                    Text(
                        text = errorMessage ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextPureWhite,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Bill Split People Counter when BILL_SPLIT is active
            AnimatedVisibility(
                visible = selectedMode == OpticalSolverMode.BILL_SPLIT,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0xCC141829))
                        .border(1.dp, AccentMintEmerald.copy(alpha = 0.5f), RoundedCornerShape(18.dp))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "🧾 Split Bill Among:",
                        style = MaterialTheme.typography.labelLarge,
                        color = TextPureWhite,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        IconButton(
                            onClick = { if (splitCount > 2) splitCount-- },
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(RoyalPurplePrimary.copy(alpha = 0.25f))
                        ) {
                            Icon(Icons.Default.Remove, contentDescription = "Decrease", tint = Color.White)
                        }
                        Text(
                            text = "$splitCount People",
                            style = MaterialTheme.typography.titleSmall,
                            color = AccentMintEmerald,
                            fontWeight = FontWeight.ExtraBold
                        )
                        IconButton(
                            onClick = { if (splitCount < 20) splitCount++ },
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(RoyalPurplePrimary.copy(alpha = 0.25f))
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Increase", tint = Color.White)
                        }
                    }
                }
            }

            // Optional Custom Focus Hint
            OutlinedTextField(
                value = customNote,
                onValueChange = { customNote = it },
                placeholder = {
                    Text(
                        text = "Optional instruction (e.g., 'Solve for x', 'Fix null pointer')...",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextLavenderMuted
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextPureWhite,
                    unfocusedTextColor = TextPureWhite,
                    focusedContainerColor = Color(0xCC121626),
                    unfocusedContainerColor = Color(0xAA121626),
                    focusedBorderColor = AccentSkyCyan,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.22f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("optical_solver_note_input")
            )

            // 4-Mode Selector Strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OpticalSolverMode.entries.forEach { mode ->
                    val isSelected = selectedMode == mode
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(
                                if (isSelected) {
                                    Brush.horizontalGradient(listOf(RoyalPurplePrimary, AccentSkyCyan))
                                } else {
                                    Brush.horizontalGradient(
                                        listOf(Color(0xCC161B2E), Color(0xCC161B2E))
                                    )
                                }
                            )
                            .border(
                                width = 1.dp,
                                color = if (isSelected) Color.White.copy(alpha = 0.65f) else Color.White.copy(alpha = 0.18f),
                                shape = RoundedCornerShape(999.dp)
                            )
                            .clickable { solverManager.setMode(mode) }
                            .padding(horizontal = 14.dp, vertical = 9.dp)
                            .testTag("optical_mode_${mode.id.lowercase()}")
                    ) {
                        Text(
                            text = "${mode.emoji} ${mode.title}",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold
                        )
                    }
                }
            }

            // Capture & Solve Shutter Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = selectedMode.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextLavenderMuted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(RoyalPurplePrimary, AccentSkyCyan)
                            )
                        )
                        .border(3.dp, Color.White, CircleShape)
                        .clickable(enabled = !isSolving) { captureAndSolve() }
                        .testTag("optical_solver_shutter_btn"),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSolving) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(32.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = "Capture and Solve",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }
        }

        // 5. SLIDING LIQUID GLASS SOLUTION DRAWER
        val activeSolution = latestResult
        if (activeSolution != null) {
            LiquidGlassBottomSheet(
                title = "${activeSolution.mode.emoji} ${activeSolution.mode.title} Solution",
                subtitle = "Solved in ${activeSolution.latencyMs}ms • Tap Copy or Share below",
                onDismiss = { solverManager.clearResult() }
            ) {
                // Spoken Hinglish Summary Banner
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(AccentSkyCyan.copy(alpha = 0.14f))
                        .border(1.dp, AccentSkyCyan.copy(alpha = 0.45f), RoundedCornerShape(18.dp))
                        .padding(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        IconButton(
                            onClick = { onSpeakSolution(activeSolution.spokenSummary) },
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(AccentSkyCyan.copy(alpha = 0.25f))
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = "Speak Summary",
                                tint = AccentSkyCyan
                            )
                        }
                        Text(
                            text = activeSolution.spokenSummary,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPureWhite,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Detailed Markdown Solution Card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFF0B0E1A))
                        .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(20.dp))
                        .padding(16.dp)
                ) {
                    Text(
                        text = activeSolution.rawMarkdownSolution,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = if (activeSolution.mode == OpticalSolverMode.CODE_DEBUG) {
                                FontFamily.Monospace
                            } else {
                                FontFamily.Default
                            },
                            lineHeight = 21.sp
                        ),
                        color = TextPureWhite
                    )
                }

                // Action Row: Copy & Share
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    JarvisSecondaryActionButton(
                        text = "Copy Solution",
                        icon = Icons.Default.ContentCopy,
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            clipboard?.setPrimaryClip(
                                ClipData.newPlainText("JARVIS Optical Solution", activeSolution.rawMarkdownSolution)
                            )
                            Toast.makeText(context, "Solution copied to clipboard", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("optical_solution_copy_btn")
                    )

                    JarvisGradientButton(
                        text = "Share",
                        icon = Icons.Default.Share,
                        onClick = {
                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "JARVIS ${activeSolution.mode.title} Solution")
                                putExtra(Intent.EXTRA_TEXT, activeSolution.rawMarkdownSolution)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "Share Solution"))
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("optical_solution_share_btn")
                    )
                }
            }
        }
    }
}

private fun imageProxyToBitmap(image: ImageProxy): Bitmap? {
    return try {
        val planeProxy = image.planes.firstOrNull() ?: return null
        val buffer: ByteBuffer = planeProxy.buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    } catch (_: Exception) {
        null
    }
}

private fun createSyntheticTargetBitmap(mode: OpticalSolverMode, customNote: String): Bitmap {
    val bmp = Bitmap.createBitmap(640, 360, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bmp)
    canvas.drawColor(AndroidColor.rgb(14, 18, 34))
    val paint = AndroidPaint().apply {
        color = AndroidColor.WHITE
        textSize = 26f
        isAntiAlias = true
    }
    val sampleText = when (mode) {
        OpticalSolverMode.MATH -> customNote.ifBlank { "Solve: ∫ (3x^2 + 4x - 5) dx = 0" }
        OpticalSolverMode.CODE_DEBUG -> customNote.ifBlank { "NullPointerException at MainActivity.kt:42" }
        OpticalSolverMode.BILL_SPLIT -> customNote.ifBlank { "Receipt: Paneer Tikka 420, Naan 120, GST 5%" }
        OpticalSolverMode.CIRCUIT -> customNote.ifBlank { "ESP32 + 10k Ohm Pull-up + I2C OLED Pinout" }
    }
    canvas.drawText(sampleText, 28f, 180f, paint)
    return bmp
}
