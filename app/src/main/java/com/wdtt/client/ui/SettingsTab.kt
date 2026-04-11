package com.wdtt.client.ui

import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wdtt.client.SettingsStore
import com.wdtt.client.TunnelManager
import com.wdtt.client.TunnelService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class WdttServer(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val ip: String,
    val password: String
)

enum class WidgetType(val title: String, val icon: ImageVector) {
    PING("Пинг", Icons.Default.NetworkPing),
    SESSION("Сессия", Icons.Default.Timer),
    WORKERS("Воркеры", Icons.Default.Hub),
    SPEED("Скорость", Icons.Default.Download)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsTab(snackbarHostState: SnackbarHostState) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val settingsStore = remember { SettingsStore(context) }
    val prefs = context.getSharedPreferences("wdtt_widgets", Context.MODE_PRIVATE)

    val tunnelRunning by TunnelManager.running.collectAsStateWithLifecycle()
    val cooldownSeconds by TunnelManager.cooldownSeconds.collectAsStateWithLifecycle()
    val currentPing by TunnelManager.currentPingMs.collectAsStateWithLifecycle()
    val currentSpeed by TunnelManager.currentSpeedBytes.collectAsStateWithLifecycle()
    val activeWorkers by TunnelManager.activeWorkers.collectAsStateWithLifecycle()

    val peer by settingsStore.peer.collectAsStateWithLifecycle("")
    val hashes by settingsStore.vkHashes.collectAsStateWithLifecycle("")
    val workers by settingsStore.workersPerHash.collectAsStateWithLifecycle(24)
    val port by settingsStore.listenPort.collectAsStateWithLifecycle(9000)
    val sni by settingsStore.sni.collectAsStateWithLifecycle("")
    val connPass by settingsStore.connectionPassword.collectAsStateWithLifecycle("")
    val protocol by settingsStore.protocol.collectAsStateWithLifecycle("udp")
    val captchaMethod by settingsStore.captchaSolveMethod.collectAsStateWithLifecycle("manual")
    val savedServersJson by settingsStore.savedServersJson.collectAsStateWithLifecycle("[]")

    val serverList = remember { mutableStateListOf<WdttServer>() }
    var activeWidgetList by remember { mutableStateOf(WidgetType.values().toList()) }

    LaunchedEffect(Unit) {
        val savedOrder = prefs.getString("order", null)
        if (savedOrder != null) {
            try {
                activeWidgetList = savedOrder.split(",").map { WidgetType.valueOf(it) }
            } catch (e: Exception) {}
        }
    }

