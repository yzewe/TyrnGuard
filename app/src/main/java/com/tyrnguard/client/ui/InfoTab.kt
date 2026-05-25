package com.tyrnguard.client.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tyrnguard.client.BuildConfig
import com.tyrnguard.client.SettingsStore
import com.tyrnguard.client.TunnelManager
import com.tyrnguard.client.fetchLatestReleaseInfo
import com.tyrnguard.client.isNewerVersion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

private const val RJS_TEMPORARILY_DISABLED = true
private const val WORKERS_PER_GROUP = 12

@Composable
fun InfoTab() {
    var currentScreen by rememberSaveable { mutableStateOf("main") }

    BackHandler(enabled = currentScreen != "main") { currentScreen = "main" }

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = currentScreen,
            transitionSpec = {
                val animationSpec = spring<IntOffset>(stiffness = Spring.StiffnessMedium)
                if (targetState != "main") {
                    slideInHorizontally(animationSpec) { it } + fadeIn() togetherWith slideOutHorizontally(animationSpec) { -it / 2 } + fadeOut()
                } else {
                    slideInHorizontally(animationSpec) { -it / 2 } + fadeIn() togetherWith slideOutHorizontally(animationSpec) { it } + fadeOut()
                }
            },
            label = "settings_navigation"
        ) { screen ->
            when (screen) {
                "main" -> MainSettingsMenu { currentScreen = it }
                "network" -> NetworkSettings { currentScreen = "main" }
                "performance" -> PerformanceSettings { currentScreen = "main" }
                "interface" -> InterfaceSettings { currentScreen = "main" }
                "about" -> AboutScreen { currentScreen = "main" }
            }
        }
    }
}

