package com.tyrnguard.client.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
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
import com.tyrnguard.client.TyrnConfigTransfer
import com.tyrnguard.client.fetchLatestReleaseInfo
import com.tyrnguard.client.isNewerVersion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    var showExportDialog by rememberSaveable { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SettingsHeroPanel(
            icon = Icons.Default.Tune,
            title = "Настройки TyrnGuard",
            subtitle = "Сеть, ключи, капча и внешний вид собраны в короткие понятные разделы.",
            stats = listOf(
                "Сервер" to currentPeer.ifBlank { "не выбран" },
                "Версия" to BuildConfig.VERSION_NAME
            )
        )
        
        MenuCategoryItem("Сеть", "Локальный порт, MTU, DNS и SNI домен", Icons.Default.Language) { onNavigate("network") }
        MenuCategoryItem("Производительность", "VK-хеши, потоки и режим капчи", Icons.Default.Speed) { onNavigate("performance") }
        MenuCategoryItem("Интерфейс", "Тема, палитра и динамические цвета", Icons.Default.Palette) { onNavigate("interface") }
        
        CategoryCard("Синхронизация", Icons.Default.Share) {
            Text("Умный импорт понимает старые ссылки, новые пакеты настроек и сырой JSON. Экспорт можно сделать целиком или только нужной частью.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    modifier = Modifier.weight(1f).height(56.dp), shape = RoundedCornerShape(18.dp),
                    onClick = {
                        val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val text = cb.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
                        if (text.isBlank()) {
                            Toast.makeText(context, "Буфер обмена пуст", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        scope.launch {
                            try {
                                val result = withContext(Dispatchers.IO) { TyrnConfigTransfer.importFromText(settingsStore, text) }
                                Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                            } catch (_: Exception) {
                                Toast.makeText(context, "Конфиг не найден или повреждён", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                ) {
                    Icon(Icons.Default.ContentPasteGo, null); Spacer(Modifier.width(8.dp)); Text("Умный импорт", fontSize = 15.sp)
                }

                FilledTonalButton(
                    modifier = Modifier.weight(1f).height(56.dp), shape = RoundedCornerShape(18.dp),
                    onClick = { showExportDialog = true }
                ) {
                    Icon(Icons.Default.IosShare, null); Spacer(Modifier.width(8.dp)); Text("Экспорт", fontSize = 16.sp)
                }
            }
        }

        MenuCategoryItem("О приложении", "Версия, Обновления, GitHub", Icons.Default.Info) { onNavigate("about") }
        Spacer(Modifier.height(32.dp))
    }

    if (showExportDialog) {
        ExportConfigDialog(
            onDismiss = { showExportDialog = false },
            onExport = { kind, title ->
                showExportDialog = false
                scope.launch {
                    try {
                        val link = withContext(Dispatchers.IO) { TyrnConfigTransfer.buildExportLink(settingsStore, kind) }
                        val shareText = "Конфигурация TyrnGuard ($title):\n\n$link"
                        val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cb.setPrimaryClip(ClipData.newPlainText("TyrnGuard config", shareText))
                        Toast.makeText(context, "Ссылка скопирована в буфер", Toast.LENGTH_SHORT).show()
                        context.startActivity(
                            Intent.createChooser(
                                Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, shareText)
                                },
                                "Поделиться конфигурацией"
                            )
                        )
                    } catch (_: Exception) {
                        Toast.makeText(context, "Не удалось собрать экспорт", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
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
    val listenPort by settingsStore.listenPort.collectAsStateWithLifecycle(9000)

    var sniInput by remember(sni) { mutableStateOf(sni) }
    var listenInput by remember(listenPort) { mutableStateOf(listenPort.toString()) }

    fun saveListenPort() {
        val l = listenInput.toIntOrNull()?.coerceIn(1, 65535) ?: 9000
        scope.launch { settingsStore.saveListenPort(l) }
    }

    fun saveSniValue(input: String) {
        sniInput = input.trim()
        scope.launch { settingsStore.saveSni(sniInput) }
    }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SettingsHeader("Сеть", onBack, "Порты, DNS и домен для рукопожатия", Icons.Default.Language)

        SettingsHeroPanel(
            icon = Icons.Default.TravelExplore,
            title = "Сетевой профиль",
            subtitle = "Здесь меняются параметры, которые напрямую влияют на подключение.",
            stats = listOf(
                "Порт" to listenPort.toString(),
                "SNI" to sniInput.ifBlank { "не задан" }
            )
        )
        
        CategoryCard("Подключение", Icons.Default.SettingsInputComponent) {
            SettingTextField(
                value = listenInput,
                onValueChange = {
                    listenInput = it.filter { c -> c.isDigit() }.take(5)
                    saveListenPort()
                },
                label = "Локальный порт VPN",
                placeholder = "9000",
                leadingIcon = Icons.Default.Lan,
                supportingText = "Порт, на котором приложение слушает локальный VPN-трафик.",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            Spacer(Modifier.height(12.dp))
            SettingTextField(
                value = sniInput,
                onValueChange = { input -> saveSniValue(input) },
                label = "SNI домен",
                placeholder = "google.com",
                leadingIcon = Icons.Default.Public,
                supportingText = "Опционально: домен для TLS ClientHello, если сеть требует SNI.",
                trailingContent = if (sniInput.isNotBlank()) {
                    {
                        IconButton(onClick = { saveSniValue("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Очистить SNI")
                        }
                    }
                } else null
            )
        }

        CategoryCard("Пакеты", Icons.AutoMirrored.Filled.CompareArrows) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Размер пакета (MTU)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Авто обычно стабильнее, ручной MTU полезен для сложных сетей.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                ValuePill("Сейчас", if (customMtu == 0) "Авто" else "$customMtu")
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
            Text("Значение ниже 1280 возвращает автоматический режим.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        CategoryCard("DNS", Icons.Default.Dns) {
            Text("Выберите резолвер для туннеля.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                listOf("default" to "Авто", "adguard" to "AdGuard", "cloudflare" to "1.1.1.1", "custom" to "Свой").forEachIndexed { i, (v, l) ->
                    SegmentedButton(
                        selected = dnsType == v, shape = SegmentedButtonDefaults.itemShape(index = i, count = 4),
                        onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); scope.launch { settingsStore.saveCustomDns(v); TunnelManager.reloadWireGuard() } }
                    ) { Text(l, fontSize = 10.sp, maxLines = 1) }
                }
            }
            AnimatedVisibility(visible = dnsType == "custom") {
                SettingTextField(
                    value = customDnsIp,
                    onValueChange = { scope.launch { settingsStore.saveCustomDnsIp(it.trim()) } },
                    label = "IP DNS сервера",
                    placeholder = "1.1.1.1",
                    leadingIcon = Icons.Default.Dns,
                    supportingText = "IPv4-адрес DNS, который попадёт в конфиг туннеля.",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.padding(top = 12.dp)
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
        SettingsHeader("Производительность", onBack, "Хеши, потоки и режим решения капчи", Icons.Default.Speed)

        SettingsHeroPanel(
            icon = Icons.Default.Bolt,
            title = "Баланс скорости",
            subtitle = "Меньше лишних потоков — меньше расход батареи и ниже шанс словить капчу.",
            stats = listOf(
                "Потоки" to workersCount.toString(),
                "Капча" to if (captchaMode == "wv") "WebView" else "RJS"
            )
        )
        
        CategoryCard("VK Хэши (Ключи)", Icons.Default.VpnKey) {
            Text("Можно вставлять чистый хеш или ссылку vk.com/call/join — приложение оставит только нужную часть.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))

            listOf(
                Triple("Основной хеш", h1) { v: String -> h1 = stripVkUrlStatic(v); saveAll() },
                Triple("Дополнительный 1", h2) { v: String -> h2 = stripVkUrlStatic(v); saveAll() },
                Triple("Дополнительный 2", h3) { v: String -> h3 = stripVkUrlStatic(v); saveAll() }
            ).forEach { (label, value, onChange) ->
                val isShort = value.isNotBlank() && value.length < 16
                SettingTextField(
                    value = value,
                    onValueChange = onChange,
                    label = label,
                    placeholder = "vk.com/call/join/...",
                    leadingIcon = Icons.Default.Key,
                    isError = isShort,
                    supportingText = if (isShort) "Хеш слишком короткий, нужно минимум 16 символов." else null,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
        }

        CategoryCard("Мощность", Icons.Default.Memory) {
            val filledHashCount = listOf(h1, h2, h3).count { it.isNotBlank() && it.length >= 16 }.coerceAtLeast(1)
            val maxWorkers = (filledHashCount * 32).toFloat()
            val currentW = workersCount.toFloat().coerceIn(12f, maxWorkers)

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Потоки обработки", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Шаг 12 потоков, лимит зависит от количества валидных хешей.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                ValuePill("Активно", "${currentW.toInt()}")
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                listOf(12, 24, 36).forEach { preset ->
                    val target = preset.coerceAtMost(maxWorkers.toInt())
                    AssistChip(
                        onClick = { saveAll(target) },
                        label = { Text("$target") },
                        leadingIcon = if (workersCount == target) { { Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp)) } } else null,
                        shape = RoundedCornerShape(14.dp)
                    )
                }
            }
            Text("Обычно 24-36 хватает для скорости без лишнего расхода CPU.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        CategoryCard("Капча", Icons.Default.SmartToy) {
            Text("Метод решения", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
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

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SettingsHeader("Интерфейс", onBack, "Тема, палитра и цветовая адаптация", Icons.Default.Palette)

        SettingsHeroPanel(
            icon = Icons.Default.AutoAwesome,
            title = "MD3 Expressive",
            subtitle = "Без градиентов: чистые контейнеры, мягкий контраст и читаемые заголовки.",
            stats = listOf(
                "Тема" to when (themeMode) {
                    "light" -> "Светлая"
                    "dark" -> "Темная"
                    "amoled" -> "Amoled"
                    else -> "Авто"
                },
                "Палитра" to themePalette
            )
        )

        CategoryCard("Внешний вид", Icons.Default.Palette) {
            val themes = listOf("system" to "Авто", "light" to "Светлая", "dark" to "Темная", "amoled" to "Amoled")
            Text("Режим темы", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
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
                    Text("Палитра", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    ColorPalettePicker(
                        selected = themePalette,
                        onSelect = { palette -> scope.launch { settingsStore.saveThemePalette(palette) } }
                    )
                }
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun ColorPalettePicker(selected: String, onSelect: (String) -> Unit) {
    val palettes = listOf(
        PaletteOption("tyrn", "Tyrn", Color(0xFF176B74)),
        PaletteOption("forest", "Лес", Color(0xFF386A20)),
        PaletteOption("violet", "Фиолет", Color(0xFF7650A6))
    )

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        palettes.forEach { option ->
            val isSelected = selected == option.id
            Surface(
                onClick = { onSelect(option.id) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(18.dp),
                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                border = BorderStroke(
                    width = if (isSelected) 2.dp else 1.dp,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(option.color)
                    )
                    Text(option.label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }
        }
    }
}

private data class PaletteOption(val id: String, val label: String, val color: Color)

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
    val glassColor = if (isDark) colors.surface.copy(alpha = 0.46f) else Color.White.copy(alpha = 0.54f)
    val glassBorder = colors.outlineVariant.copy(alpha = if (isDark) 0.50f else 0.32f)

    Surface(shape = RoundedCornerShape(32.dp), color = colors.surfaceContainerHigh, contentColor = colors.onSurface, shadowElevation = 6.dp) {
        Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(32.dp)).padding(22.dp)) {
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
private fun ExportConfigDialog(onDismiss: () -> Unit, onExport: (kind: String, title: String) -> Unit) {
    val options = listOf(
        ExportConfigOption(TyrnConfigTransfer.KIND_ALL, "Всё сразу", "Серверы, сеть, производительность, интерфейс и исключения", Icons.Default.Inventory2),
        ExportConfigOption(TyrnConfigTransfer.KIND_SERVERS, "Все серверы", "Список серверов, активный сервер и пароль подключения", Icons.Default.Dns),
        ExportConfigOption(TyrnConfigTransfer.KIND_ACTIVE_SERVER, "Активный сервер", "Только выбранный сервер для быстрого переноса", Icons.Default.RadioButtonChecked),
        ExportConfigOption(TyrnConfigTransfer.KIND_NETWORK, "Сеть", "Протокол, порты, SNI, MTU и DNS", Icons.Default.Language),
        ExportConfigOption(TyrnConfigTransfer.KIND_PERFORMANCE, "Производительность", "VK-хеши, потоки и режим капчи", Icons.Default.Speed),
        ExportConfigOption(TyrnConfigTransfer.KIND_INTERFACE, "Интерфейс", "Тема, динамические цвета и палитра", Icons.Default.Palette)
    )

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(30.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, contentColor = MaterialTheme.colorScheme.onSurface) {
            Column(modifier = Modifier.padding(22.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(48.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.IosShare, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Экспорт конфигурации", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                        Text("Выберите, что положить в ссылку", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "Закрыть") }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                options.forEach { option ->
                    SettingClickRow(option.icon, option.title, option.subtitle) { onExport(option.kind, option.title) }
                }
            }
        }
    }
}

private data class ExportConfigOption(
    val kind: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector
)

@Composable
private fun SettingsHeroPanel(
    icon: ImageVector,
    title: String,
    subtitle: String,
    stats: List<Pair<String, String>> = emptyList()
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = colors.primaryContainer,
        contentColor = colors.onPrimaryContainer,
        border = BorderStroke(1.dp, colors.primary.copy(alpha = 0.18f)),
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(shape = RoundedCornerShape(18.dp), color = colors.primary.copy(alpha = 0.14f)) {
                    Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                        Icon(icon, contentDescription = null, tint = colors.onPrimaryContainer, modifier = Modifier.size(24.dp))
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = colors.onPrimaryContainer)
                    Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = colors.onPrimaryContainer.copy(alpha = 0.78f), lineHeight = 20.sp)
                }
            }

            if (stats.isNotEmpty()) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    stats.take(2).forEach { (label, value) ->
                        ValuePill(label = label, value = value, modifier = Modifier.weight(1f), elevated = true)
                    }
                }
            }
        }
    }
}

@Composable
private fun ValuePill(label: String, value: String, modifier: Modifier = Modifier, elevated: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.heightIn(min = 44.dp),
        shape = RoundedCornerShape(16.dp),
        color = if (elevated) colors.surface.copy(alpha = 0.56f) else colors.secondaryContainer.copy(alpha = 0.72f),
        contentColor = if (elevated) colors.onSurface else colors.onSecondaryContainer,
        border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.28f))
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = LocalContentColor.current.copy(alpha = 0.70f), maxLines = 1)
            Text(value, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

@Composable
private fun SettingTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    supportingText: String? = null,
    trailingContent: (@Composable (() -> Unit))? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    isError: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        leadingIcon = if (leadingIcon != null) {
            { Icon(leadingIcon, contentDescription = null) }
        } else null,
        trailingIcon = trailingContent,
        supportingText = if (supportingText != null) {
            { Text(supportingText, color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant) }
        } else null,
        isError = isError,
        keyboardOptions = keyboardOptions,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        singleLine = true
    )
}

@Composable
fun MenuCategoryItem(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.30f)),
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(50.dp)) {
                Box(contentAlignment = Alignment.Center) { Icon(icon, null, tint = MaterialTheme.colorScheme.onSecondaryContainer) }
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
fun SettingsHeader(title: String, onBack: () -> Unit, subtitle: String? = null, icon: ImageVector? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        IconButton(onClick = onBack, modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (icon != null) {
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.size(44.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                }
            }
        }
    }
}

@Composable
private fun CategoryCard(title: String, icon: ImageVector, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)),
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth().animateContentSize()
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 18.dp)) {
                Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(42.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(21.dp))
                    }
                }
                Spacer(Modifier.width(12.dp)); Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
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
