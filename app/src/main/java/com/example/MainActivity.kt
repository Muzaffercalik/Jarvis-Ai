package com.example

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.core.content.ContextCompat
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.database.*
import kotlinx.coroutines.launch
import com.example.speech.JarvisSpeechManager
import com.example.ui.theme.*
import com.example.viewmodel.JarvisTab
import com.example.viewmodel.JarvisViewModel
import com.example.viewmodel.JarvisViewModelFactory

class MainActivity : ComponentActivity() {
    private var speechManager: JarvisSpeechManager? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val database = JarvisDatabase.getDatabase(applicationContext)
        val repository = JarvisRepository(database)

        setContent {
            MyApplicationTheme {
                val viewModel: JarvisViewModel = viewModel(
                    factory = JarvisViewModelFactory(repository)
                )

                val context = LocalContext.current

                // Initialize Speech Manager
                DisposableEffect(Unit) {
                    speechManager = JarvisSpeechManager(
                        context = context,
                        onResult = { text ->
                            viewModel.setSpeechInput(text)
                            viewModel.executeCommand(text, context) { speechManager?.speak(it) }
                        },
                        onPartialResult = { text ->
                            viewModel.setSpeechInput(text)
                        },
                        onError = { errorText ->
                            Toast.makeText(context, errorText, Toast.LENGTH_SHORT).show()
                            viewModel.setListening(false)
                        },
                        onListeningStateChanged = { active ->
                            viewModel.setListening(active)
                        }
                    )

                    onDispose {
                        speechManager?.shutdown()
                    }
                }

                JarvisMainScreen(
                    viewModel = viewModel,
                    speechManager = speechManager,
                    onStartVoice = { speechManager?.startListening() },
                    onStopVoice = { speechManager?.stopListening() }
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun JarvisMainScreen(
    viewModel: JarvisViewModel,
    speechManager: JarvisSpeechManager?,
    onStartVoice: () -> Unit,
    onStopVoice: () -> Unit
) {
    val context = LocalContext.current
    val activeTab by viewModel.activeTab.collectAsStateWithLifecycle()
    val isListening by viewModel.isListening.collectAsStateWithLifecycle()
    val isAnalyzing by viewModel.isAnalyzing.collectAsStateWithLifecycle()
    val currentInput by viewModel.currentSpeechInput.collectAsStateWithLifecycle()
    val jarvisResponse by viewModel.jarvisSpeechResponse.collectAsStateWithLifecycle()

    var typedInput by remember { mutableStateOf("") }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            onStartVoice()
        } else {
            Toast.makeText(context, "Ses algılama için mikrofon yetkisi vermelisiniz sör.", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(SpaceNavy),
        containerColor = SpaceNavy,
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Transparent)
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                JarvisNavigationBar(
                    activeTab = activeTab,
                    onTabSelected = { viewModel.setActiveTab(it) }
                )
            }
        },
        contentWindowInsets = WindowInsets.systemBars
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(SpaceNavy)
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // High-tech Header
            JarvisHeader(isAnalyzing = isAnalyzing)

            Spacer(modifier = Modifier.height(16.dp))

            // Main View Area depending on Active Tab
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (activeTab) {
                    JarvisTab.HUD -> {
                        JarvisHudView(
                            isListening = isListening,
                            isAnalyzing = isAnalyzing,
                            currentInput = currentInput,
                            jarvisResponse = jarvisResponse,
                            typedInput = typedInput,
                            onTypedInputChange = { typedInput = it },
                            onSendTypedCommand = {
                                if (typedInput.isNotBlank()) {
                                    viewModel.executeCommand(typedInput, context) { speechManager?.speak(it) }
                                    typedInput = ""
                                }
                            },
                            onMicClicked = {
                                if (isListening) {
                                    onStopVoice()
                                } else {
                                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            },
                            viewModel = viewModel,
                            speechManager = speechManager
                        )
                    }
                    JarvisTab.CREDENTIALS -> {
                        JarvisCredentialsView(viewModel = viewModel)
                    }
                    JarvisTab.SIMULATOR -> {
                        JarvisSimulatorView(viewModel = viewModel)
                    }
                    JarvisTab.SHORTCUTS -> {
                        JarvisShortcutsView(viewModel = viewModel)
                    }
                    JarvisTab.LOGS -> {
                        JarvisLogsView(viewModel = viewModel)
                    }
                    JarvisTab.BROWSER_USE -> {
                        JarvisBrowserUseView(viewModel = viewModel)
                    }
                }
            }
        }
    }
}