@Composable
fun MainSettingsMenu(onNavigate: (String) -> Unit) {
    val context = LocalContext.current
    val settingsStore = remember { SettingsStore(context) }
    val scope = rememberCoroutineScope()
    val currentPeer by settingsStore.peer.collectAsStateWithLifecycle("")

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Настройки", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
        
        MenuCategoryItem("Сеть", "Ручные порты, MTU, DNS, SNI", Icons.Default.Language) { onNavigate("network") }
        MenuCategoryItem("Производительность", "Ключи, Потоки, Капча (RJS)", Icons.Default.Speed) { onNavigate("performance") }
        MenuCategoryItem("Интерфейс", "Темы, Цвета, Отклик", Icons.Default.Palette) { onNavigate("interface") }
        
        CategoryCard("Синхронизация", Icons.Default.Share) {
            Text("Импорт запустит настройку по ссылке из буфера обмена.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    modifier = Modifier.weight(1f).height(56.dp), shape = RoundedCornerShape(20.dp),
                    onClick = {
                        val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val text = cb.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
                        if (text.contains("tyrnguard://config?data=")) {
                            try {
                                val b64Data = text.substringAfter("data=")
                                val json = JSONObject(String(Base64.decode(b64Data, Base64.URL_SAFE)))
                                scope.launch { addServerToStoreDirect(context, settingsStore, json) }
                            } catch (e: Exception) { Toast.makeText(context, "Ошибка чтения ссылки", Toast.LENGTH_SHORT).show() }
                        } else Toast.makeText(context, "Ссылка не найдена в буфере", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Icon(Icons.Default.ContentPasteGo, null); Spacer(Modifier.width(8.dp)); Text("Импорт", fontSize = 16.sp)
                }

                FilledTonalButton(
                    modifier = Modifier.weight(1f).height(56.dp), shape = RoundedCornerShape(20.dp),
                    onClick = {
                        scope.launch {
                            val serversJson = settingsStore.savedServersJson.first()
                            if (currentPeer.isBlank() || serversJson.isBlank()) { 
                                Toast.makeText(context, "Сначала выберите сервер на главном экране", Toast.LENGTH_SHORT).show(); return@launch 
                            }
                            val servers = JSONArray(serversJson)
                            var activeObj: JSONObject? = null
                            for (i in 0 until servers.length()) { if (servers.getJSONObject(i).optString("ip") == currentPeer) { activeObj = servers.getJSONObject(i); break } }
                            if (activeObj == null) { Toast.makeText(context, "Активный сервер не найден", Toast.LENGTH_SHORT).show(); return@launch }
                            
                            val b64 = Base64.encodeToString(activeObj.toString().toByteArray(), Base64.URL_SAFE or Base64.NO_WRAP)
                            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, "Конфигурация TyrnGuard:\n\ntyrnguard://config?data=$b64") }, "Поделиться конфигурацией"))
                        }
                    }
                ) {
                    Icon(Icons.Default.IosShare, null); Spacer(Modifier.width(8.dp)); Text("Экспорт", fontSize = 16.sp)
                }
            }
        }

        MenuCategoryItem("О приложении", "Версия, Обновления, GitHub", Icons.Default.Info) { onNavigate("about") }
        Spacer(Modifier.height(32.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkSettings(onBack: () -> Unit) {
    val context = LocalContext.current
    val settingsStore = remember { SettingsStore(context) }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    val customMtu by settingsStore.customMtu.collectAsStateWithLifecycle(0)
    val dnsType by settingsStore.customDns.collectAsStateWithLifecycle("default")
    val customDnsIp by settingsStore.customDnsIp.collectAsStateWithLifecycle("1.1.1.1")
    val sni by settingsStore.sni.collectAsStateWithLifecycle("")
    
    val manualPortsEnabled by settingsStore.manualPortsEnabled.collectAsStateWithLifecycle(false)
    val serverDtlsPort by settingsStore.serverDtlsPort.collectAsStateWithLifecycle(56000)
    val serverWgPort by settingsStore.serverWgPort.collectAsStateWithLifecycle(56001)
    val listenPort by settingsStore.listenPort.collectAsStateWithLifecycle(9000)

    var sniInput by remember(sni) { mutableStateOf(sni) }
    var dtlsInput by remember(serverDtlsPort) { mutableStateOf(serverDtlsPort.toString()) }
    var wgInput by remember(serverWgPort) { mutableStateOf(serverWgPort.toString()) }
    var listenInput by remember(listenPort) { mutableStateOf(listenPort.toString()) }

    fun savePorts() {
        val d = dtlsInput.toIntOrNull()?.coerceIn(1, 65535) ?: 56000
        val w = wgInput.toIntOrNull()?.coerceIn(1, 65535) ?: 56001
        val l = listenInput.toIntOrNull()?.coerceIn(1, 65535) ?: 9000
        scope.launch { settingsStore.savePorts(d, w, l) }
    }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SettingsHeader("Сеть", onBack)
        
        CategoryCard("Порты", Icons.Default.SettingsInputComponent) {
            SettingSwitchRow(
                icon = Icons.Default.SettingsEthernet, title = "Ручные порты сервера", subtitle = "Использовать нестандартные порты",
                checked = manualPortsEnabled
            ) { scope.launch { settingsStore.saveManualPortsEnabled(it) } }

            AnimatedVisibility(visible = manualPortsEnabled) {
                Column {
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(value = dtlsInput, onValueChange = { dtlsInput = it.filter { c -> c.isDigit() }.take(5); savePorts() }, label = { Text("Порт сервера DTLS") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), singleLine = true)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = wgInput, onValueChange = { wgInput = it.filter { c -> c.isDigit() }.take(5); savePorts() }, label = { Text("Порт сервера WireGuard") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), singleLine = true)
                }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(value = listenInput, onValueChange = { listenInput = it.filter { c -> c.isDigit() }.take(5); savePorts() }, label = { Text("Локальный порт VPN") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), singleLine = true)
        }

        CategoryCard("Расширенные", Icons.AutoMirrored.Filled.CompareArrows) {
            Text("SNI (Опционально)", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            OutlinedTextField(
                value = sniInput,
                onValueChange = { input -> 
                    sniInput = input.trim()
                    scope.launch { 
                        val p = settingsStore.peer.first()
                        val h = settingsStore.vkHashes.first()
                        val w = settingsStore.workersPerHash.first()
                        val l = settingsStore.listenPort.first()
                        val pr = settingsStore.protocol.first()
                        settingsStore.save(p, h, "", w, pr, l, sniInput)
                    } 
                },
                placeholder = { Text("google.com") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                singleLine = true
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Размер пакета (MTU)", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                Text(if (customMtu == 0) "Авто" else "$customMtu", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
            Slider(
                value = if (customMtu == 0) 1279f else customMtu.toFloat(),
                onValueChange = {
                    val v = if (it < 1280f) 0 else it.roundToInt()
                    scope.launch { settingsStore.saveCustomMtu(v) }
                },
                onValueChangeFinished = { scope.launch { TunnelManager.reloadWireGuard() } }, 
                valueRange = 1279f..1500f
            )
        }

        CategoryCard("DNS Сервер", Icons.Default.Dns) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                listOf("default" to "Авто", "adguard" to "AdGuard", "cloudflare" to "1.1.1.1", "custom" to "Свой").forEachIndexed { i, (v, l) ->
                    SegmentedButton(
                        selected = dnsType == v, shape = SegmentedButtonDefaults.itemShape(index = i, count = 4),
                        onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); scope.launch { settingsStore.saveCustomDns(v); TunnelManager.reloadWireGuard() } }
                    ) { Text(l, fontSize = 10.sp, maxLines = 1) }
                }
            }
            AnimatedVisibility(visible = dnsType == "custom") {
                OutlinedTextField(
                    value = customDnsIp,
                    onValueChange = { scope.launch { settingsStore.saveCustomDnsIp(it.trim()) } },
                    label = { Text("IP DNS сервера") },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    shape = RoundedCornerShape(16.dp)
                )
            }
        }
    }
}

