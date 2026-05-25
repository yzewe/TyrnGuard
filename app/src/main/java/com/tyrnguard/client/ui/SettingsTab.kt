package com.tyrnguard.client.ui

import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tyrnguard.client.SettingsStore
import com.tyrnguard.client.TunnelManager
import com.tyrnguard.client.TunnelService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import java.util.UUID

data class TyrnGuardServer(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val ip: String,
    val password: String,
    val dtlsPort: Int = 56000,
    val wgPort: Int = 56001
)

enum class WidgetType(val title: String, val icon: ImageVector, val isWide: Boolean = false) {
    PING("РџРёРЅРі", Icons.Default.NetworkPing),
    SESSION("РЎРµСЃСЃРёСЏ", Icons.Default.Timer),
    WORKERS("Р’РѕСЂРєРµСЂС‹", Icons.Default.Hub),
    SPEED("РЎРєРѕСЂРѕСЃС‚СЊ", Icons.Default.Download),
    TRAFFIC("РўСЂР°С„РёРє", Icons.Default.DataUsage),
    HEALTH("РЎС‚Р°С‚СѓСЃ", Icons.Default.HealthAndSafety),
    GRAPH("Р“СЂР°С„РёРє СЃРµС‚Рё", Icons.Default.QueryStats, isWide = true)
}

private const val RJS_TEMPORARILY_DISABLED = true