@Composable
fun JarvisHeader(isAnalyzing: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, NeonCyan.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
            .background(Color(0xFF141619), RoundedCornerShape(16.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val animationScale by rememberInfiniteTransition(label = "").animateFloat(
            initialValue = 0.4f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(8000, easing = LinearEasing), RepeatMode.Reverse),
            label = ""
        )

        // Emblem Capsule 'JV'
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(Color(0xFF1A1C1E))
                .border(1.dp, NeonCyan.copy(alpha = 0.3f), CircleShape)
                .padding(2.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "JV",
                color = NeonCyan,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                letterSpacing = 1.sp
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column {
            Text(
                text = "JARVIS PRO",
                color = Color.White,
                fontSize = 14.sp,
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp
            )
            Text(
                text = if (isAnalyzing) "SİSTEM ANALİZDE" else "SYSTEM ONLINE",
                color = if (isAnalyzing) TechViolet else NeonCyan.copy(alpha = 0.8f),
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.5.sp
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        // Premium telemetry pulse
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black.copy(alpha = 0.4f))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(if (isAnalyzing) TechViolet else SuccessGreen)
            )
            Text(
                text = if (isAnalyzing) "SYS_BUSY" else "SYS_OK",
                color = if (isAnalyzing) TechViolet else SuccessGreen,
                fontSize = 8.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun JarvisCoreVisualizer(
    isListening: Boolean,
    isThinking: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "core")
    
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isListening) 700 else if (isThinking) 1300 else 2500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(170.dp)
            .padding(8.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2, size.height / 2)
            val baseRadius = size.minDimension / 2.3f

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        NeonCyan.copy(alpha = if (isListening) 0.5f else if (isThinking) 0.4f else 0.15f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = (baseRadius * pulseScale).coerceAtLeast(0.1f)
                )
            )

            drawCircle(
                color = NeonCyan.copy(alpha = 0.8f),
                radius = (baseRadius * pulseScale).coerceAtLeast(0f),
                style = Stroke(
                    width = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(
                        floatArrayOf(40f, 25f),
                        rotation
                    )
                )
            )

            val safeInverseScale = if (pulseScale > 0.01f) 1f / pulseScale else 1.0f
            drawCircle(
                color = NeonBlue.copy(alpha = 0.5f),
                radius = ((baseRadius - 16.dp.toPx()) * safeInverseScale).coerceAtLeast(0f),
                style = Stroke(
                    width = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(
                        floatArrayOf(30f, 15f),
                        -rotation * 1.5f
                    )
                )
            )

            drawCircle(
                color = if (isListening) ErrorRed else if (isThinking) TechViolet else NeonCyan,
                radius = 12.dp.toPx(),
                style = Stroke(width = 3.dp.toPx())
            )
        }
        
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(12.dp)
        ) {
            val lineScale by infiniteTransition.animateFloat(
                initialValue = 0.6f,
                targetValue = 1.1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(if (isListening) 500 else 1100, easing = LinearOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "lineScale"
            )

            // Dynamic Immersive telemetry lines
            Box(
                modifier = Modifier
                    .width((32.dp.value * lineScale).dp)
                    .height(2.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(NeonCyan)
            )
            Spacer(modifier = Modifier.height(3.dp))
            Box(
                modifier = Modifier
                    .width((46.dp.value * lineScale).dp)
                    .height(2.5.dp)
                    .clip(RoundedCornerShape(1.2.dp))
                    .background(Color.White)
            )
            Spacer(modifier = Modifier.height(3.dp))
            Box(
                modifier = Modifier
                    .width((24.dp.value * lineScale).dp)
                    .height(2.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(NeonCyan)
            )
        }
    }
}

@Composable
fun JarvisNavigationBar(
    activeTab: JarvisTab,
    onTabSelected: (JarvisTab) -> Unit
) {
    NavigationBar(
        containerColor = Color(0xFF1A1C1E).copy(alpha = 0.9f),
        tonalElevation = 8.dp,
        modifier = Modifier
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(28.dp))
            .border(width = 1.dp, color = Color.White.copy(alpha = 0.08f), shape = RoundedCornerShape(28.dp))
    ) {
        NavigationBarItem(
            selected = activeTab == JarvisTab.HUD,
            onClick = { onTabSelected(JarvisTab.HUD) },
            icon = { Icon(Icons.Default.Home, contentDescription = "HUD", modifier = Modifier.testTag("nav_hud")) },
            label = { Text("HUD", fontSize = 9.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = NeonCyan,
                selectedTextColor = NeonCyan,
                unselectedIconColor = SoftGrey,
                unselectedTextColor = SoftGrey,
                indicatorColor = Color.White.copy(alpha = 0.05f)
            )
        )

        NavigationBarItem(
            selected = activeTab == JarvisTab.CREDENTIALS,
            onClick = { onTabSelected(JarvisTab.CREDENTIALS) },
            icon = { Icon(Icons.Default.Lock, contentDescription = "Kasa", modifier = Modifier.testTag("nav_creds")) },
            label = { Text("Kasa", fontSize = 9.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = NeonCyan,
                selectedTextColor = NeonCyan,
                unselectedIconColor = SoftGrey,
                unselectedTextColor = SoftGrey,
                indicatorColor = Color.White.copy(alpha = 0.05f)
            )
        )

        NavigationBarItem(
            selected = activeTab == JarvisTab.SIMULATOR,
            onClick = { onTabSelected(JarvisTab.SIMULATOR) },
            icon = { Icon(Icons.Default.PlayArrow, contentDescription = "Terminal", modifier = Modifier.testTag("nav_simulator")) },
            label = { Text("Terminal", fontSize = 9.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = NeonCyan,
                selectedTextColor = NeonCyan,
                unselectedIconColor = SoftGrey,
                unselectedTextColor = SoftGrey,
                indicatorColor = Color.White.copy(alpha = 0.05f)
            )
        )

        NavigationBarItem(
            selected = activeTab == JarvisTab.SHORTCUTS,
            onClick = { onTabSelected(JarvisTab.SHORTCUTS) },
            icon = { Icon(Icons.Default.Settings, contentDescription = "Kısayollar", modifier = Modifier.testTag("nav_shortcuts")) },
            label = { Text("Kısayol", fontSize = 9.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = NeonCyan,
                selectedTextColor = NeonCyan,
                unselectedIconColor = SoftGrey,
                unselectedTextColor = SoftGrey,
                indicatorColor = Color.White.copy(alpha = 0.05f)
            )
        )

        NavigationBarItem(
            selected = activeTab == JarvisTab.LOGS,
            onClick = { onTabSelected(JarvisTab.LOGS) },
            icon = { Icon(Icons.Default.List, contentDescription = "Geçmiş", modifier = Modifier.testTag("nav_logs")) },
            label = { Text("Geçmiş", fontSize = 9.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = NeonCyan,
                selectedTextColor = NeonCyan,
                unselectedIconColor = SoftGrey,
                unselectedTextColor = SoftGrey,
                indicatorColor = Color.White.copy(alpha = 0.05f)
            )
        )

        NavigationBarItem(
            selected = activeTab == JarvisTab.BROWSER_USE,
            onClick = { onTabSelected(JarvisTab.BROWSER_USE) },
            icon = { Icon(Icons.Default.Search, contentDescription = "Tarayıcı", modifier = Modifier.testTag("nav_browser")) },
            label = { Text("Bulut", fontSize = 9.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = NeonCyan,
                selectedTextColor = NeonCyan,
                unselectedIconColor = SoftGrey,
                unselectedTextColor = SoftGrey,
                indicatorColor = Color.White.copy(alpha = 0.05f)
            )
        )
    }
}

@Composable
fun JarvisHudView(
    isListening: Boolean,
    isAnalyzing: Boolean,
    currentInput: String,
    jarvisResponse: String,
    typedInput: String,
    onTypedInputChange: (String) -> Unit,
    onSendTypedCommand: () -> Unit,
    onMicClicked: () -> Unit,
    viewModel: JarvisViewModel,
    speechManager: JarvisSpeechManager?
) {
    val shortcuts by viewModel.shortcuts.collectAsStateWithLifecycle()
    val context = LocalContext.current
    
    val orbPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            try {
                val serviceIntent = Intent(context, JarvisFloatingService::class.java)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
                Toast.makeText(context, "Jarvis Asistan Küresi aktif edildi sör.", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Servis başlatılamadı sör: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        } else {
            Toast.makeText(context, "Asistan küresi için Mikrofon izni gereklidir sör.", Toast.LENGTH_SHORT).show()
        }
    }

    val keyboardOptions = KeyboardActionsWithFloatingBubbles(
        shortcuts = shortcuts,
        onShortcutClick = { phrase ->
            viewModel.executeCommand(phrase, context) { speechManager?.speak(it) }
        }
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                JarvisCoreVisualizer(isListening = isListening, isThinking = isAnalyzing)
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Text(
                    text = if (isListening) "\"Dinliyorum, Efendim...\"" else if (isAnalyzing) "\"Düşünüyorum, Efendim...\"" else "\"Emrinizdeyim, sör.\"",
                    color = NeonCyan.copy(alpha = 0.9f),
                    fontSize = 15.sp,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Light,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Sound Waveforms
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val infiniteTransition = rememberInfiniteTransition(label = "audio_wave")
                    val waveHeight1 by infiniteTransition.animateFloat(
                        initialValue = 4f, targetValue = 12f,
                        animationSpec = infiniteRepeatable(tween(400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                        label = "wave1"
                    )
                    val waveHeight2 by infiniteTransition.animateFloat(
                        initialValue = 6f, targetValue = 20f,
                        animationSpec = infiniteRepeatable(tween(350, easing = LinearOutSlowInEasing), RepeatMode.Reverse),
                        label = "wave2"
                    )
                    val waveHeight3 by infiniteTransition.animateFloat(
                        initialValue = 4f, targetValue = 28f,
                        animationSpec = infiniteRepeatable(tween(500, easing = FastOutLinearInEasing), RepeatMode.Reverse),
                        label = "wave3"
                    )
                    val waveHeight4 by infiniteTransition.animateFloat(
                        initialValue = 5f, targetValue = 18f,
                        animationSpec = infiniteRepeatable(tween(300, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                        label = "wave4"
                    )
                    val waveHeight5 by infiniteTransition.animateFloat(
                        initialValue = 3f, targetValue = 10f,
                        animationSpec = infiniteRepeatable(tween(450, easing = LinearEasing), RepeatMode.Reverse),
                        label = "wave5"
                    )

                    val multiplier = if (isListening) 1.5f else if (isAnalyzing) 0.8f else 0.3f

                    Box(modifier = Modifier.size(3.dp, (waveHeight1 * multiplier).coerceAtLeast(3f).dp).background(NeonCyan.copy(alpha = 0.4f), CircleShape))
                    Box(modifier = Modifier.size(3.dp, (waveHeight2 * multiplier).coerceAtLeast(4f).dp).background(NeonCyan.copy(alpha = 0.6f), CircleShape))
                    Box(modifier = Modifier.size(3.dp, (waveHeight3 * multiplier).coerceAtLeast(6f).dp).background(NeonCyan, CircleShape))
                    Box(modifier = Modifier.size(3.dp, (waveHeight4 * multiplier).coerceAtLeast(4f).dp).background(NeonCyan.copy(alpha = 0.6f), CircleShape))
                    Box(modifier = Modifier.size(3.dp, (waveHeight5 * multiplier).coerceAtLeast(3f).dp).background(NeonCyan.copy(alpha = 0.4f), CircleShape))
                }
            }
        }

        // Live Speech / Command Input Display
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, NeonBlue.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                    .background(TechPanel, RoundedCornerShape(8.dp))
                    .padding(14.dp)
            ) {
                Text(
                    text = "GİRİŞ ALINDI:",
                    color = NeonBlue,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (currentInput.isEmpty()) "Komut bekleme durumunda sör..." else currentInput,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Jarvis Spoken Response Pane
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, NeonCyan.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                    .background(TechPanel.copy(alpha = 0.8f), RoundedCornerShape(8.dp))
                    .padding(14.dp)
            ) {
                Text(
                    text = "JARVIS SESLİ YANIT:",
                    color = NeonCyan,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = jarvisResponse,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // Speech & Keyboard Inputs Row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Glow circular Mic
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.verticalGradient(
                                if (isListening) listOf(ErrorRed, TechViolet)
                                else listOf(NeonCyan, NeonBlue)
                            )
                        )
                        .clickable { onMicClicked() }
                        .testTag("action_mic"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isListening) Icons.Default.Close else Icons.Default.PlayArrow,
                        contentDescription = "Mikrofon Komut",
                        tint = SpaceNavy,
                        modifier = Modifier.size(32.dp)
                    )
                }

                // Styled input field
                OutlinedTextField(
                    value = typedInput,
                    onValueChange = onTypedInputChange,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("typed_input"),
                    placeholder = { Text("Jarvis'e komut yazın sör...", color = SoftGrey, fontSize = 12.sp) },
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = Color.White,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace
                    ),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonCyan,
                        unfocusedBorderColor = NeonBlue.copy(alpha = 0.5f),
                        focusedLabelColor = NeonCyan,
                        unfocusedContainerColor = TechPanel,
                        focusedContainerColor = TechPanel
                    ),
                    shape = RoundedCornerShape(24.dp),
                    trailingIcon = {
                        IconButton(
                            onClick = onSendTypedCommand,
                            modifier = Modifier.testTag("send_button")
                        ) {
                            Icon(imageVector = Icons.Default.Send, contentDescription = "Send", tint = NeonCyan)
                        }
                    }
                )
            }
        }

        // Fast action bubble helpers!
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "HIZLI DENEYİM BUBBLE'LARI:",
                    color = SoftGrey,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(8.dp))
                keyboardOptions
            }
        }

        // Background Orb & Accessibility Permissions Control Center
        item {
            var isOrbActive by remember { mutableStateOf(false) }
            val isAccessibilityActive = JarvisAccessibilityService.isServiceRunning()
            
            // Check if service is already running on view load/interval
            LaunchedEffect(Unit) {
                val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
                if (manager != null) {
                    @Suppress("DEPRECATION")
                    for (service in manager.getRunningServices(Int.MAX_VALUE)) {
                        if (JarvisFloatingService::class.java.name == service.service.className) {
                            isOrbActive = true
                            break
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, TechViolet.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                    .background(TechPanel.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "ARKA PLAN ASİSTANI VE ERİŞİLEBİLİRLİK YÖNETİMİ",
                    color = TechViolet,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )

                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

                // 1. Draggable Floating Orb Service
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Sürüklenebilir Jarvis Küresi (Floating Orb)",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Arka planda dilediğiniz ekranda asistanı çağırın",
                            color = SoftGrey,
                            fontSize = 10.sp
                        )
                    }
                    Switch(
                        checked = isOrbActive,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = NeonCyan,
                            checkedTrackColor = NeonBlue.copy(alpha = 0.5f)
                        ),
                        onCheckedChange = { checked ->
                            if (checked) {
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M && !android.provider.Settings.canDrawOverlays(context)) {
                                    Toast.makeText(context, "Sistem üstünde çizim yetkisini onaylamalısınız sör.", Toast.LENGTH_LONG).show()
                                    val intent = Intent(
                                        android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        android.net.Uri.parse("package:${context.packageName}")
                                    )
                                    context.startActivity(intent)
                                } else {
                                    val hasMicPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                                    if (!hasMicPermission) {
                                        orbPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    } else {
                                        try {
                                            val serviceIntent = Intent(context, JarvisFloatingService::class.java)
                                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                                context.startForegroundService(serviceIntent)
                                            } else {
                                                context.startService(serviceIntent)
                                            }
                                            isOrbActive = true
                                            Toast.makeText(context, "Jarvis Asistan Küresi aktif edildi sör.", Toast.LENGTH_SHORT).show()
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Servis başlatılamadı sör: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }
                            } else {
                                try {
                                    val serviceIntent = Intent(context, JarvisFloatingService::class.java)
                                    context.stopService(serviceIntent)
                                    isOrbActive = false
                                    Toast.makeText(context, "Jarvis Asistan Küresi kapatıldı.", Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Servis durdurulamadı sör: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    )
                }

                // 2. Intelligent Screen Clicking (Accessibility)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Akıllı Ekran Tıklayıcı (Erişilebilirlik)",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (isAccessibilityActive) SuccessGreen.copy(alpha = 0.15f) else ErrorRed.copy(alpha = 0.15f))
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = if (isAccessibilityActive) "AKTİF" else "KAPALI",
                                    color = if (isAccessibilityActive) SuccessGreen else ErrorRed,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Text(
                            text = "Ekrandaki butonları Jarvis'in tıklamasını sağlar",
                            color = SoftGrey,
                            fontSize = 10.sp
                        )
                    }
                    Button(
                        onClick = {
                            val intent = Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
                            context.startActivity(intent)
                            Toast.makeText(context, "Jarvis Akıllı Erişim Asistanı'nı etkinleştirin sör.", Toast.LENGTH_LONG).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = if (isAccessibilityActive) Color.DarkGray else TechViolet),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text(
                            text = if (isAccessibilityActive) "Ayarlar" else "Aktif Et",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // 3. Smart API Key & Dynamic AI Engine Hub
        item {
            val sharedPrefs = context.getSharedPreferences("jarvis_prefs", Context.MODE_PRIVATE)
            
            // Set initial browser use key if not present
            val savedBuKey = sharedPrefs.getString("browser_use_api_key", "") ?: ""
            if (savedBuKey.isBlank()) {
                sharedPrefs.edit().putString("browser_use_api_key", "bu_0zjrgu1oPJQav7GSJU4LhiL6vxggjdTvCpdrnhh4ppY").apply()
            }
            
            var activeEngine by remember { mutableStateOf(sharedPrefs.getString("active_ai_engine", "GEMINI") ?: "GEMINI") }
            var keyPasterInput by remember { mutableStateOf("") }
            var detectedEngine by remember { mutableStateOf("Bilinmiyor") }
            
            // Retrieve actual values
            var geminiKey by remember { mutableStateOf(sharedPrefs.getString("custom_api_key", "AIzaSyA2j_H4g2JwzKgN9tbXMQH3Apl6Cks0nks") ?: "AIzaSyA2j_H4g2JwzKgN9tbXMQH3Apl6Cks0nks") }
            var openaiKey by remember { mutableStateOf(sharedPrefs.getString("openai_api_key", "") ?: "") }
            var openrouterKey by remember { mutableStateOf(sharedPrefs.getString("openrouter_api_key", "") ?: "") }
            var claudeKey by remember { mutableStateOf(sharedPrefs.getString("claude_api_key", "") ?: "") }
            var grokKey by remember { mutableStateOf(sharedPrefs.getString("grok_api_key", "") ?: "") }
            var browserUseKey by remember { mutableStateOf(sharedPrefs.getString("browser_use_api_key", "bu_0zjrgu1oPJQav7GSJU4LhiL6vxggjdTvCpdrnhh4ppY") ?: "bu_0zjrgu1oPJQav7GSJU4LhiL6vxggjdTvCpdrnhh4ppY") }
            
            var testResult by remember { mutableStateOf("") }
            var isTesting by remember { mutableStateOf(false) }
            var showConfigureKeys by remember { mutableStateOf(false) }
            val coroutineScope = rememberCoroutineScope()

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, NeonCyan.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                    .background(TechPanel.copy(alpha = 0.65f), RoundedCornerShape(12.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "AKILLI API KONTROL VE YAPAY ZEKA MERKEZİ",
                    color = NeonCyan,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )

                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

                // Active Engine Selection Layout
                Text(
                    text = "AKTİF YAPAY ZEKA MOTORU: $activeEngine",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val engines = listOf(
                        "GEMINI" to NeonCyan,
                        "OPENAI" to SuccessGreen,
                        "OPENROUTER" to TechViolet,
                        "CLAUDE" to ErrorRed,
                        "GROK" to Color(0xFFE2E2E2)
                    )
                    engines.forEach { (engine, eColor) ->
                        val isSelected = activeEngine == engine
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isSelected) eColor.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.03f))
                                .border(1.dp, if (isSelected) eColor else Color.White.copy(alpha = 0.08f), RoundedCornerShape(6.dp))
                                .clickable {
                                    activeEngine = engine
                                    sharedPrefs.edit().putString("active_ai_engine", engine).apply()
                                    Toast.makeText(context, "$engine aktif yapay zeka motoru olarak seçildi.", Toast.LENGTH_SHORT).show()
                                }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = engine,
                                color = if (isSelected) eColor else SoftGrey,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.05f))

                // Smart Key Detector Pool
                Text(
                    text = "AKILLI ANAHTAR ALGINAYICI (API DETECTOR)",
                    color = NeonBlue,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                
                OutlinedTextField(
                    value = keyPasterInput,
                    onValueChange = { valKey ->
                        keyPasterInput = valKey.trim()
                        if (valKey.isNotBlank()) {
                            detectedEngine = com.example.network.JarvisBrain.detectApiKeyType(valKey)
                        } else {
                            detectedEngine = "Bilinmiyor"
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("api_key_input"),
                    placeholder = { Text("Buraya herhangi bir API Key yapıştırın...", color = SoftGrey, fontSize = 10.sp) },
                    textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 11.sp, fontFamily = FontFamily.Monospace),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonBlue,
                        unfocusedBorderColor = NeonBlue.copy(alpha = 0.2f),
                        unfocusedContainerColor = SpaceNavy.copy(alpha = 0.5f),
                        focusedContainerColor = SpaceNavy.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(6.dp)
                )

                if (keyPasterInput.isNotBlank()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Tespit Edilen Tür: $detectedEngine",
                            color = if (detectedEngine != "UNKNOWN") SuccessGreen else ErrorRed,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                        
                        if (detectedEngine != "UNKNOWN") {
                            Button(
                                onClick = {
                                    val keyToSave = keyPasterInput
                                    when (detectedEngine) {
                                        "GEMINI" -> {
                                            geminiKey = keyToSave
                                            sharedPrefs.edit().putString("custom_api_key", keyToSave).apply()
                                        }
                                        "OPENAI" -> {
                                            openaiKey = keyToSave
                                            sharedPrefs.edit().putString("openai_api_key", keyToSave).apply()
                                        }
                                        "OPENROUTER" -> {
                                            openrouterKey = keyToSave
                                            sharedPrefs.edit().putString("openrouter_api_key", keyToSave).apply()
                                        }
                                        "CLAUDE" -> {
                                            claudeKey = keyToSave
                                            sharedPrefs.edit().putString("claude_api_key", keyToSave).apply()
                                        }
                                        "GROK" -> {
                                            grokKey = keyToSave
                                            sharedPrefs.edit().putString("grok_api_key", keyToSave).apply()
                                        }
                                        "BROWSER_USE" -> {
                                            browserUseKey = keyToSave
                                            sharedPrefs.edit().putString("browser_use_api_key", keyToSave).apply()
                                        }
                                    }
                                    Toast.makeText(context, "$detectedEngine anahtarı başarıyla kaydedildi sör!", Toast.LENGTH_SHORT).show()
                                    keyPasterInput = ""
                                    detectedEngine = "Bilinmiyor"
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                                contentPadding = PaddingValues(horizontal = 8.dp),
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Text("Derhal Kaydet", color = SpaceNavy, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.05f))

                // Configure Individual Keys Toggle
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { showConfigureKeys = !showConfigureKeys },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "MANUEL ANAHTAR YAPILANDIRMASI " + (if (showConfigureKeys) "▲" else "▼"),
                        color = SoftGrey,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (showConfigureKeys) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Gemini Field
                        Column {
                            Text("Google Gemini Key (AIzaSy...)", color = SoftGrey, fontSize = 9.sp)
                            OutlinedTextField(
                                value = geminiKey,
                                onValueChange = { geminiKey = it; sharedPrefs.edit().putString("custom_api_key", it).apply() },
                                modifier = Modifier.fillMaxWidth(),
                                textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 10.sp, fontFamily = FontFamily.Monospace),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NeonCyan)
                            )
                        }

                        // OpenAI Field
                        Column {
                            Text("OpenAI ChatGPT Key (sk-...)", color = SoftGrey, fontSize = 9.sp)
                            OutlinedTextField(
                                value = openaiKey,
                                onValueChange = { openaiKey = it; sharedPrefs.edit().putString("openai_api_key", it).apply() },
                                modifier = Modifier.fillMaxWidth(),
                                textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 10.sp, fontFamily = FontFamily.Monospace),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = SuccessGreen)
                            )
                        }

                        // OpenRouter Field
                        Column {
                            Text("OpenRouter Key (sk-or-...)", color = SoftGrey, fontSize = 9.sp)
                            OutlinedTextField(
                                value = openrouterKey,
                                onValueChange = { openrouterKey = it; sharedPrefs.edit().putString("openrouter_api_key", it).apply() },
                                modifier = Modifier.fillMaxWidth(),
                                textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 10.sp, fontFamily = FontFamily.Monospace),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = TechViolet)
                            )
                        }

                        // Anthropic Claude Field
                        Column {
                            Text("Anthropic Claude Key (sk-ant-...)", color = SoftGrey, fontSize = 9.sp)
                            OutlinedTextField(
                                value = claudeKey,
                                onValueChange = { claudeKey = it; sharedPrefs.edit().putString("claude_api_key", it).apply() },
                                modifier = Modifier.fillMaxWidth(),
                                textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 10.sp, fontFamily = FontFamily.Monospace),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ErrorRed)
                            )
                        }

                        // xAI Grok Field
                        Column {
                            Text("xAI Grok Key (xai-...)", color = SoftGrey, fontSize = 9.sp)
                            OutlinedTextField(
                                value = grokKey,
                                onValueChange = { grokKey = it; sharedPrefs.edit().putString("grok_api_key", it).apply() },
                                modifier = Modifier.fillMaxWidth(),
                                textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 10.sp, fontFamily = FontFamily.Monospace),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color.White)
                            )
                        }

                        // Browser Use Cloud Field
                        Column {
                            Text("Browser Use Cloud Key (bu_...)", color = SoftGrey, fontSize = 9.sp)
                            OutlinedTextField(
                                value = browserUseKey,
                                onValueChange = { browserUseKey = it; sharedPrefs.edit().putString("browser_use_api_key", it).apply() },
                                modifier = Modifier.fillMaxWidth(),
                                textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 10.sp, fontFamily = FontFamily.Monospace),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NeonCyan)
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = {
                            isTesting = true
                            testResult = "İlgili yapay zeka tüneli sorgulanıyor sör..."
                            coroutineScope.launch {
                                val activeKeyInField = when(activeEngine) {
                                    "GEMINI" -> geminiKey
                                    "OPENAI" -> openaiKey
                                    "OPENROUTER" -> openrouterKey
                                    "CLAUDE" -> claudeKey
                                    "GROK" -> grokKey
                                    else -> ""
                                }
                                val result = com.example.network.JarvisBrain.testConnection(context, activeKeyInField)
                                testResult = result
                                isTesting = false
                            }
                        },
                        enabled = !isTesting,
                        colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text(
                            text = if (isTesting) "Kontrol Ediliyor..." else "Bağlantıyı Test Et ($activeEngine)",
                            color = SpaceNavy,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (testResult.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (testResult.contains("BAŞARILI")) SuccessGreen.copy(alpha = 0.12f)
                                else ErrorRed.copy(alpha = 0.12f)
                            )
                            .padding(8.dp)
                    ) {
                        Text(
                            text = testResult,
                            color = if (testResult.contains("BAŞARILI")) SuccessGreen else ErrorRed,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 14.sp
                        )
                    }
                }
            }
        }
    }
}