private fun stripVkUrlStatic(input: String): String {
    var s = input.trim()
    s = s.removePrefix("https://vk.com/call/join/")
        .removePrefix("http://vk.com/call/join/")
        .removePrefix("vk.com/call/join/")
    val qIdx = s.indexOf('?')
    if (qIdx != -1) s = s.substring(0, qIdx)
    val hIdx = s.indexOf('#')
    if (hIdx != -1) s = s.substring(0, hIdx)
    return s.trimEnd('/')
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PerformanceSettings(onBack: () -> Unit) {
    val context = LocalContext.current
    val settingsStore = remember { SettingsStore(context) }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    val workersCount by settingsStore.workersPerHash.collectAsStateWithLifecycle(24)
    val captchaMethod by settingsStore.captchaSolveMethod.collectAsStateWithLifecycle("manual")
    val captchaMode by settingsStore.captchaMode.collectAsStateWithLifecycle("wv")
    val currentHashes by settingsStore.vkHashes.collectAsStateWithLifecycle("")

    var h1 by remember(currentHashes) { mutableStateOf(currentHashes.split(",").getOrElse(0){""}) }
    var h2 by remember(currentHashes) { mutableStateOf(currentHashes.split(",").getOrElse(1){""}) }
    var h3 by remember(currentHashes) { mutableStateOf(currentHashes.split(",").getOrElse(2){""}) }

    fun saveAll(newWorkers: Int = workersCount) {
        val combined = listOf(h1, h2, h3).map { it.trim() }.filter { it.isNotEmpty() }.joinToString(",")
        scope.launch { 
            val peer = settingsStore.peer.first()
            val proto = settingsStore.protocol.first()
            val port = settingsStore.listenPort.first()
            val sni = settingsStore.sni.first()
            settingsStore.save(peer, combined, "", newWorkers, proto, port, sni) 
        }
    }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SettingsHeader("Производительность", onBack)
        
        CategoryCard("VK Хэши (Ключи)", Icons.Default.VpnKey) {
            Text("Больше хешей — выше лимит потоков и лучшее распределение нагрузки.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))

            listOf(
                Triple("Основной хеш", h1) { v: String -> h1 = stripVkUrlStatic(v); saveAll() },
                Triple("Дополнительный 1", h2) { v: String -> h2 = stripVkUrlStatic(v); saveAll() },
                Triple("Дополнительный 2", h3) { v: String -> h3 = stripVkUrlStatic(v); saveAll() }
            ).forEach { (label, value, onChange) ->
                val isShort = value.isNotBlank() && value.length < 16
                OutlinedTextField(
                    value = value, onValueChange = onChange, label = { Text(label) },
                    isError = isShort,
                    supportingText = if (isShort) { { Text("Хеш слишком короткий (мин. 16)", color = MaterialTheme.colorScheme.error) } } else null,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), shape = RoundedCornerShape(16.dp)
                )
            }
        }

        CategoryCard("Мощность", Icons.Default.Memory) {
            val filledHashCount = listOf(h1, h2, h3).count { it.isNotBlank() && it.length >= 16 }.coerceAtLeast(1)
            val maxWorkers = (filledHashCount * 32).toFloat()
            val currentW = workersCount.toFloat().coerceIn(12f, maxWorkers)

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Потоки обработки", fontWeight = FontWeight.Bold)
                Text("${currentW.toInt()}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
            Slider(
                value = currentW,
                onValueChange = {
                    val stepped = ((it / WORKERS_PER_GROUP).roundToInt() * WORKERS_PER_GROUP).toFloat()
                    val finalW = stepped.coerceIn(12f, maxWorkers).toInt()
                    if (finalW != workersCount) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        saveAll(finalW)
                    }
                },
                valueRange = 12f..maxWorkers
            )
            Text("Оптимально: 24-36. Больше — выше скорость, но риск блокировки IP.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        CategoryCard("Капча", Icons.Default.SmartToy) {
            Text("Метод решения", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = captchaMode == "wv", onClick = { scope.launch { settingsStore.saveCaptchaMode("wv") } }, label = { Text("WebView") }, shape = RoundedCornerShape(16.dp))
                FilterChip(selected = captchaMode == "rjs", onClick = { if (!RJS_TEMPORARILY_DISABLED) scope.launch { settingsStore.saveCaptchaMode("rjs"); settingsStore.saveCaptchaSolveMethod("auto") } }, label = { Text("Reverse JS (Auto)") }, shape = RoundedCornerShape(16.dp), enabled = !RJS_TEMPORARILY_DISABLED)
            }
            if (RJS_TEMPORARILY_DISABLED) {
                Text("RJS временно отключен разработчиком проекта.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 4.dp))
            }
            
            AnimatedVisibility(visible = captchaMode == "wv") {
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    Text("Режим WebView", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = captchaMethod == "manual", onClick = { scope.launch { settingsStore.saveCaptchaSolveMethod("manual") } }, label = { Text("Ручной") }, shape = RoundedCornerShape(16.dp))
                        FilterChip(selected = captchaMethod == "auto", onClick = { scope.launch { settingsStore.saveCaptchaSolveMethod("auto") } }, label = { Text("Авто") }, shape = RoundedCornerShape(16.dp))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InterfaceSettings(onBack: () -> Unit) {
    val context = LocalContext.current
    val settingsStore = remember { SettingsStore(context) }
    val scope = rememberCoroutineScope()

    val themeMode by settingsStore.themeMode.collectAsStateWithLifecycle("system")
    val dynamicColor by settingsStore.useDynamicColor.collectAsStateWithLifecycle(false)
    val themePalette by settingsStore.themePalette.collectAsStateWithLifecycle("tyrn")

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SettingsHeader("Интерфейс", onBack)
        CategoryCard("Тема", Icons.Default.Palette) {
            val themes = listOf("system" to "Авто", "light" to "Светлая", "dark" to "Темная", "amoled" to "Amoled")
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                themes.forEachIndexed { i, (v, l) ->
                    SegmentedButton(
                        selected = themeMode == v, shape = SegmentedButtonDefaults.itemShape(index = i, count = 4),
                        onClick = { scope.launch { settingsStore.saveThemeMode(v) } }
                    ) { Text(l, fontSize = 10.sp) }
                }
            }
            Spacer(Modifier.height(16.dp))
            SettingSwitchRow(
                icon = Icons.Default.ColorLens, title = "Динамические цвета", subtitle = "Цвета из обоев системы",
                checked = dynamicColor, enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            ) { scope.launch { settingsStore.saveDynamicColor(it) } }
            AnimatedVisibility(visible = !dynamicColor) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    HorizontalDivider(modifier = Modifier.padding(top = 10.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Text("РџР°Р»РёС‚СЂР°", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        listOf("tyrn" to "Tyrn", "forest" to "Forest", "violet" to "Violet").forEachIndexed { i, (v, l) ->
                            SegmentedButton(
                                selected = themePalette == v,
                                shape = SegmentedButtonDefaults.itemShape(index = i, count = 3),
                                onClick = { scope.launch { settingsStore.saveThemePalette(v) } }
                            ) { Text(l, fontSize = 11.sp, maxLines = 1) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentVersion = remember { "v${BuildConfig.VERSION_NAME.removePrefix("v")}" }
    
    var updateStatus by remember { mutableStateOf("Проверить обновления") }
    var isCheckingUpdates by remember { mutableStateOf(false) }
    var showHelpDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SettingsHeader("О приложении", onBack)
        
        InfoHeroCard(currentVersion = currentVersion) {
            try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/yzewe/TyrnGuard"))) } catch (_: Exception) {}
        }
        
        CategoryCard("Действия", Icons.Default.Info) {
            SettingClickRow(Icons.AutoMirrored.Filled.HelpOutline, "Справка", "Как решать капчу и потоки") { showHelpDialog = true }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            
            SettingClickRow(Icons.Default.ContentCopy, "Собрать отчёт", "Скопировать данные об устройстве") {
                val clipboard = context.getSystemService(ClipboardManager::class.java)
                clipboard?.setPrimaryClip(ClipData.newPlainText("WDTT Report", buildSupportReport()))
                Toast.makeText(context, "Отчёт сформирован и скопирован", Toast.LENGTH_SHORT).show()
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            
            SettingClickRow(if (isCheckingUpdates) Icons.Default.HourglassEmpty else Icons.Default.Update, "Проверить обновления", updateStatus) {
                if (isCheckingUpdates) return@SettingClickRow
                isCheckingUpdates = true
                updateStatus = "Проверяем GitHub releases..."
                scope.launch {
                    val release = fetchLatestReleaseInfo(currentVersion)
                    val latest = release?.versionTag
                    isCheckingUpdates = false
                    updateStatus = when {
                        latest == null -> "Не удалось проверить"
                        !isNewerVersion(currentVersion, latest) -> "У вас последняя версия: $latest"
                        else -> "Доступна новая версия $latest"
                    }
                }
            }
        }

        CategoryCard("О проекте", Icons.Default.Code) {
            SettingClickRow(Icons.Default.Person, "Автор Android-версии", "GitHub профиль amurcanov") {
                try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/amurcanov"))) } catch (_: Exception) {}
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            SettingClickRow(Icons.Default.Code, "Форк TyrnGuard (yzewe)", "Редизайн и модификации") {
                try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/yzewe/TyrnGuard"))) } catch (_: Exception) {}
            }
        }
        Spacer(Modifier.height(32.dp))
    }

    if (showHelpDialog) ImportantInfoDialog { showHelpDialog = false }
}

private val Android16BlobShape: Shape = GenericShape { size, _ ->
    val centerX = size.width / 2f
    val centerY = size.height / 2f
    val outerRadius = min(size.width, size.height) / 2f
    val innerRadius = outerRadius * 0.92f
    val points = 14
    for (i in 0 until points * 2) {
        val angle = (-PI / 2.0) + (i * PI / points)
        val radius = if (i % 2 == 0) outerRadius else innerRadius
        val x = centerX + (radius * cos(angle)).toFloat()
        val y = centerY + (radius * sin(angle)).toFloat()
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

@Composable
private fun InfoHeroCard(currentVersion: String, onSupportClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val isDark = colors.background.luminance() < 0.22f
    val heroBrush = remember(colors.primaryContainer, colors.secondaryContainer, colors.surfaceVariant) {
        Brush.linearGradient(listOf(colors.primaryContainer, colors.secondaryContainer, colors.surfaceVariant))
    }
    val glassColor = if (isDark) colors.surface.copy(alpha = 0.46f) else Color.White.copy(alpha = 0.54f)
    val glassBorder = colors.outlineVariant.copy(alpha = if (isDark) 0.50f else 0.32f)

    Surface(shape = RoundedCornerShape(32.dp), color = Color.Transparent, contentColor = MaterialTheme.colorScheme.onSurface, shadowElevation = 10.dp) {
        Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(32.dp)).background(heroBrush).padding(22.dp)) {
            Box(modifier = Modifier.align(Alignment.TopEnd).offset(x = 30.dp, y = (-34).dp).size(138.dp).clip(Android16BlobShape).background(colors.primary.copy(alpha = 0.10f)))
            Box(modifier = Modifier.align(Alignment.BottomEnd).offset(x = 26.dp, y = 30.dp).size(112.dp).clip(Android16BlobShape).background(colors.secondary.copy(alpha = 0.12f)))

            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = RoundedCornerShape(18.dp), color = glassColor, border = BorderStroke(1.dp, glassBorder), modifier = Modifier.weight(1f)) {
                        Text("TyrnGuard", modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    }
                    Surface(shape = RoundedCornerShape(18.dp), color = colors.primary.copy(alpha = if (isDark) 0.18f else 0.10f), border = BorderStroke(1.dp, colors.primary.copy(alpha = if (isDark) 0.22f else 0.14f)), modifier = Modifier.weight(1f)) {
                        Text(currentVersion, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("TyrnGuard VPN", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Black, fontSize = 30.sp, lineHeight = 34.sp), color = colors.onSurface)
                    Text("Модифицированный Android-клиент для TURN/VK туннеля с измененным современным дизайном.", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, lineHeight = 21.sp)
                }

                Button(onClick = onSupportClick, shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth().height(54.dp)) {
                    Icon(Icons.Default.Favorite, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Поддержать проект (GitHub)", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
        }
    }
}

private fun buildSupportReport(): String {
    val androidVersion = Build.VERSION.RELEASE ?: "?"
    val sdkInt = Build.VERSION.SDK_INT
    val primaryAbi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty().ifBlank { "unknown" }
    val supportedAbis = Build.SUPPORTED_ABIS.joinToString().ifBlank { "unknown" }
    val manufacturer = Build.MANUFACTURER.orEmpty().ifBlank { "unknown" }
    val brand = Build.BRAND.orEmpty().ifBlank { "unknown" }
    val model = Build.MODEL.orEmpty().ifBlank { "unknown" }
    val device = Build.DEVICE.orEmpty().ifBlank { "unknown" }
    val product = Build.PRODUCT.orEmpty().ifBlank { "unknown" }
    val hardware = Build.HARDWARE.orEmpty().ifBlank { "unknown" }
    val board = Build.BOARD.orEmpty().ifBlank { "unknown" }
    val buildId = Build.ID.orEmpty().ifBlank { "unknown" }
    val buildType = Build.TYPE.orEmpty().ifBlank { "unknown" }

    return buildString {
        appendLine("Версия приложения: ${BuildConfig.VERSION_NAME}")
        appendLine("Андроид: $androidVersion (SDK $sdkInt)")
        appendLine("Устройство: $manufacturer / $brand / $model")
        appendLine("Код устройства: $device")
        appendLine("Продукт: $product")
        appendLine("ABI: $primaryAbi")
        appendLine("Все ABI: $supportedAbis")
        appendLine("Hardware: $hardware")
        appendLine("Board: $board")
        appendLine("Build ID: $buildId")
        appendLine("Build type: $buildType")
    }.trim()
}

suspend fun addServerToStoreDirect(context: Context, settingsStore: SettingsStore, json: JSONObject) {
    val ip = json.optString("ip", "").trim()
    val name = json.optString("name", "Импортированный сервер").trim()
    val pass = json.optString("password", "").trim()
    if (ip.isBlank()) return

    val currentArray = try { JSONArray(settingsStore.savedServersJson.first()) } catch (e: Exception) { JSONArray() }
    var existsIdx = -1
    for (i in 0 until currentArray.length()) { if (currentArray.getJSONObject(i).optString("ip").trim() == ip) { existsIdx = i; break } }

    val newObj = JSONObject().apply { put("id", if (existsIdx != -1) currentArray.getJSONObject(existsIdx).getString("id") else UUID.randomUUID().toString()); put("name", name); put("ip", ip); put("password", pass) }
    if (existsIdx != -1) currentArray.put(existsIdx, newObj) else currentArray.put(newObj)
    settingsStore.saveServersList(currentArray.toString())

    withContext(Dispatchers.Main) { Toast.makeText(context, "Сервер '$name' ${if (existsIdx != -1) "обновлен" else "добавлен"}", Toast.LENGTH_SHORT).show() }
}

@Composable
fun ImportantInfoDialog(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(32.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column(modifier = Modifier.padding(28.dp).verticalScroll(rememberScrollState())) {
                Text("Справка", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(20.dp))
                Text("• RJS-Капча — автоматическое решение (экспериментально). Временно отключено разработчиком.\n\n• Связь потоков — Рекомендую выбирать 12-36 потока для меньшего количества капч.\n\n• Как решать капчу — Нужно просто потянуть слайдер вправо так, чтобы все элементы идеально сошлись в пазле.", style = MaterialTheme.typography.bodyLarge, lineHeight = 24.sp, fontSize = 16.sp)
                Spacer(Modifier.height(32.dp))
                Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(24.dp)) { Text("Закрыть", fontWeight = FontWeight.Bold, fontSize = 16.sp) }
            }
        }
    }
}

@Composable
fun MenuCategoryItem(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(26.dp),
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f)),
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .background(Brush.horizontalGradient(listOf(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.colorScheme.surfaceContainer)))
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(48.dp).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape), contentAlignment = Alignment.Center) { Icon(icon, null, tint = MaterialTheme.colorScheme.onSecondaryContainer) }
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
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
        IconButton(onClick = onBack, modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
        Spacer(Modifier.width(16.dp)); Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun CategoryCard(title: String, icon: ImageVector, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(26.dp),
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.26f)),
        tonalElevation = 2.dp,
        shadowElevation = 3.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .background(Brush.verticalGradient(listOf(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.colorScheme.surfaceContainer)))
                .padding(22.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 20.dp)) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(38.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(20.dp))
                    }
                }
                Spacer(Modifier.width(12.dp)); Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
            content()
        }
    }
}

@Composable
private fun SettingClickRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { onClick() }.padding(vertical = 12.dp, horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(40.dp).background(MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape), contentAlignment = Alignment.Center) { Icon(icon, null, modifier = Modifier.size(20.dp)) }
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold); Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SettingSwitchRow(icon: ImageVector, title: String, subtitle: String, checked: Boolean, enabled: Boolean = true, onCheckedChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().alpha(if(enabled) 1f else 0.5f).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(40.dp).background(MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape), contentAlignment = Alignment.Center) { Icon(icon, null, modifier = Modifier.size(20.dp)) }
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold); Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}
