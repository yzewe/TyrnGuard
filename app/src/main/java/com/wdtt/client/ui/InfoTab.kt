package com.wdtt.client.ui

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wdtt.client.SettingsStore
import com.wdtt.client.TunnelManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import kotlin.math.roundToInt

@Composable
fun InfoTab() {
    var currentScreen by rememberSaveable { mutableStateOf("main") }
    val context = LocalContext.current
    val settingsStore = remember { SettingsStore(context) }

    BackHandler(enabled = currentScreen != "main") { currentScreen = "main" }

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = currentScreen,
            transitionSpec = {
                val animationSpec = spring<IntOffset>(stiffness = Spring.StiffnessLow)
                if (targetState != "main") {
                    slideInHorizontally(animationSpec) { it } + fadeIn() togetherWith slideOutHorizontally(animationSpec) { -it } + fadeOut()
                } else {
                    slideInHorizontally(animationSpec) { -it } + fadeIn() togetherWith slideOutHorizontally(animationSpec) { it } + fadeOut()
                }
            },
            label = ""
        ) { screen ->
            when (screen) {
                "main" -> MainSettingsMenu(settingsStore) { currentScreen = it }
                "network" -> NetworkSettings(settingsStore) { currentScreen = "main" }
                "performance" -> PerformanceSettings(settingsStore) { currentScreen = "main" }
                "interface" -> InterfaceSettings(settingsStore) { currentScreen = "main" }
            }
        }
    }
}