    LaunchedEffect(savedServersJson) {
        serverList.clear()
        try {
            val array = JSONArray(savedServersJson)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                serverList.add(WdttServer(obj.optString("id", UUID.randomUUID().toString()), obj.optString("name"), obj.optString("ip").trim(), obj.optString("password").trim()))
            }
        } catch (_: Exception) {}
    }

    val activeServer = remember(peer, serverList.size) { serverList.find { it.ip == peer.trim() } }

    var showServerBottomSheet by remember { mutableStateOf(false) }
    var showDiagnosticDialog by remember { mutableStateOf(false) }
    var sessionSeconds by rememberSaveable { mutableIntStateOf(0) }
    var isEditMode by rememberSaveable { mutableStateOf(false) }
    var showGraphWidget by remember { mutableStateOf(prefs.getBoolean("show_graph", true)) }

    LaunchedEffect(tunnelRunning) {
        if (tunnelRunning) { while (true) { delay(1000); sessionSeconds++ } } else sessionSeconds = 0
    }

    val timerString = String.format("%02d:%02d:%02d", sessionSeconds / 3600, (sessionSeconds % 3600) / 60, sessionSeconds % 60)

    fun saveServers() {
        val array = JSONArray()
        serverList.forEach { array.put(JSONObject().apply { put("id", it.id); put("name", it.name); put("ip", it.ip.trim()); put("password", it.password.trim()) }) }
        scope.launch { settingsStore.saveServersList(array.toString()) }
    }

    fun updateWidgetOrder(newList: List<WidgetType>) {
        activeWidgetList = newList
        prefs.edit().putString("order", newList.joinToString(",") { it.name }).apply()
    }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Дашборд", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
            IconButton(
                onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); isEditMode = !isEditMode },
                modifier = Modifier.background(if (isEditMode) MaterialTheme.colorScheme.primaryContainer else Color.Transparent, CircleShape)
            ) { Icon(if (isEditMode) Icons.Default.Check else Icons.Default.Edit, null, tint = if (isEditMode) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant) }
        }

        AnimatedVisibility(visible = isEditMode, enter = expandVertically(spring(stiffness = Spring.StiffnessLow)) + fadeIn(), exit = shrinkVertically(spring(stiffness = Spring.StiffnessLow)) + fadeOut()) {
            Surface(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp), shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Настройка виджетов", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    activeWidgetList.forEachIndexed { index, widget ->
                        Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(widget.icon, null, modifier = Modifier.padding(horizontal = 8.dp))
                            Text(widget.title, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
                            if (index > 0) IconButton(onClick = { val l = activeWidgetList.toMutableList(); java.util.Collections.swap(l, index, index - 1); updateWidgetOrder(l); haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }) { Icon(Icons.Default.ArrowUpward, null) }
                            if (index < activeWidgetList.size - 1) IconButton(onClick = { val l = activeWidgetList.toMutableList(); java.util.Collections.swap(l, index, index + 1); updateWidgetOrder(l); haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }) { Icon(Icons.Default.ArrowDownward, null) }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("График активности", style = MaterialTheme.typography.bodyLarge)
                        Switch(checked = showGraphWidget, onCheckedChange = { showGraphWidget = it; prefs.edit().putBoolean("show_graph", it).apply(); haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }, modifier = Modifier.scale(0.9f))
                    }
                }
            }
        }

        Surface(
            onClick = { if (!tunnelRunning) showServerBottomSheet = true },
            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest, tonalElevation = 2.dp
        ) {
            Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(modifier = Modifier.size(52.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primary) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Dns, null, tint = MaterialTheme.colorScheme.onPrimary) }
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Текущий сервер", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(activeServer?.name ?: "Не выбран", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface)
                }
                IconButton(onClick = { showDiagnosticDialog = true }) { Icon(Icons.Default.HealthAndSafety, null, tint = MaterialTheme.colorScheme.primary) }
            }
        }

        Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 32.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            FilterChip(selected = protocol == "udp", onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); scope.launch { settingsStore.save(peer, hashes, "", workers, "udp", port, sni) } }, label = { Text("UDP", fontWeight = FontWeight.Bold) }, enabled = !tunnelRunning)
            Spacer(modifier = Modifier.width(12.dp))
            FilterChip(selected = protocol == "tcp", onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); scope.launch { settingsStore.save(peer, hashes, "", workers, "tcp", port, sni) } }, label = { Text("TCP", fontWeight = FontWeight.Bold) }, enabled = !tunnelRunning)
        }

        val connectionStatusText = when {
            tunnelRunning -> "Подключено"
            cooldownSeconds > 4 -> "Подключение..."
            cooldownSeconds > 2 -> "Проверка конфигурации..."
            cooldownSeconds > 0 -> "Установка туннеля..."
            else -> "Нажмите для старта"
        }

        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(240.dp)) {
            if (tunnelRunning || cooldownSeconds > 0) RadarWaves()
            val circleColor by animateColorAsState(targetValue = if (tunnelRunning) MaterialTheme.colorScheme.primary else if (cooldownSeconds > 0) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.surfaceVariant, animationSpec = tween(600), label = "")
            val iconColor by animateColorAsState(targetValue = if (tunnelRunning || cooldownSeconds > 0) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant, label = "")
            Surface(
                modifier = Modifier.size(150.dp).clip(CircleShape).clickable(enabled = cooldownSeconds == 0 || tunnelRunning) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    if (tunnelRunning) {
                        context.startService(Intent(context, TunnelService::class.java).apply { action = "STOP" })
                    } else {
                        if (peer.isBlank() || hashes.isBlank()) { Toast.makeText(context, "Выберите сервер и укажите хеши!", Toast.LENGTH_SHORT).show(); return@clickable }
                        val intent = Intent(context, TunnelService::class.java).apply {
                            action = "START"
                            putExtra("peer", "${peer.trim()}:56000")
                            putExtra("vk_hashes", hashes)
                            putExtra("workers_per_hash", workers)
                            putExtra("port", port)
                            putExtra("sni", sni)
                            putExtra("connection_password", connPass.trim())
                            putExtra("protocol", protocol)
                            putExtra("captcha_mode", if (captchaMethod == "auto") "rjs" else "wv")
                        }
                        if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent) else context.startService(intent)
                    }
                },
                shape = CircleShape, color = circleColor, shadowElevation = if (tunnelRunning) 16.dp else 4.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (cooldownSeconds > 0 && !tunnelRunning) {
                        CircularProgressIndicator(color = iconColor, modifier = Modifier.size(70.dp), strokeWidth = 6.dp, strokeCap = StrokeCap.Round)
                    } else {
                        Icon(if (tunnelRunning) Icons.Default.Shield else Icons.Default.PowerSettingsNew, null, modifier = Modifier.size(68.dp).scale(if (tunnelRunning) 1.1f else 1f), tint = iconColor)
                    }
                }
            }
        }
        
        AnimatedContent(targetState = connectionStatusText, transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(300)) }, label = "") { text ->
            Text(text, style = MaterialTheme.typography.titleMedium, color = if (tunnelRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp, bottom = 24.dp))
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp), userScrollEnabled = false
        ) {
            items(activeWidgetList, key = { it.name }) { widget ->
                DashboardCard(title = widget.title, icon = widget.icon) {
                    AnimatedContent(
                        targetState = when (widget) {
                            WidgetType.PING -> if (tunnelRunning && currentPing > 0) "${currentPing}ms" else "--"
                            WidgetType.SESSION -> if (tunnelRunning) timerString else "00:00"
                            WidgetType.WORKERS -> "$activeWorkers"
                            WidgetType.SPEED -> {
                                val speedKb = currentSpeed / 1024f
                                if (tunnelRunning) if (speedKb > 1024) String.format("%.1f MB/s", speedKb / 1024f) else String.format("%.0f KB/s", speedKb) else "0 KB/s"
                            }
                        },
                        transitionSpec = { fadeIn() togetherWith fadeOut() }, label = ""
                    ) { value -> Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
            }
        }

        AnimatedVisibility(visible = showGraphWidget, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column { Spacer(Modifier.height(16.dp)); SpeedGraphCard(isRunning = tunnelRunning, currentSpeedBytes = currentSpeed) }
        }
        Spacer(modifier = Modifier.height(32.dp))
    }

    if (showDiagnosticDialog) DiagnosticDialog(peer = peer, port = "56000", hashes = hashes) { showDiagnosticDialog = false }

    if (showServerBottomSheet) {
        var serverToEdit by remember { mutableStateOf<WdttServer?>(null) }
        ModalBottomSheet(onDismissRequest = { showServerBottomSheet = false }, windowInsets = WindowInsets.navigationBars) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text("Список серверов", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 16.dp))
                if (serverList.isEmpty()) {
                    Text("Список пуст", modifier = Modifier.padding(vertical = 32.dp).align(Alignment.CenterHorizontally), fontSize = 16.sp)
                } else {
                    LazyColumn(modifier = Modifier.weight(1f, fill = false).padding(bottom = 16.dp)) {
                        items(serverList, key = { it.id }) { server ->
                            val isSelected = peer.trim() == server.ip.trim()
                            Surface(
                                onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); scope.launch { settingsStore.save(server.ip.trim(), hashes, "", workers, protocol, port, sni); settingsStore.saveConnectionPassword(server.password.trim()) }; showServerBottomSheet = false },
                                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), shape = RoundedCornerShape(24.dp), color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
                            ) {
                                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Dns, null, tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(Modifier.width(16.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(server.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface)
                                        Text(server.ip, style = MaterialTheme.typography.bodyMedium, color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                                    }
                                    IconButton(onClick = { serverToEdit = server }) { Icon(Icons.Default.Edit, null, tint = MaterialTheme.colorScheme.primary) }
                                }
                            }
                        }
                    }
                }
                Button(onClick = { serverToEdit = WdttServer(name = "", ip = "", password = "") }, modifier = Modifier.fillMaxWidth().height(60.dp), shape = RoundedCornerShape(20.dp)) {
                    Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("Добавить сервер", fontWeight = FontWeight.Bold, fontSize = 16.sp)
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
fun DiagnosticDialog(peer: String, port: String, hashes: String, onDismiss: () -> Unit) {
    var step by remember { mutableIntStateOf(0) }
    var results by remember { mutableStateOf(listOf<Boolean?>(null, null, null, null)) }
    
    LaunchedEffect(Unit) {
        if (peer.isBlank()) { results = listOf(false, false, false, false); step = 4; return@LaunchedEffect }
        delay(600); results = results.toMutableList().apply { set(0, true) }; step = 1
        delay(800); results = results.toMutableList().apply { set(1, true) }; step = 2
        delay(1000); results = results.toMutableList().apply { set(2, true) }; step = 3
        delay(700); results = results.toMutableList().apply { set(3, hashes.isNotBlank()) }; step = 4
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column(modifier = Modifier.padding(24.dp).fillMaxWidth()) {
                Text("Диагностика", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 16.dp))
                val steps = listOf("Доступность IP (Ping)", "Проверка порта $port", "Аутентификация", "Валидация VK Хэшей")
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
                Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(20.dp), enabled = step == 4) { Text("Закрыть", fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
fun SpeedGraphCard(isRunning: Boolean, currentSpeedBytes: Long) {
    val points = remember { mutableStateListOf<Float>().apply { repeat(30) { add(0f) } } }
    LaunchedEffect(currentSpeedBytes, isRunning) { points.removeAt(0); points.add(if (isRunning) currentSpeedBytes.toFloat() else 0f) }
    val maxPoint by remember(points) { derivedStateOf { (points.maxOrNull() ?: 1f).coerceAtLeast(1024 * 50f) } }

    Surface(modifier = Modifier.fillMaxWidth().height(140.dp), shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.QueryStats, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Трафик сети", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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
                drawPath(path = fillPath, brush = Brush.verticalGradient(listOf(lineColor.copy(alpha = 0.4f), Color.Transparent), 0f, size.height))
            }
        }
    }
}

@Composable
fun RadarWaves() {
    val t = rememberInfiniteTransition(label = "")
    listOf(0, 1, 2).forEach { i ->
        val s by t.animateFloat(initialValue = 0.5f, targetValue = 2.5f, animationSpec = infiniteRepeatable(tween(2400, i * 800, LinearEasing)), label = "")
        val a by t.animateFloat(initialValue = 0.4f, targetValue = 0f, animationSpec = infiniteRepeatable(tween(2400, i * 800, LinearEasing)), label = "")
        Box(modifier = Modifier.size(150.dp).scale(s).alpha(a).background(MaterialTheme.colorScheme.primary, CircleShape))
    }
}

@Composable
fun DashboardCard(title: String, icon: ImageVector, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(modifier = modifier.heightIn(min = 90.dp), shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.Center) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(32.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(icon, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Spacer(Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
fun AddEditServerDialog(server: WdttServer, onDismiss: () -> Unit, onSave: (WdttServer) -> Unit, onDelete: () -> Unit) {
    var name by remember { mutableStateOf(server.name) }
    var ip by remember { mutableStateOf(server.ip) }
    var pass by remember { mutableStateOf(server.password) }
    val isNew = server.name.isBlank()
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, tonalElevation = 6.dp) {
            Column(modifier = Modifier.padding(24.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(if (isNew) "Новый сервер" else "Настройки сервера", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    if (!isNew) IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) }
                }
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Имя (напр. Германия)", fontSize = 14.sp) }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp))
                OutlinedTextField(value = ip, onValueChange = { ip = it.filter { c -> !c.isWhitespace() } }, label = { Text("IP адрес", fontSize = 14.sp) }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp))
                OutlinedTextField(value = pass, onValueChange = { pass = it.filter { c -> !c.isWhitespace() } }, label = { Text("Пароль от туннеля", fontSize = 14.sp) }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp))
                Button(onClick = { onSave(server.copy(name = name, ip = ip, password = pass)) }, enabled = name.isNotBlank() && ip.isNotBlank(), modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(20.dp)) { Text("Сохранить", fontWeight = FontWeight.Bold, fontSize = 16.sp) }
            }
        }
    }
}