private fun formatRate(bytesPerSecond: Long): String {
    if (bytesPerSecond <= 0L) return "0 KB/s"
    val kb = bytesPerSecond / 1024f
    return if (kb >= 1024f) String.format("%.1f MB/s", kb / 1024f) else String.format("%.0f KB/s", kb)
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 MB"
    val mb = bytes / (1024f * 1024f)
    return if (mb >= 1024f) String.format("%.1f GB", mb / 1024f) else String.format("%.1f MB", mb)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsTab(snackbarHostState: SnackbarHostState) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val settingsStore = remember { SettingsStore(context) }
    val prefs = context.getSharedPreferences("tyrnguard_widgets", Context.MODE_PRIVATE)

    val tunnelRunning by TunnelManager.running.collectAsStateWithLifecycle()
    val cooldownSeconds by TunnelManager.cooldownSeconds.collectAsStateWithLifecycle()
    val currentPing by TunnelManager.currentPingMs.collectAsStateWithLifecycle()
    val currentSpeed by TunnelManager.currentSpeedBytes.collectAsStateWithLifecycle()
    val totalTraffic by TunnelManager.totalTrafficBytes.collectAsStateWithLifecycle()
    val activeWorkers by TunnelManager.activeWorkers.collectAsStateWithLifecycle()

    val peer by settingsStore.peer.collectAsStateWithLifecycle("")
    val hashes by settingsStore.vkHashes.collectAsStateWithLifecycle("")
    val workers by settingsStore.workersPerHash.collectAsStateWithLifecycle(24)
    val port by settingsStore.listenPort.collectAsStateWithLifecycle(9000)
    val sni by settingsStore.sni.collectAsStateWithLifecycle("")
    val connPass by settingsStore.connectionPassword.collectAsStateWithLifecycle("")
    val protocol by settingsStore.protocol.collectAsStateWithLifecycle("udp")
    val captchaMode by settingsStore.captchaMode.collectAsStateWithLifecycle("wv")
    val captchaMethod by settingsStore.captchaSolveMethod.collectAsStateWithLifecycle("auto")
    val savedServersJson by settingsStore.savedServersJson.collectAsStateWithLifecycle("[]")

    val serverList = remember { mutableStateListOf<TyrnGuardServer>() }
    var activeWidgetList by remember { mutableStateOf(listOf<WidgetType>()) }
    var availableWidgetList by remember { mutableStateOf(listOf<WidgetType>()) }

    var pendingStartAfterVpnPermission by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val savedOrder = prefs.getString("order", null)
        if (savedOrder != null) {
            try {
                val active = savedOrder.split(",").mapNotNull { name -> WidgetType.entries.find { it.name == name } }
                activeWidgetList = active
                availableWidgetList = WidgetType.entries - active.toSet()
            } catch (e: Exception) {
                activeWidgetList = WidgetType.entries
                availableWidgetList = emptyList()
            }
        } else {
            activeWidgetList = WidgetType.entries
            availableWidgetList = emptyList()
        }
    }

    LaunchedEffect(savedServersJson) {
        serverList.clear()
        try {
            val array = JSONArray(savedServersJson)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                serverList.add(
                    TyrnGuardServer(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        name = obj.optString("name"),
                        ip = obj.optString("ip").trim(),
                        password = obj.optString("password").trim(),
                        dtlsPort = obj.optInt("dtlsPort", 56000).coerceIn(1, 65535),
                        wgPort = obj.optInt("wgPort", 56001).coerceIn(1, 65535)
                    )
                )
            }
        } catch (_: Exception) {}
    }

    val activeServer = remember(peer, savedServersJson) { serverList.find { it.ip == peer.trim() } }

    var showServerBottomSheet by remember { mutableStateOf(false) }
    var showDiagnosticDialog by remember { mutableStateOf(false) }
    var sessionSeconds by rememberSaveable { mutableIntStateOf(0) }
    var isEditMode by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(tunnelRunning) {
        if (tunnelRunning) { while (true) { delay(1000); sessionSeconds++ } } else sessionSeconds = 0
    }

    val timerString = String.format("%02d:%02d:%02d", sessionSeconds / 3600, (sessionSeconds % 3600) / 60, sessionSeconds % 60)

    fun saveServers() {
        val array = JSONArray()
        serverList.forEach {
            array.put(
                JSONObject().apply {
                    put("id", it.id)
                    put("name", it.name)
                    put("ip", it.ip.trim())
                    put("password", it.password.trim())
                    put("dtlsPort", it.dtlsPort.coerceIn(1, 65535))
                    put("wgPort", it.wgPort.coerceIn(1, 65535))
                }
            )
        }
        scope.launch { settingsStore.saveServersList(array.toString()) }
    }

    fun updateWidgetOrder(newList: List<WidgetType>) {
        activeWidgetList = newList
        availableWidgetList = WidgetType.entries - newList.toSet()
        prefs.edit().putString("order", newList.joinToString(",") { it.name }).apply()
    }

    fun startTunnel() {
        if (peer.isBlank() || hashes.isBlank()) { 
            Toast.makeText(context, "РЎРЅР°С‡Р°Р»Р° РІС‹Р±РµСЂРёС‚Рµ СЃРµСЂРІРµСЂ Рё РґРѕР±Р°РІСЊС‚Рµ VK С…РµС€Рё РІ РЅР°СЃС‚СЂРѕР№РєР°С…!", Toast.LENGTH_LONG).show()
            return 
        }
        
        val effectiveServerDtlsPort = activeServer?.dtlsPort?.coerceIn(1, 65535) ?: 56000
        val effectiveLocalPort = port.coerceIn(1, 65535)
        val finalPeer = if (peer.contains(":")) peer else "${peer.trim()}:$effectiveServerDtlsPort"

        val effectiveCaptchaMode = if (RJS_TEMPORARILY_DISABLED) "wv" else captchaMode
        val effectiveCaptchaMethod = if (effectiveCaptchaMode == "wv" && captchaMethod == "manual") "manual" else "auto"

        val intent = Intent(context, TunnelService::class.java).apply {
            action = "START"
            putExtra("peer", finalPeer)
            putExtra("vk_hashes", hashes)
            putExtra("workers_per_hash", workers)
            putExtra("port", effectiveLocalPort)
            putExtra("sni", sni)
            putExtra("connection_password", connPass.trim())
            putExtra("protocol", protocol)
            putExtra("captcha_mode", effectiveCaptchaMode)
            putExtra("captcha_solve_method", effectiveCaptchaMethod)
        }
        if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent) else context.startService(intent)
    }

    val vpnPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (pendingStartAfterVpnPermission) {
            pendingStartAfterVpnPermission = false
            if (VpnService.prepare(context) == null) startTunnel()
            else Toast.makeText(context, "VPN-СЂР°Р·СЂРµС€РµРЅРёРµ РЅРµ РІС‹РґР°РЅРѕ", Toast.LENGTH_SHORT).show()
        }
    }

    fun requestVpnAndStart() {
        val vpnIntent = VpnService.prepare(context)
        if (vpnIntent != null) {
            pendingStartAfterVpnPermission = true
            vpnPermissionLauncher.launch(vpnIntent)
        } else startTunnel()
    }

    val infiniteTransition = rememberInfiniteTransition(label = "jiggle")
    val jiggleRotation by infiniteTransition.animateFloat(initialValue = -1.5f, targetValue = 1.5f, animationSpec = infiniteRepeatable(tween(120, easing = LinearEasing), RepeatMode.Reverse), label = "rotation")
    val jiggleTx by infiniteTransition.animateFloat(initialValue = -1.5f, targetValue = 1.5f, animationSpec = infiniteRepeatable(tween(130, easing = LinearEasing), RepeatMode.Reverse), label = "tx")
    val jiggleTy by infiniteTransition.animateFloat(initialValue = -1.5f, targetValue = 1.5f, animationSpec = infiniteRepeatable(tween(110, easing = LinearEasing), RepeatMode.Reverse), label = "ty")

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 10.dp).animateContentSize(spring(stiffness = Spring.StiffnessLow)),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Р”Р°С€Р±РѕСЂРґ", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
            IconButton(
                onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); isEditMode = !isEditMode },
                modifier = Modifier.background(if (isEditMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape)
            ) { Icon(if (isEditMode) Icons.Default.Check else Icons.Default.Edit, null, tint = if (isEditMode) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant) }
        }

        val serverInteractionSource = remember { MutableInteractionSource() }
        val isServerPressed by serverInteractionSource.collectIsPressedAsState()
        val serverScale by animateFloatAsState(targetValue = if (isServerPressed) 0.96f else 1f, animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f), label = "")

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .scale(serverScale)
                .graphicsLayer { if (isEditMode) { rotationZ = jiggleRotation * 0.5f; translationX = jiggleTx; translationY = jiggleTy } }
                .clickable(interactionSource = serverInteractionSource, indication = null) {
                    if (!tunnelRunning && !isEditMode) showServerBottomSheet = true
                },
            shape = RoundedCornerShape(30.dp),
            color = if (tunnelRunning) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = if (tunnelRunning) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.32f)),
            tonalElevation = if (tunnelRunning) 5.dp else 2.dp,
            shadowElevation = if (tunnelRunning) 10.dp else 3.dp
        ) {
            Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(modifier = Modifier.size(54.dp), shape = CircleShape, color = if (tunnelRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Dns, null, tint = if (tunnelRunning) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer) }
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("РўРµРєСѓС‰РёР№ СЃРµСЂРІРµСЂ", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(activeServer?.name ?: "РќРµ РІС‹Р±СЂР°РЅ", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface)
                }
                if (isEditMode) {
                    Icon(Icons.Default.DragHandle, null, tint = MaterialTheme.colorScheme.outline)
                } else {
                    IconButton(onClick = { showDiagnosticDialog = true }) { Icon(Icons.Default.HealthAndSafety, null, tint = MaterialTheme.colorScheme.primary) }
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 32.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            FilterChip(selected = protocol == "udp", onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); scope.launch { settingsStore.save(peer, hashes, "", workers, "udp", port, sni) } }, label = { Text("UDP", fontWeight = FontWeight.Bold) }, enabled = !tunnelRunning)
            Spacer(modifier = Modifier.width(12.dp))
            FilterChip(selected = protocol == "tcp", onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); scope.launch { settingsStore.save(peer, hashes, "", workers, "tcp", port, sni) } }, label = { Text("TCP", fontWeight = FontWeight.Bold) }, enabled = !tunnelRunning)
        }

        val connectionStatusText = when {
            tunnelRunning -> "РџРѕРґРєР»СЋС‡РµРЅРѕ"
            cooldownSeconds > 4 -> "РџРѕРґРєР»СЋС‡РµРЅРёРµ..."
            cooldownSeconds > 2 -> "РџСЂРѕРІРµСЂРєР° РєРѕРЅС„РёРіСѓСЂР°С†РёРё..."
            cooldownSeconds > 0 -> "РЈСЃС‚Р°РЅРѕРІРєР° С‚СѓРЅРЅРµР»СЏ..."
            else -> "РќР°Р¶РјРёС‚Рµ РґР»СЏ СЃС‚Р°СЂС‚Р°"
        }

        val mainBtnInteractionSource = remember { MutableInteractionSource() }
        val isMainBtnPressed by mainBtnInteractionSource.collectIsPressedAsState()
        val buttonScale by animateFloatAsState(
            targetValue = when { isMainBtnPressed -> 0.88f; tunnelRunning -> 1.05f; else -> 1f },
            animationSpec = spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow), label = "mainBtnScale"
        )

        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(232.dp)) {
            if (tunnelRunning || cooldownSeconds > 0) PremiumRadarWaves(tunnelRunning)
            
            val circleColor by animateColorAsState(targetValue = if (tunnelRunning) MaterialTheme.colorScheme.primary else if (cooldownSeconds > 0) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.surfaceVariant, animationSpec = tween(500, easing = LinearOutSlowInEasing), label = "")
            val iconColor by animateColorAsState(targetValue = if (tunnelRunning || cooldownSeconds > 0) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant, label = "")
            
            Surface(
                modifier = Modifier
                    .size(150.dp)
                    .scale(buttonScale)
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = mainBtnInteractionSource, indication = null,
                        enabled = cooldownSeconds == 0 || tunnelRunning
                    ) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        if (tunnelRunning) context.startService(Intent(context, TunnelService::class.java).apply { action = "STOP" })
                        else requestVpnAndStart()
                    },
                shape = CircleShape, color = circleColor, shadowElevation = if (tunnelRunning) 22.dp else 7.dp,
                tonalElevation = if (tunnelRunning) 8.dp else 2.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (cooldownSeconds > 0 && !tunnelRunning) {
                        CircularProgressIndicator(color = iconColor, modifier = Modifier.size(70.dp), strokeWidth = 6.dp, strokeCap = StrokeCap.Round)
                    } else {
                        Icon(if (tunnelRunning) Icons.Default.Shield else Icons.Default.PowerSettingsNew, null, modifier = Modifier.size(68.dp), tint = iconColor)
                    }
                }
            }
        }
        
        AnimatedContent(
            targetState = connectionStatusText,
            transitionSpec = { slideInVertically { it / 2 } + fadeIn(tween(300)) togetherWith slideOutVertically { -it / 2 } + fadeOut(tween(300)) },
            label = "statusText"
        ) { text ->
            Text(text, style = MaterialTheme.typography.titleMedium, color = if (tunnelRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp, bottom = 24.dp))
        }

        val gridState = rememberLazyGridState()
        var draggingWidgetIndex by remember { mutableStateOf<Int?>(null) }

        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxWidth().heightIn(max = 1000.dp).pointerInput(isEditMode, activeWidgetList) {
                if (!isEditMode) return@pointerInput
                detectDragGesturesAfterLongPress(
                    onDragStart = { offset ->
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        val item = gridState.layoutInfo.visibleItemsInfo.find {
                            offset.x >= it.offset.x && offset.x <= it.offset.x + it.size.width &&
                            offset.y >= it.offset.y && offset.y <= it.offset.y + it.size.height
                        }
                        draggingWidgetIndex = item?.index
                    },
                    onDrag = { change, _ ->
                        val pointer = change.position
                        val hoveredItem = gridState.layoutInfo.visibleItemsInfo.find {
                            pointer.x >= it.offset.x && pointer.x <= it.offset.x + it.size.width &&
                            pointer.y >= it.offset.y && pointer.y <= it.offset.y + it.size.height
                        }
                        if (hoveredItem != null && draggingWidgetIndex != null && hoveredItem.index != draggingWidgetIndex) {
                            val from = draggingWidgetIndex!!
                            val to = hoveredItem.index
                            if (from in activeWidgetList.indices && to in activeWidgetList.indices) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                val list = activeWidgetList.toMutableList()
                                val temp = list[from]
                                list[from] = list[to]
                                list[to] = temp
                                updateWidgetOrder(list)
                                draggingWidgetIndex = to
                            }
                        }
                    },
                    onDragEnd = { draggingWidgetIndex = null },
                    onDragCancel = { draggingWidgetIndex = null }
                )
            },
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp), userScrollEnabled = false,
            contentPadding = PaddingValues(bottom = 12.dp)
        ) {
            items(activeWidgetList, key = { it.name }, span = { if (it.isWide) GridItemSpan(maxLineSpan) else GridItemSpan(1) }) { widget ->
                val index = activeWidgetList.indexOf(widget)
                val isDragging = draggingWidgetIndex == index
                val rotate = if (isEditMode && !isDragging) (if (index % 2 == 0) jiggleRotation else -jiggleRotation) else 0f
                val tx = if (isEditMode && !isDragging) (if (index % 3 == 0) jiggleTx else -jiggleTx) else 0f
                val ty = if (isEditMode && !isDragging) (if (index % 2 != 0) jiggleTy else -jiggleTy) else 0f
                
                Box(
                    modifier = Modifier
                        .animateItem()
                        .zIndex(if (isDragging) 10f else 0f)
                        .graphicsLayer { 
                            rotationZ = rotate
                            translationX = tx.dp.toPx()
                            translationY = ty.dp.toPx()
                            scaleX = if (isDragging) 1.08f else 1f
                            scaleY = if (isDragging) 1.08f else 1f
                            shadowElevation = if (isDragging) 30f else 0f 
                        }
                ) {
                    if (widget == WidgetType.GRAPH) {
                        SpeedGraphCard(isRunning = tunnelRunning, currentSpeedBytes = currentSpeed, modifier = Modifier.height(160.dp))
                    } else {
                        DashboardCard(title = widget.title, icon = widget.icon, modifier = Modifier.height(130.dp)) {
                            AnimatedContent(
                                targetState = when (widget) {
                                    WidgetType.PING -> if (tunnelRunning) { if (currentPing > 0) "${currentPing} ms" else "..." } else "--"
                                    WidgetType.SESSION -> if (tunnelRunning) timerString else "00:00"
                                    WidgetType.WORKERS -> "$activeWorkers"
                                    WidgetType.SPEED -> if (tunnelRunning) formatRate(currentSpeed) else "0 KB/s"
                                    WidgetType.TRAFFIC -> formatBytes(totalTraffic)
                                    WidgetType.HEALTH -> when {
                                        tunnelRunning && activeWorkers > 0 -> "OK"
                                        tunnelRunning -> "Warmup"
                                        peer.isBlank() || hashes.isBlank() -> "Setup"
                                        else -> "Idle"
                                    }
                                    else -> ""
                                },
                                transitionSpec = {
                                    if (targetState != "--" && targetState != "0 KB/s" && targetState != "00:00" && targetState != "...") {
                                        slideInVertically(spring(stiffness = Spring.StiffnessMediumLow)) { it } + fadeIn(tween(200)) togetherWith 
                                        slideOutVertically(spring(stiffness = Spring.StiffnessMediumLow)) { -it } + fadeOut(tween(200))
                                    } else {
                                        fadeIn(tween(300)) togetherWith fadeOut(tween(300))
                                    }
                                }, label = ""
                            ) { value -> 
                                Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, fontSize = 20.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) 
                            }
                        }
                    }
                    
                    androidx.compose.animation.AnimatedVisibility(
                        visible = isEditMode,
                        enter = scaleIn(spring(stiffness = Spring.StiffnessMediumLow)), exit = scaleOut(spring(stiffness = Spring.StiffnessMediumLow)),
                        modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)
                    ) {
                        Surface(
                            onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); updateWidgetOrder(activeWidgetList - widget) },
                            shape = CircleShape, color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Remove, null, tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(20.dp)) }
                        }
                    }
                }
            }
        }

        androidx.compose.animation.AnimatedVisibility(visible = isEditMode && availableWidgetList.isNotEmpty(), enter = expandVertically(spring(stiffness = Spring.StiffnessMediumLow)) + fadeIn(), exit = shrinkVertically(spring(stiffness = Spring.StiffnessMediumLow)) + fadeOut()) {
            Column(modifier = Modifier.fillMaxWidth().padding(top = 24.dp)) {
                Text("Р”РѕСЃС‚СѓРїРЅС‹Рµ РІРёРґР¶РµС‚С‹", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp))
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2), modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp), userScrollEnabled = false,
                    contentPadding = PaddingValues(bottom = 12.dp)
                ) {
                    items(availableWidgetList, key = { it.name }, span = { if (it.isWide) GridItemSpan(maxLineSpan) else GridItemSpan(1) }) { widget ->
                        val index = availableWidgetList.indexOf(widget)
                        val rotate = (if (index % 2 == 0) -jiggleRotation else jiggleRotation) * 0.7f
                        
                        Box(modifier = Modifier.animateItem().graphicsLayer { rotationZ = rotate; alpha = 0.8f }) {
                            Surface(modifier = Modifier.fillMaxWidth().height(if(widget.isWide) 80.dp else 130.dp), shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(widget.icon, null, modifier = Modifier.size(28.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(Modifier.height(8.dp))
                                    Text(widget.title, style = MaterialTheme.typography.labelLarge)
                                }
                            }
                            Surface(
                                onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); updateWidgetOrder(activeWidgetList + widget) },
                                shape = CircleShape, color = Color(0xFF4CAF50), modifier = Modifier.align(Alignment.TopEnd).padding(12.dp).size(32.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Add, null, tint = Color.White, modifier = Modifier.size(20.dp)) }
                            }
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(32.dp))
    }

    if (showDiagnosticDialog) DiagnosticDialog(context = context, peer = peer, hashes = hashes) { showDiagnosticDialog = false }

    if (showServerBottomSheet) {
        var serverToEdit by remember { mutableStateOf<TyrnGuardServer?>(null) }
        val listState = rememberLazyListState()
        var draggingServerIndex by remember { mutableStateOf<Int?>(null) }
        var isServerEditMode by remember { mutableStateOf(false) }

        ModalBottomSheet(onDismissRequest = { showServerBottomSheet = false }) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).animateContentSize()) {
                Row(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("РЎРїРёСЃРѕРє СЃРµСЂРІРµСЂРѕРІ", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    if (serverList.isNotEmpty()) {
                        TextButton(onClick = { isServerEditMode = !isServerEditMode; haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }) {
                            Text(if (isServerEditMode) "Р“РѕС‚РѕРІРѕ" else "РџСЂР°РІРєР°", fontWeight = FontWeight.Bold)
                        }
                    }
                }
                
                if (serverList.isEmpty()) {
                    Text("РЎРїРёСЃРѕРє РїСѓСЃС‚", modifier = Modifier.padding(vertical = 32.dp).align(Alignment.CenterHorizontally), fontSize = 16.sp)
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.weight(1f, fill = false).padding(bottom = 16.dp).pointerInput(isServerEditMode, serverList) {
                            if (!isServerEditMode) return@pointerInput
                            detectDragGesturesAfterLongPress(
                                onDragStart = { offset ->
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    val item = listState.layoutInfo.visibleItemsInfo.find { offset.y >= it.offset && offset.y <= it.offset + it.size }
                                    draggingServerIndex = item?.index
                                },
                                onDrag = { change, _ ->
                                    val pointerY = change.position.y
                                    val hoveredItem = listState.layoutInfo.visibleItemsInfo.find { pointerY >= it.offset && pointerY <= it.offset + it.size }
                                    if (hoveredItem != null && draggingServerIndex != null && hoveredItem.index != draggingServerIndex) {
                                        val from = draggingServerIndex!!
                                        val to = hoveredItem.index
                                        if (from in serverList.indices && to in serverList.indices) {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            val list = serverList.toMutableList()
                                            val temp = list[from]
                                            list[from] = list[to]
                                            list[to] = temp
                                            serverList.clear()
                                            serverList.addAll(list)
                                            saveServers()
                                            draggingServerIndex = to
                                        }
                                    }
                                },
                                onDragEnd = { draggingServerIndex = null },
                                onDragCancel = { draggingServerIndex = null }
                            )
                        }
                    ) {
                        items(serverList, key = { it.id }) { server ->
                            val index = serverList.indexOf(server)
                            val isSelected = peer.trim() == server.ip.trim()
                            val isDragging = draggingServerIndex == index
                            val rotate = if (isServerEditMode && !isDragging) (if (index % 2 == 0) jiggleRotation else -jiggleRotation) else 0f
                            val tx = if (isServerEditMode && !isDragging) (if (index % 3 == 0) jiggleTx else -jiggleTx) else 0f
                            val ty = if (isServerEditMode && !isDragging) (if (index % 2 != 0) jiggleTy else -jiggleTy) else 0f
                            
                            val itemInteractionSource = remember { MutableInteractionSource() }
                            val isItemPressed by itemInteractionSource.collectIsPressedAsState()
                            val itemScale by animateFloatAsState(if (isItemPressed) 0.96f else if (isDragging) 1.05f else 1f, spring(dampingRatio = 0.5f, stiffness = 400f), label = "")

                            Surface(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).animateItem()
                                    .zIndex(if (isDragging) 10f else 0f)
                                    .graphicsLayer { 
                                        rotationZ = rotate
                                        translationX = tx.dp.toPx()
                                        translationY = ty.dp.toPx()
                                        scaleX = itemScale
                                        scaleY = itemScale
                                        shadowElevation = if (isDragging) 24f else 0f
                                        alpha = if (isDragging) 0.9f else 1f
                                    }.clickable(interactionSource = itemInteractionSource, indication = null) {
                                        if (!isServerEditMode) {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); 
                                            scope.launch { 
                                                settingsStore.save(server.ip.trim(), hashes, "", workers, protocol, port, sni)
                                                settingsStore.saveConnectionPassword(server.password.trim()) 
                                            }; 
                                            showServerBottomSheet = false 
                                        }
                                    }, 
                                shape = RoundedCornerShape(24.dp), 
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
                            ) {
                                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    if (isServerEditMode) {
                                        Icon(Icons.Default.DragHandle, null, tint = MaterialTheme.colorScheme.outline)
                                        Spacer(Modifier.width(12.dp))
                                    } else {
                                        Icon(Icons.Default.Dns, null, tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                                        Spacer(Modifier.width(16.dp))
                                    }
                                    
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(server.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface)
                                        Text(server.ip, style = MaterialTheme.typography.bodyMedium, color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                                        Text("DTLS ${server.dtlsPort}  |  WG ${server.wgPort}", style = MaterialTheme.typography.labelSmall, color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f) else MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    
                                    if (isServerEditMode) {
                                        IconButton(onClick = { serverToEdit = server }) { Icon(Icons.Default.Edit, null, tint = MaterialTheme.colorScheme.primary) }
                                    } else if (isSelected) {
                                        Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                    }
                }
                Button(onClick = { serverToEdit = TyrnGuardServer(name = "", ip = "", password = "") }, modifier = Modifier.fillMaxWidth().height(60.dp), shape = RoundedCornerShape(20.dp)) {
                    Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("Р”РѕР±Р°РІРёС‚СЊ СЃРµСЂРІРµСЂ", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                Spacer(Modifier.height(16.dp))
            }
        }
        serverToEdit?.let { srv ->
            AddEditServerDialog(server = srv, onDismiss = { serverToEdit = null }, onSave = { updated -> val idx = serverList.indexOfFirst { it.id == updated.id }; if (idx != -1) serverList[idx] = updated else serverList.add(updated); saveServers(); serverToEdit = null }, onDelete = { serverList.removeIf { it.id == srv.id }; saveServers(); serverToEdit = null })
        }
    }
}

@Composable
fun PremiumRadarWaves(isConnected: Boolean) {
    val t = rememberInfiniteTransition(label = "")
    val scale by t.animateFloat(
        initialValue = 1.0f, 
        targetValue = if (isConnected) 1.35f else 1.8f, 
        animationSpec = infiniteRepeatable(tween(if (isConnected) 2000 else 1500, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = ""
    )
    val alpha by t.animateFloat(
        initialValue = if (isConnected) 0.6f else 0.4f, 
        targetValue = 0f, 
        animationSpec = infiniteRepeatable(tween(if (isConnected) 2000 else 1500, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = ""
    )
    
    Box(modifier = Modifier.size(150.dp).scale(scale).alpha(alpha).background(MaterialTheme.colorScheme.primary, CircleShape))
    if (!isConnected) { 
        val scale2 by t.animateFloat(initialValue = 0.8f, targetValue = 2.2f, animationSpec = infiniteRepeatable(tween(1500, 500, FastOutSlowInEasing)), label = "")
        val alpha2 by t.animateFloat(initialValue = 0.3f, targetValue = 0f, animationSpec = infiniteRepeatable(tween(1500, 500, FastOutSlowInEasing)), label = "")
        Box(modifier = Modifier.size(150.dp).scale(scale2).alpha(alpha2).background(MaterialTheme.colorScheme.primary, CircleShape))
    }
}

@Composable
fun DiagnosticDialog(context: Context, peer: String, hashes: String, onDismiss: () -> Unit) {
    var step by remember { mutableIntStateOf(0) }
    var results by remember { mutableStateOf(listOf<Boolean?>(null, null, null, null)) }
    
    LaunchedEffect(Unit) {
        if (peer.isBlank()) {
            results = listOf(false, false, false, false)
            step = 4
            return@LaunchedEffect
        }
        
        withContext(Dispatchers.IO) {
            val ip = peer.substringBefore(":")
            
            val internetOk = try { Socket().use { it.connect(InetSocketAddress("8.8.8.8", 53), 1500) }; true } catch (e: Exception) { false }
            results = results.toMutableList().apply { set(0, internetOk) }; step = 1

            val serverOk = try {
                val process = Runtime.getRuntime().exec("ping -c 1 -W 2 $ip")
                if (process.waitFor() == 0) true else { Socket().use { it.connect(InetSocketAddress(ip, 22), 1500) }; true }
            } catch (e: Exception) { false }
            results = results.toMutableList().apply { set(1, serverOk) }; step = 2

            val hashValid = hashes.isNotBlank() && hashes.split(",").any { it.trim().length > 20 }
            results = results.toMutableList().apply { set(2, hashValid) }; step = 3

            val coreOk = File(context.applicationInfo.nativeLibraryDir + "/libclient.so").exists()
            delay(500)
            results = results.toMutableList().apply { set(3, coreOk) }; step = 4
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column(modifier = Modifier.padding(24.dp).fillMaxWidth().animateContentSize()) {
                Text("Р”РёР°РіРЅРѕСЃС‚РёРєР°", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 16.dp))
                val steps = listOf("Р”РѕСЃС‚СѓРї Рє РёРЅС‚РµСЂРЅРµС‚Сѓ", "Р”РѕСЃС‚СѓРїРЅРѕСЃС‚СЊ СЃРµСЂРІРµСЂР° (VPS)", "Р¤РѕСЂРјР°С‚ VK РҐСЌС€РµР№", "РЇРґСЂРѕ С‚СѓРЅРЅРµР»СЏ (Core)")
                steps.forEachIndexed { i, title ->
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        AnimatedContent(targetState = results[i], label = "") { res ->
                            when (res) {
                                null -> if (step == i) CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp) else Icon(Icons.Default.Circle, null, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.outlineVariant)
                                true -> Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(24.dp), tint = Color(0xFF4CAF50))
                                false -> Icon(Icons.Default.Cancel, null, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.error)
                            }
                        }
                        Spacer(Modifier.width(16.dp))
                        Text(title, style = MaterialTheme.typography.bodyLarge, color = if (step == i) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                    }
                }
                Spacer(Modifier.height(24.dp))
                Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(20.dp), enabled = step == 4) { Text("Р—Р°РєСЂС‹С‚СЊ", fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
fun SpeedGraphCard(isRunning: Boolean, currentSpeedBytes: Long, modifier: Modifier = Modifier) {
    val points = remember { mutableStateListOf<Float>().apply { repeat(30) { add(0f) } } }
    LaunchedEffect(currentSpeedBytes, isRunning) { points.removeAt(0); points.add(if (isRunning) currentSpeedBytes.toFloat() else 0f) }
    val maxPoint by remember(points) { derivedStateOf { (points.maxOrNull() ?: 1f).coerceAtLeast(1024 * 50f) } }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.26f)),
        shadowElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.QueryStats, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("РўСЂР°С„РёРє СЃРµС‚Рё", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                val dotColor by animateColorAsState(if (isRunning && currentSpeedBytes > 1024) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, label = "")
                Box(modifier = Modifier.size(10.dp).background(dotColor, CircleShape))
            }
            Spacer(Modifier.height(16.dp))
            val lineColor = MaterialTheme.colorScheme.primary
            Canvas(modifier = Modifier.fillMaxSize()) {
                val path = Path()
                val stepX = size.width / (points.size - 1)
                points.forEachIndexed { i, v ->
                    val x = i * stepX
                    val y = size.height - (v / maxPoint * size.height)
                    if (i == 0) path.moveTo(x, y) else {
                        val px = (i - 1) * stepX
                        val py = size.height - (points[i - 1] / maxPoint * size.height)
                        path.cubicTo(px + stepX / 2f, py, px + stepX / 2f, y, x, y)
                    }
                }
                drawPath(path = path, color = lineColor, style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                val fillPath = Path().apply { addPath(path); lineTo(size.width, size.height); lineTo(0f, size.height); close() }
                drawPath(path = fillPath, color = lineColor.copy(alpha = 0.10f))
            }
        }
    }
}

@Composable
fun DashboardCard(title: String, icon: ImageVector, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.24f)),
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(34.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(icon, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Spacer(Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
            }
            Spacer(Modifier.height(16.dp))
            content()
        }
    }
}

@Composable
fun AddEditServerDialog(server: TyrnGuardServer, onDismiss: () -> Unit, onSave: (TyrnGuardServer) -> Unit, onDelete: () -> Unit) {
    var name by remember { mutableStateOf(server.name) }
    var ip by remember { mutableStateOf(server.ip) }
    var pass by remember { mutableStateOf(server.password) }
    var dtlsPort by remember { mutableStateOf(server.dtlsPort.toString()) }
    var wgPort by remember { mutableStateOf(server.wgPort.toString()) }
    val isNew = server.name.isBlank()
    val normalizedDtlsPort = dtlsPort.toIntOrNull()?.coerceIn(1, 65535) ?: 56000
    val normalizedWgPort = wgPort.toIntOrNull()?.coerceIn(1, 65535) ?: 56001
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, tonalElevation = 6.dp) {
            Column(modifier = Modifier.padding(24.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(if (isNew) "РќРѕРІС‹Р№ СЃРµСЂРІРµСЂ" else "РќР°СЃС‚СЂРѕР№РєРё СЃРµСЂРІРµСЂР°", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    if (!isNew) IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) }
                }
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("РРјСЏ (РЅР°РїСЂ. Р“РµСЂРјР°РЅРёСЏ)", fontSize = 14.sp) }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp))
                OutlinedTextField(value = ip, onValueChange = { ip = it.filter { c -> !c.isWhitespace() } }, label = { Text("IP Р°РґСЂРµСЃ", fontSize = 14.sp) }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp))
                OutlinedTextField(value = pass, onValueChange = { pass = it.filter { c -> !c.isWhitespace() } }, label = { Text("РџР°СЂРѕР»СЊ РѕС‚ С‚СѓРЅРЅРµР»СЏ", fontSize = 14.sp) }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(value = dtlsPort, onValueChange = { dtlsPort = it.filter(Char::isDigit).take(5) }, label = { Text("DTLS", fontSize = 14.sp) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp))
                    OutlinedTextField(value = wgPort, onValueChange = { wgPort = it.filter(Char::isDigit).take(5) }, label = { Text("WireGuard", fontSize = 14.sp) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp))
                }
                Button(onClick = { onSave(server.copy(name = name, ip = ip, password = pass, dtlsPort = normalizedDtlsPort, wgPort = normalizedWgPort)) }, enabled = name.isNotBlank() && ip.isNotBlank(), modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(20.dp)) { Text("Сохранить", fontWeight = FontWeight.Bold, fontSize = 16.sp) }
            }
        }
    }
}