@Composable
fun MainSettingsMenu(settingsStore: SettingsStore, onNavigate: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentPeer by settingsStore.peer.collectAsStateWithLifecycle("")
    var showImportantInfoDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Настройки", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
        MenuCategoryItem("Сеть", "Протокол, MTU, DNS", Icons.Default.Language) { onNavigate("network") }
        MenuCategoryItem("Производительность", "Хеши, Потоки, Капча", Icons.Default.Speed) { onNavigate("performance") }
        MenuCategoryItem("Интерфейс", "Темы, Цвета, Отклик", Icons.Default.Palette) { onNavigate("interface") }
        
        CategoryCard("Импорт / Экспорт", Icons.Default.Share) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    modifier = Modifier.weight(1f).height(56.dp), shape = RoundedCornerShape(20.dp),
                    onClick = {
                        val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val text = cb.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
                        if (text.contains("wdtt://config?data=")) {
                            try {
                                val json = JSONObject(String(Base64.decode(text.substringAfter("data="), Base64.URL_SAFE)))
                                scope.launch { addServerToStoreDirect(context, settingsStore, json) }
                            } catch (e: Exception) { Toast.makeText(context, "Ошибка чтения ссылки", Toast.LENGTH_SHORT).show() }
                        } else Toast.makeText(context, "Ссылка не в буфере", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Icon(Icons.Default.ContentPasteGo, null); Spacer(Modifier.width(8.dp)); Text("Импорт", fontSize = 16.sp)
                }

                OutlinedButton(
                    modifier = Modifier.weight(1f).height(56.dp), shape = RoundedCornerShape(20.dp),
                    onClick = {
                        scope.launch {
                            val servers = JSONArray(settingsStore.savedServersJson.first())
                            var activeObj: JSONObject? = null
                            for (i in 0 until servers.length()) { if (servers.getJSONObject(i).optString("ip") == currentPeer) { activeObj = servers.getJSONObject(i); break } }
                            if (activeObj == null) { Toast.makeText(context, "Выберите сервер", Toast.LENGTH_SHORT).show(); return@launch }
                            val b64 = Base64.encodeToString(activeObj.toString().toByteArray(), Base64.URL_SAFE or Base64.NO_WRAP)
                            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, "Конфигурация WDTT:\nwdtt://config?data=$b64") }, "Поделиться"))
                        }
                    }
                ) {
                    Icon(Icons.Default.IosShare, null); Spacer(Modifier.width(8.dp)); Text("Экспорт", fontSize = 16.sp)
                }
            }
        }
        CategoryCard("О приложении", Icons.Default.Info) {
            Button(onClick = { showImportantInfoDialog = true }, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(20.dp)) { Text("Важная информация", fontSize = 16.sp) }
            Spacer(Modifier.height(12.dp))
            SettingClickRow(Icons.Default.Code, "GitHub", "Исходный код проекта") { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/amurcanov/proxy-turn-vk-android"))) }
        }
        Column(modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Версия 1.0.6 (Stable)", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline, fontSize = 14.sp)
            Spacer(Modifier.height(16.dp))
            Surface(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://yzewe.ru"))) }, shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)) {
                Text("Форк от yzewe", modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
    }
    if (showImportantInfoDialog) ImportantInfoDialog { showImportantInfoDialog = false }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkSettings(settingsStore: SettingsStore, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current

    val protocol by settingsStore.protocol.collectAsStateWithLifecycle("udp")
    val customMtu by settingsStore.customMtu.collectAsStateWithLifecycle(0)
    val dnsType by settingsStore.customDns.collectAsStateWithLifecycle("default")
    val currentPeer by settingsStore.peer.collectAsStateWithLifecycle("")
    val currentHashes by settingsStore.vkHashes.collectAsStateWithLifecycle("")
    val workersCount by settingsStore.workersPerHash.collectAsStateWithLifecycle(24)
    val autoConnect by settingsStore.autoConnectOnBoot.collectAsStateWithLifecycle(false)

    var lastMtu by remember(customMtu) { mutableIntStateOf(customMtu) }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SettingsHeader("Сеть", onBack)
        CategoryCard("Транспорт", Icons.Default.CompareArrows) {
            Text("Сетевой протокол", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(Modifier.height(12.dp))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                listOf("udp" to "UDP (Быстро)", "tcp" to "TCP (Обход)").forEachIndexed { i, (v, l) ->
                    SegmentedButton(
                        selected = protocol == v, shape = SegmentedButtonDefaults.itemShape(index = i, count = 2),
                        onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); scope.launch { settingsStore.save(currentPeer, currentHashes, "", workersCount, v, 9000, "") } }
                    ) { Text(l, fontSize = 14.sp) }
                }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Размер пакета (MTU)", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                AnimatedContent(targetState = customMtu, label = "") { mtu -> Text(if (mtu == 0) "Авто" else "$mtu", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 16.sp) }
            }
            Slider(
                value = if (customMtu == 0) 1279f else customMtu.toFloat(),
                onValueChange = {
                    val v = if (it < 1280f) 0 else it.roundToInt()
                    if (kotlin.math.abs(v - lastMtu) > 5 || (v == 0 && lastMtu != 0)) { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); lastMtu = v }
                    scope.launch { settingsStore.saveCustomMtu(v) }
                },
                onValueChangeFinished = { scope.launch { TunnelManager.reloadWireGuard() } }, valueRange = 1279f..1500f
            )
            Text("Меньшее значение помогает при плохой связи", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
        }
        CategoryCard("DNS Сервер", Icons.Default.Dns) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                listOf("default" to "Авто", "adguard" to "AdBlock", "cloudflare" to "Cloudflare").forEachIndexed { i, (v, l) ->
                    SegmentedButton(
                        selected = dnsType == v, shape = SegmentedButtonDefaults.itemShape(index = i, count = 3),
                        onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); scope.launch { settingsStore.saveCustomDns(v); TunnelManager.reloadWireGuard() } }
                    ) { Text(l, fontSize = 12.sp, maxLines = 1) }
                }
            }
            Text(when(dnsType) { "adguard" -> "Блокирует рекламу и трекеры на уровне пакетов."; "cloudflare" -> "Самый быстрый и приватный DNS."; else -> "Использовать DNS провайдера или сервера." }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp), fontSize = 14.sp)
        }
        CategoryCard("Система", Icons.Default.SettingsSystemDaydream) {
            SettingSwitchRow(Icons.Default.Power, "Автозапуск", "Включать VPN при загрузке системы", autoConnect) { scope.launch { settingsStore.saveAutoConnect(it) } }
            SettingClickRow(Icons.Default.VpnLock, "Android Kill Switch", "Запретить трафик без VPN") { try { context.startActivity(Intent("android.net.vpn.SETTINGS").apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }) } catch (e: Exception) { Toast.makeText(context, "Не поддерживается", Toast.LENGTH_SHORT).show() } }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PerformanceSettings(settingsStore: SettingsStore, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    val workersCount by settingsStore.workersPerHash.collectAsStateWithLifecycle(24)
    val captchaMethod by settingsStore.captchaSolveMethod.collectAsStateWithLifecycle("manual")
    val currentHashesFromStore by settingsStore.vkHashes.collectAsStateWithLifecycle("")
    val protocol by settingsStore.protocol.collectAsStateWithLifecycle("udp")
    val currentPeer by settingsStore.peer.collectAsStateWithLifecycle("")

    var hashesList by remember { mutableStateOf(listOf("")) }
    LaunchedEffect(currentHashesFromStore) { hashesList = currentHashesFromStore.split(",").map { it.trim() }.filter { it.isNotEmpty() }.ifEmpty { listOf("") } }

    fun updateHashes(newList: List<String>) {
        hashesList = newList
        scope.launch { settingsStore.save(currentPeer, newList.map { it.trim() }.filter { it.isNotEmpty() }.joinToString(","), "", workersCount, protocol, 9000, "") }
    }
    
    var lastWorkerCount by remember(workersCount) { mutableIntStateOf(workersCount) }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SettingsHeader("Производительность", onBack)
        CategoryCard("VK Ключи (Hashes)", Icons.Default.VpnKey) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                hashesList.forEachIndexed { index, hash ->
                    OutlinedTextField(
                        value = hash,
                        onValueChange = { val l = hashesList.toMutableList(); l[index] = it; updateHashes(l) },
                        label = { Text("Хэш ${index + 1}") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), singleLine = true,
                        trailingIcon = { if (hashesList.size > 1) IconButton(onClick = { val l = hashesList.toMutableList(); l.removeAt(index); updateHashes(l) }) { Icon(Icons.Default.DeleteOutline, null, tint = MaterialTheme.colorScheme.error) } }
                    )
                }
                if (hashesList.size < 3 && hashesList.last().isNotEmpty()) Button(onClick = { updateHashes(hashesList + "") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)) { Text("Добавить Хэш") }
            }
        }
        CategoryCard("Нагрузка", Icons.Default.Memory) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Потоки обработки", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                AnimatedContent(targetState = workersCount, label = "") { wc -> Text("$wc", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 16.sp) }
            }
            Slider(
                value = workersCount.toFloat(),
                onValueChange = {
                    val clamped = ((it / 12).roundToInt() * 12).coerceIn(12, 72)
                    if (clamped != lastWorkerCount) { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); lastWorkerCount = clamped }
                    scope.launch { settingsStore.save(currentPeer, currentHashesFromStore, "", clamped, protocol, 9000, "") }
                }, valueRange = 12f..72f, steps = 4
            )
            Text("Больше потоков — выше скорость, но сильнее расход батареи.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
        }
        CategoryCard("Решение капчи", Icons.Default.SmartToy) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                listOf("manual" to "WebView (Надежно)", "auto" to "RJS (Автомат)").forEachIndexed { i, (v, l) ->
                    SegmentedButton(
                        selected = captchaMethod == v, shape = SegmentedButtonDefaults.itemShape(index = i, count = 2),
                        onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); scope.launch { settingsStore.saveCaptchaMode(if (v == "auto") "rjs" else "wv"); settingsStore.saveCaptchaSolveMethod(v) } }
                    ) { Text(l, fontSize = 14.sp) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InterfaceSettings(settingsStore: SettingsStore, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current

    val themeMode by settingsStore.themeMode.collectAsStateWithLifecycle("system")
    val dynamicColor by settingsStore.useDynamicColor.collectAsStateWithLifecycle(true)
    val prefs = remember { context.getSharedPreferences("wdtt_ui_prefs", Context.MODE_PRIVATE) }
    var vibrationEnabled by remember { mutableStateOf(prefs.getBoolean("vibration", true)) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SettingsHeader("Интерфейс", onBack)
        CategoryCard("Внешний вид", Icons.Default.Palette) {
            Text("Тема оформления", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(Modifier.height(12.dp))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                listOf("system" to "Авто", "light" to "Светлая", "dark" to "Темная", "amoled" to "Amoled").forEachIndexed { i, (v, l) ->
                    SegmentedButton(
                        selected = themeMode == v, shape = SegmentedButtonDefaults.itemShape(index = i, count = 4),
                        onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); scope.launch { settingsStore.saveThemeMode(v) } }
                    ) { Text(l, fontSize = 12.sp, maxLines = 1) }
                }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
            SettingSwitchRow(Icons.Default.ColorLens, "Dynamic Colors", "Использовать цвета обоев системы", dynamicColor) { scope.launch { settingsStore.saveDynamicColor(it) } }
            SettingSwitchRow(Icons.Default.Vibration, "Вибрация", "Тактильный отклик интерфейса", vibrationEnabled) { vibrationEnabled = it; prefs.edit().putBoolean("vibration", it).apply() }
        }
    }
}