private data class SuggestionItem(
    val command: String,
    val label: String,
    val color: Color,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

@Composable
fun KeyboardActionsWithFloatingBubbles(
    shortcuts: List<VoiceShortcut>,
    onShortcutClick: (String) -> Unit
) {
    val items = listOf(
        SuggestionItem(
            command = "bu videoyu whatsapp'tan ahmet'e gönder",
            label = "WhatsApp'tan Ahmet'e Gönder",
            color = Color(0xFF10B981),
            icon = Icons.Default.Share
        ),
        SuggestionItem(
            command = "web sitesindeki bilgileri kopyalayıp notlar uygulamasına yapıştır",
            label = "Bilgiyi Notlara Aktar",
            color = Color(0xFFF59E0B),
            icon = Icons.Default.Edit
        ),
        SuggestionItem(
            command = "feneri aç sör",
            label = "Telefon Fenerini Aç sör",
            color = Color(0xFFFFD700),
            icon = Icons.Default.Warning
        ),
        SuggestionItem(
            command = "wifiyi kapat sör",
            label = "WiFi/Kablosuz Kapat sör",
            color = Color(0xFF8B5CF6),
            icon = Icons.Default.Refresh
        ),
        SuggestionItem(
            command = "youtube'da tarkan çalın",
            label = "Youtube'da Tarkan Çalın",
            color = Color(0xFFEF4444),
            icon = Icons.Default.PlayArrow
        ),
        SuggestionItem(
            command = "google'da yapay zeka ara",
            label = "Google'da Haber Ara",
            color = Color(0xFF3B82F6),
            icon = Icons.Default.Search
        )
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items.chunked(2).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                rowItems.forEach { item ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF141619))
                            .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(16.dp))
                            .clickable { onShortcutClick(item.command) }
                            .padding(12.dp)
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            horizontalAlignment = Alignment.Start
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(item.color.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = item.label,
                                    tint = item.color,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Text(
                                text = item.label,
                                color = Color.White.copy(alpha = 0.9f),
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
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

@Composable
fun JarvisCredentialsView(viewModel: JarvisViewModel) {
    val credentials by viewModel.credentials.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var siteName by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "KASA (CREDENTIAL VAULT)",
                color = NeonCyan,
                fontSize = 14.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Otomatik şifre doldurma komutları için buraya hesap enjekte edin.",
                color = SoftGrey,
                fontSize = 11.sp
            )
        }

        // Add form
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, NeonBlue.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                    .background(TechPanel, RoundedCornerShape(8.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "YENİ HESAP EKLE",
                    color = NeonBlue,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )

                OutlinedTextField(
                    value = siteName,
                    onValueChange = { siteName = it },
                    label = { Text("Site / Uygulama Adı (örn: Netflix, Spotify)", fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("add_site"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonCyan,
                        unfocusedBorderColor = SoftGrey.copy(alpha = 0.4f),
                        focusedLabelColor = NeonCyan
                    ),
                    shape = RoundedCornerShape(8.dp)
                )

                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("E-Posta / Kullanıcı Adı", fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("add_user"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonCyan,
                        unfocusedBorderColor = SoftGrey.copy(alpha = 0.4f),
                        focusedLabelColor = NeonCyan
                    ),
                    shape = RoundedCornerShape(8.dp)
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Şifre", fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("add_pw"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonCyan,
                        unfocusedBorderColor = SoftGrey.copy(alpha = 0.4f),
                        focusedLabelColor = NeonCyan
                    ),
                    shape = RoundedCornerShape(8.dp),
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation()
                )

                Button(
                    onClick = {
                        if (siteName.isNotBlank() && username.isNotBlank() && password.isNotBlank()) {
                            viewModel.addCredential(siteName, username, password)
                            siteName = ""
                            username = ""
                            password = ""
                            Toast.makeText(context, "Hesap Kasaya enjekte edildi, sör.", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Lütfen tüm alanları doldurun sör.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("submit_cred"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonCyan,
                        contentColor = SpaceNavy
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "Add", tint = SpaceNavy)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Kasaya Başarıyla Enjekte Et", fontWeight = FontWeight.Bold)
                }
            }
        }

        // List credentials
        item {
            Text(
                text = "KASA KAĞITLARI (${credentials.size} Hesap)",
                color = SoftGrey,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
        }

        if (credentials.isEmpty()) {
            item {
                Text(
                    text = "Güvenli kasada kayıt bulunamadı sör. Yukarıdan netflix, google, spotify vb. kimlik bilgilerini ekleyebilirsiniz.",
                    color = SoftGrey.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 24.dp)
                )
            }
        } else {
            items(credentials) { cred ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, NeonCyan.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                        .background(TechPanel, RoundedCornerShape(8.dp))
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(NeonBlue.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = cred.logoText,
                            color = NeonBlue,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = cred.siteName,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Kullanıcı: ${cred.username}",
                            color = SoftGrey,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Şifre: •••••••••",
                            color = NeonCyan,
                            fontSize = 11.sp
                        )
                    }

                    IconButton(
                        onClick = { viewModel.deleteCredential(cred.id) },
                        modifier = Modifier.testTag("delete_cred_${cred.id}")
                    ) {
                        Icon(imageVector = Icons.Default.Delete, contentDescription = "Sil", tint = ErrorRed)
                    }
                }
            }
        }
    }
}

@Composable
fun JarvisSimulatorView(viewModel: JarvisViewModel) {
    val steps by viewModel.simulationSteps.collectAsStateWithLifecycle()
    val isRunning by viewModel.isSimulationRunning.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "MİLYON DOSYALIK OTO-KONTROL SİMÜLATÖRÜ",
                color = NeonCyan,
                fontSize = 14.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Jarvis'in cihazınızda gerçekleştirdiği bot/otomasyon logları.",
                color = SoftGrey,
                fontSize = 11.sp
            )
        }

        // Mock phone frame visualizer
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .border(2.dp, NeonCyan, RoundedCornerShape(12.dp))
                    .background(Color.Black)
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                // Background coordinates matrix
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val cellsHor = 12
                    val cellsVert = 6
                    val stepX = size.width / cellsHor
                    val stepY = size.height / cellsVert
                    for (i in 0..cellsHor) {
                        drawLine(
                            color = NeonBlue.copy(alpha = 0.12f),
                            start = Offset(i * stepX, 0f),
                            end = Offset(i * stepX, size.height),
                            strokeWidth = 1f
                        )
                    }
                    for (i in 0..cellsVert) {
                        drawLine(
                            color = NeonBlue.copy(alpha = 0.12f),
                            start = Offset(0f, i * stepY),
                            end = Offset(size.width, i * stepY),
                            strokeWidth = 1f
                        )
                    }
                }

                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("[SİMÜLE CİHAZ]", color = NeonCyan, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                        Text(if (isRunning) "DURUM: AKTİF" else "DURUM: BEKLEMEDE", color = if (isRunning) SuccessGreen else SoftGrey, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                    }

                    if (isRunning) {
                        CircularProgressIndicator(color = NeonCyan, modifier = Modifier.size(32.dp))
                        Text(
                            text = "KOORDİNAT PARSE VE TIKLAMA ENJEKSİYONU SÜRÜYOR...",
                            color = NeonBlue,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Ready",
                            tint = NeonCyan.copy(alpha = 0.6f),
                            modifier = Modifier.size(40.dp)
                        )
                        Text(
                            text = "Bekleniyor. Ana ekrandan bir komut vererek otomasyon simülasyonunu başlatın sör.",
                            color = SoftGrey,
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                    }

                    Text("[X: 0256 // Y: 0482]", color = NeonCyan.copy(alpha = 0.5f), fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }

        // Scrollable steps terminal
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, SoftGrey.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                    .background(TechPanel, RoundedCornerShape(8.dp))
                    .padding(14.dp)
            ) {
                Text(
                    text = "SİSTEM LOG KONSOLU (CEYVİS ENGINE)",
                    color = NeonCyan,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(10.dp))

                if (steps.isEmpty()) {
                    Text(
                        text = "Kaydedilmiş simülasyon adımı yok. Komut verdiğinizde sistem motoru adımları buraya çıkartır sör.",
                        color = SoftGrey.copy(alpha = 0.5f),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                } else {
                    steps.forEach { step ->
                        Text(
                            text = step,
                            color = if (step.contains("[BAŞARILI]") || step.contains("[KASA]")) SuccessGreen else if (step.contains("[HATA]") || step.contains("[UYARI]")) ErrorRed else Color.White,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun JarvisShortcutsView(viewModel: JarvisViewModel) {
    val shortcuts by viewModel.shortcuts.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var name by remember { mutableStateOf("") }
    var phrase by remember { mutableStateOf("") }
    var action by remember { mutableStateOf("OPEN_YOUTUBE") }
    var targetUrl by remember { mutableStateOf("") }

    val options = listOf("OPEN_YOUTUBE", "SEARCH_GOOGLE", "AUTO_LOGIN")

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "KISAYOLLAR (CUSTOM SHORTCUTS)",
                color = NeonCyan,
                fontSize = 14.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Özel sesli yönergelerinize özel reaksiyonlar atayın.",
                color = SoftGrey,
                fontSize = 11.sp
            )
        }

        // Add form
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, NeonBlue.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                    .background(TechPanel, RoundedCornerShape(8.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "YENİ SES KISAYOLU",
                    color = NeonBlue,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Kısayol Başlığı (örn: Rap Şarkıları aç)", fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("add_shortcut_name"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonCyan,
                        unfocusedBorderColor = SoftGrey.copy(alpha = 0.4f),
                        focusedLabelColor = NeonCyan
                    ),
                    shape = RoundedCornerShape(8.dp)
                )

                OutlinedTextField(
                    value = phrase,
                    onValueChange = { phrase = it },
                    label = { Text("Tetikleyici Sözcük (örn: Jarvis Rap Aç)", fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("add_shortcut_phrase"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonCyan,
                        unfocusedBorderColor = SoftGrey.copy(alpha = 0.4f),
                        focusedLabelColor = NeonCyan
                    ),
                    shape = RoundedCornerShape(8.dp)
                )

                Column {
                    Text("Reaksiyon Tipi:", color = SoftGrey, fontSize = 11.sp, modifier = Modifier.padding(bottom = 4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        options.forEach { opt ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(
                                        1.dp,
                                        if (action == opt) NeonCyan else SoftGrey.copy(alpha = 0.3f),
                                        RoundedCornerShape(8.dp)
                                    )
                                    .background(if (action == opt) SpaceNavy else TechPanel)
                                    .clickable { action = opt }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = when(opt) {
                                        "OPEN_YOUTUBE" -> "YouTube"
                                        "SEARCH_GOOGLE" -> "Google"
                                        else -> "Oto Giriş"
                                    },
                                    color = if (action == opt) NeonCyan else Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = targetUrl,
                    onValueChange = { targetUrl = it },
                    label = { Text("Hedef Parametre / URL (İsteğe Bağlı)", fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("add_shortcut_target"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonCyan,
                        unfocusedBorderColor = SoftGrey.copy(alpha = 0.4f),
                        focusedLabelColor = NeonCyan
                    ),
                    shape = RoundedCornerShape(8.dp)
                )

                Button(
                    onClick = {
                        if (name.isNotBlank() && phrase.isNotBlank()) {
                            viewModel.addShortcut(name, phrase, action, targetUrl)
                            name = ""
                            phrase = ""
                            targetUrl = ""
                            Toast.makeText(context, "Kısayol eklendi sör.", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Lütfen gerekli alanları doldurun sör.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("submit_shortcut"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonCyan,
                        contentColor = SpaceNavy
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "Add", tint = SpaceNavy)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Kısayolu Sisteme Enjekte Et", fontWeight = FontWeight.Bold)
                }
            }
        }

        // Shortcuts list
        item {
            Text(
                text = "KAYITLI ÖZEL TETİKLEYİCİLER",
                color = SoftGrey,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
        }

        if (shortcuts.isEmpty()) {
            item {
                Text(
                    text = "Kayıtlı özel kısayol tetikleyicisi bulunmamaktadır sör.",
                    color = SoftGrey.copy(alpha = 0.5f),
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
                )
            }
        } else {
            items(shortcuts) { sc ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, NeonBlue.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                        .background(TechPanel, RoundedCornerShape(8.dp))
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = sc.name,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Ses Tetikleyici: \"${sc.phrase}\"",
                            color = NeonCyan,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "Eylem: ${sc.targetAction} | Parametre: ${sc.targetUrl.ifEmpty { "YOK" }}",
                            color = SoftGrey,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    IconButton(
                        onClick = { viewModel.deleteShortcut(sc.id) },
                        modifier = Modifier.testTag("delete_shortcut_${sc.id}")
                    ) {
                        Icon(imageVector = Icons.Default.Delete, contentDescription = "Sil", tint = ErrorRed)
                    }
                }
            }
        }
    }
}

@Composable
fun JarvisLogsView(viewModel: JarvisViewModel) {
    val logs by viewModel.logs.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "KOMUT GEÇMİŞİ LOGLARI",
                        color = NeonCyan,
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Sistem üzerinde çalıştırılan sesli komut dökümü.",
                        color = SoftGrey,
                        fontSize = 11.sp
                    )
                }

                Button(
                    onClick = { viewModel.clearLogs() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ErrorRed.copy(alpha = 0.2f),
                        contentColor = ErrorRed
                    ),
                    modifier = Modifier.testTag("clear_logs"),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("Temizle", fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }

        if (logs.isEmpty()) {
            item {
                Text(
                    text = "Lokal işlem günlüğü veritabanı boş sör.",
                    color = SoftGrey.copy(alpha = 0.5f),
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 40.dp)
                )
            }
        } else {
            items(logs) { log ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, NeonBlue.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                        .background(TechPanel, RoundedCornerShape(8.dp))
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "EYLEM: ${log.intentType}",
                            color = NeonCyan,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(SuccessGreen.copy(alpha = 0.2f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "SİMÜLE",
                                color = SuccessGreen,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Ses Girişi: \"${log.commandText}\"",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Cevap: ${log.resultMessage}",
                        color = SoftGrey,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal
                    )
                }
            }
        }
    }
}

@Composable
fun JarvisBrowserUseView(viewModel: JarvisViewModel) {
    val logs by viewModel.browserUseLogs.collectAsStateWithLifecycle()
    val currentUrl by viewModel.browserUseCurrentUrl.collectAsStateWithLifecycle()
    val screenshotName by viewModel.browserUseScreenshotName.collectAsStateWithLifecycle()
    val activeTask by viewModel.browserUseActiveTask.collectAsStateWithLifecycle()
    val isRunning by viewModel.isBrowserUseRunning.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val sharedPrefs = context.getSharedPreferences("jarvis_prefs", Context.MODE_PRIVATE)
    var browserUseKey by remember { mutableStateOf(sharedPrefs.getString("browser_use_api_key", "bu_0zjrgu1oPJQav7GSJU4LhiL6vxggjdTvCpdrnhh4ppY") ?: "bu_0zjrgu1oPJQav7GSJU4LhiL6vxggjdTvCpdrnhh4ppY") }
    var taskInput by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp)
    ) {
        item {
            Column {
                Text(
                    text = "BROWSER USE CLOUD KONTROL",
                    color = NeonCyan,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Bulut üzerinde gerçek zamanlı otomasyon ve tarayıcı sürüş merkezi.",
                    color = SoftGrey,
                    fontSize = 11.sp
                )
            }
        }

        // Google AI Studio Key Rotator Autopilot
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, NeonCyan.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                    .background(TechPanel.copy(alpha = 0.8f), RoundedCornerShape(12.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "GOOGLE AI STUDIO OTOPİLOTU",
                            color = NeonCyan,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Anahtarın süresi dolduğunda veya tıkandığında Jarvis kendi kendine yeni anahtar üretir.",
                            color = SoftGrey,
                            fontSize = 9.sp,
                            lineHeight = 12.sp
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(SuccessGreen.copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "KORUMA AKTİF",
                            color = SuccessGreen,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.05f))

                Button(
                    onClick = {
                        viewModel.startApiKeyAutoRenewalTask(context)
                    },
                    modifier = Modifier.fillMaxWidth().height(36.dp).testTag("btn_autopilot_renew"),
                    colors = ButtonDefaults.buttonColors(containerColor = TechViolet, contentColor = Color.White),
                    shape = RoundedCornerShape(8.dp),
                    enabled = !isRunning
                ) {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Autopilot",
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Şimdi Otomatik API Key Yenile (Otopilot)",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }

        // 1. Browser Use API Key config slot
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, NeonCyan.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                    .background(TechPanel.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "BULUT CRITICAL API KEY YAPILANDIRMASI",
                    color = NeonCyan,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
                
                OutlinedTextField(
                    value = browserUseKey,
                    onValueChange = { newValue ->
                        browserUseKey = newValue
                        sharedPrefs.edit().putString("browser_use_api_key", newValue).apply()
                    },
                    modifier = Modifier.fillMaxWidth().testTag("browser_use_key_field"),
                    placeholder = { Text("Browser Use Cloud Key (bu_...)...", color = SoftGrey, fontSize = 11.sp) },
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = Color.White,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    ),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonCyan,
                        unfocusedBorderColor = NeonBlue.copy(alpha = 0.2f),
                        unfocusedContainerColor = SpaceNavy,
                        focusedContainerColor = SpaceNavy
                    ),
                    shape = RoundedCornerShape(6.dp)
                )
                Text(
                    text = "Bulut tarayıcınızı yönetmek için API anahtarını anında değiştirip kaydedebilirsiniz sör.",
                    color = SoftGrey.copy(alpha = 0.7f),
                    fontSize = 9.sp
                )
            }
        }

        // 2. Browser Simulator Live Display Console
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, NeonBlue.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                    .background(SpaceNavy, RoundedCornerShape(12.dp))
            ) {
                // Top header representing Chrome UI
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1E2022))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Traffic lights icons
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(ErrorRed))
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFFF59E0B)))
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(SuccessGreen))

                    Spacer(modifier = Modifier.width(8.dp))

                    // Address Pill
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF2C2E31))
                            .border(width = 0.5.dp, color = Color.White.copy(alpha = 0.08f), shape = RoundedCornerShape(14.dp))
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Secure Connection",
                                tint = SuccessGreen,
                                modifier = Modifier.size(10.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = currentUrl,
                                color = SoftGrey,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(if (isRunning) NeonCyan.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.05f))
                            .clickable { },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Reload",
                            tint = if (isRunning) NeonCyan else SoftGrey,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }

                // Main simulated visual rendering of the page
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .background(Color(0xFF141517))
                        .padding(14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    when (screenshotName) {
                        "empty" -> {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Cloud Browser",
                                    tint = SoftGrey.copy(alpha = 0.3f),
                                    modifier = Modifier.size(40.dp)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "BULUT TARAYICI BAĞLANTISI YOK",
                                    color = SoftGrey.copy(alpha = 0.8f),
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Yukarıdan görev tetikleyerek oturumu canlandırın.",
                                    color = SoftGrey.copy(alpha = 0.5f),
                                    fontSize = 9.sp
                                )
                            }
                        }
                        "google_page" -> {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Text(
                                    text = "G o o g l e",
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Box(
                                    modifier = Modifier
                                        .width(220.dp)
                                        .height(24.dp)
                                        .border(0.5.dp, SoftGrey.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                                        .background(Color(0xFF2C2E31))
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text(text = activeTask, color = Color.White, fontSize = 9.sp)
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Box(modifier = Modifier.background(Color(0xFF202124), RoundedCornerShape(4.dp)).padding(horizontal = 8.dp, vertical = 4.dp)) {
                                        Text("Google'da Ara", color = SoftGrey, fontSize = 8.sp)
                                    }
                                }
                            }
                        }
                        "google_results" -> {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("Arama Sonuçları: \"$activeTask\"", color = SoftGrey, fontSize = 9.sp)
                                repeat(3) { index ->
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        Text(
                                            text = "Grup $index: Sanal robot aramalarını inceliyor sör.",
                                            color = NeonCyan,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Saniyeler içinde veriler parse edilecektir... AI bulut tüneli.",
                                            color = SoftGrey,
                                            fontSize = 9.sp
                                        )
                                    }
                                }
                            }
                        }
                        "target_site" -> {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Sayfa İçeriği Taranıyor", color = SuccessGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    Box(modifier = Modifier.background(SuccessGreen.copy(0.2f)).padding(horizontal = 4.dp, vertical = 2.dp)) {
                                        Text("VERİ SEKTÖRÜ", color = SuccessGreen, fontSize = 8.sp)
                                    }
                                }
                                Text(
                                    text = "Bulunulan Adres: $currentUrl",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = "Tarayıcı robotumuz ($browserUseKey) üzerinden tüm link ve yazıları analiz ederek hafızaya çekiyor.",
                                    color = SoftGrey,
                                    fontSize = 9.sp,
                                    lineHeight = 13.sp
                                )
                            }
                        }
                        "task_done" -> {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(SuccessGreen.copy(alpha = 0.15f))
                                        .border(1.dp, SuccessGreen, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(imageVector = Icons.Default.Check, contentDescription = "Done", tint = SuccessGreen)
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "BULUT OTOMASYONU BAŞARIYLA TAMAMLANDI!",
                                    color = SuccessGreen,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Özet bilgi sistem beynine enjekte edildi sör.",
                                    color = SoftGrey,
                                    fontSize = 9.sp
                                )
                            }
                        }
                        else -> {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("Arayüz canlanıyor...", color = NeonCyan)
                            }
                        }
                    }
                }

                // Interactive Bottom Action Strip inside simulator
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1E2022))
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { viewModel.manualBrowserUseClick() },
                        enabled = isRunning,
                        colors = ButtonDefaults.buttonColors(containerColor = TechViolet.copy(alpha = 0.2f), contentColor = TechViolet),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Text("Manuel Click", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = { viewModel.manualBrowserUseScroll() },
                        enabled = isRunning,
                        colors = ButtonDefaults.buttonColors(containerColor = TechViolet.copy(alpha = 0.2f), contentColor = TechViolet),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Text("Aşağı Kaydır", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    if (isRunning) {
                        Button(
                            onClick = { viewModel.stopBrowserUseTask() },
                            colors = ButtonDefaults.buttonColors(containerColor = ErrorRed.copy(alpha = 0.2f), contentColor = ErrorRed),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Text("Durdur", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // 3. Command dispatcher field
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, NeonBlue.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                    .background(TechPanel.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "GÖREV ENJEKSİYONU",
                    color = NeonBlue,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )

                OutlinedTextField(
                    value = taskInput,
                    onValueChange = { taskInput = it },
                    modifier = Modifier.fillMaxWidth().testTag("browser_use_query_input"),
                    placeholder = { Text("Bulutta yapmasını istediğiniz işlemi yazın...", color = SoftGrey, fontSize = 11.sp) },
                    textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 12.sp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonCyan,
                        unfocusedBorderColor = SoftGrey.copy(alpha = 0.3f)
                    ),
                    shape = RoundedCornerShape(8.dp)
                )

                Button(
                    onClick = {
                        if (taskInput.isNotBlank()) {
                            viewModel.startBrowserUseCloudTask(taskInput, context)
                            taskInput = ""
                        } else {
                            Toast.makeText(context, "Lütfen bir görev yazın sör.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(36.dp).testTag("browser_use_submit"),
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = SpaceNavy),
                    shape = RoundedCornerShape(8.dp),
                    enabled = !isRunning
                ) {
                    Text(
                        text = if (isRunning) "Görev Sürdürülüyor..." else "Bulut Görevini Başlat",
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }
            }
        }

        // 4. Live Log Term stream
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "İŞLEM LOG YAYINI (${logs.size} log)",
                    color = SoftGrey,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )

                Button(
                    onClick = { viewModel.clearBrowserUseLogs() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ErrorRed.copy(alpha = 0.15f),
                        contentColor = ErrorRed
                    ),
                    modifier = Modifier.height(26.dp).testTag("browser_use_clear"),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                ) {
                    Text("Temizle", fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }

        if (logs.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                        .background(TechPanel.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Aktif işlem günlüğü henüz mevcut değil sör.",
                        color = SoftGrey.copy(alpha = 0.5f),
                        fontSize = 11.sp
                    )
                }
            }
        } else {
            items(logs) { logLine ->
                val logColor = when {
                    logLine.contains("[HATA]") -> ErrorRed
                    logLine.contains("[TAMAMLANDI]") -> SuccessGreen
                    logLine.contains("[SİSTEM]") -> TechViolet
                    logLine.contains("[ETKİLEŞİM]") -> NeonCyan
                    logLine.contains("[TARAYICI]") -> NeonBlue
                    else -> SoftGrey
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(0.5.dp, logColor.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                        .background(Color(0xFF0F1113), RoundedCornerShape(6.dp))
                        .padding(8.dp)
                ) {
                    Text(
                        text = logLine,
                        color = logColor,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 14.sp
                    )
                }
            }
        }
    }
}