suspend fun addServerToStoreDirect(context: Context, settingsStore: SettingsStore, json: JSONObject) {
    val ip = json.optString("ip", "").trim()
    val name = json.optString("name", "Новый сервер").trim()
    val pass = json.optString("password", "").trim()
    if (ip.isBlank()) return

    val currentArray = try { JSONArray(settingsStore.savedServersJson.first()) } catch (e: Exception) { JSONArray() }
    var existsIdx = -1
    for (i in 0 until currentArray.length()) { if (currentArray.getJSONObject(i).optString("ip").trim() == ip) { existsIdx = i; break } }

    val newObj = JSONObject().apply { put("id", if (existsIdx != -1) currentArray.getJSONObject(existsIdx).getString("id") else UUID.randomUUID().toString()); put("name", name); put("ip", ip); put("password", pass) }
    if (existsIdx != -1) currentArray.put(existsIdx, newObj) else currentArray.put(newObj)
    settingsStore.saveServersList(currentArray.toString())

    withContext(Dispatchers.Main) { Toast.makeText(context, "Сервер $name ${if (existsIdx != -1) "обновлен" else "добавлен"}", Toast.LENGTH_SHORT).show() }
}

@Composable
fun MenuCategoryItem(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(48.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape), contentAlignment = Alignment.Center) { Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(24.dp)) }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
fun SettingsHeader(title: String, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 16.dp)) {
        IconButton(onClick = onBack, modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = MaterialTheme.colorScheme.onSurface) }
        Spacer(Modifier.width(16.dp)); Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun CategoryCard(title: String, icon: ImageVector, content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(32.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 20.dp)) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(12.dp)); Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
            content()
        }
    }
}

@Composable
private fun SettingClickRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).clickable { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onClick() }.padding(vertical = 12.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(44.dp).background(MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape), contentAlignment = Alignment.Center) { Icon(icon, null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp)) }
        Spacer(Modifier.width(16.dp))
        Column { Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, fontSize = 16.sp); Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp) }
    }
}

@Composable
private fun SettingSwitchRow(icon: ImageVector, title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val haptic = LocalHapticFeedback.current
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(44.dp).background(MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape), contentAlignment = Alignment.Center) { Icon(icon, null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp)) }
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, fontSize = 16.sp); Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp) }
        Switch(checked = checked, onCheckedChange = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onCheckedChange(it) })
    }
}

@Composable
fun ImportantInfoDialog(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(32.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column(modifier = Modifier.padding(28.dp).verticalScroll(rememberScrollState())) {
                Text("Справка", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(20.dp))
                Text("• RJS-Капча — автоматическое решение (экспериментально). В случае проблем верните WebView.\n\n• Диплинки (wdtt://) при экспорте содержат пароль от туннеля в base64. Не передавайте их третьим лицам.\n\n• Если туннель подключается, но интернета нет — обновите Хеши ВК.", style = MaterialTheme.typography.bodyLarge, lineHeight = 24.sp, fontSize = 16.sp)
                Spacer(Modifier.height(32.dp))
                Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(24.dp)) { Text("Закрыть", fontWeight = FontWeight.Bold, fontSize = 16.sp) }
            }
        }
    }
}