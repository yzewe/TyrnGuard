package com.tyrnguard.client.ui

import android.widget.Toast
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import com.tyrnguard.client.DeployManager
import com.tyrnguard.client.SettingsStore
import com.tyrnguard.client.TunnelManager
import com.tyrnguard.client.TunnelService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Properties

private const val CMD_TIMEOUT = 900000L // 15 minutes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeployTab() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsStore = remember { SettingsStore(context) }

    LaunchedEffect(Unit) { DeployManager.init(context) }

    val savedIp by settingsStore.deployIp.collectAsStateWithLifecycle(initialValue = "")
    val savedLogin by settingsStore.deployLogin.collectAsStateWithLifecycle(initialValue = "")
    val savedPassword by settingsStore.deployPassword.collectAsStateWithLifecycle(initialValue = "")

    var ip by remember { mutableStateOf("") }
    var login by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    val savedMainPass by settingsStore.deployMainPassword.collectAsStateWithLifecycle(initialValue = "")
    val savedAdminId by settingsStore.deployAdminId.collectAsStateWithLifecycle(initialValue = "")
    val savedBotToken by settingsStore.deployBotToken.collectAsStateWithLifecycle(initialValue = "")
    val savedSshPort by settingsStore.deploySshPort.collectAsStateWithLifecycle(initialValue = "22")
    val savedManualPorts by settingsStore.manualPortsEnabled.collectAsStateWithLifecycle(initialValue = false)
    val savedServerDtlsPort by settingsStore.serverDtlsPort.collectAsStateWithLifecycle(initialValue = 56000)
    val savedServerWgPort by settingsStore.serverWgPort.collectAsStateWithLifecycle(initialValue = 56001)

    var showSecretsDialog by remember { mutableStateOf(false) }
    var showUninstallDialog by remember { mutableStateOf(false) }

    var showSuccessBanner by rememberSaveable { mutableStateOf(false) }
    var successCountdown by rememberSaveable { mutableIntStateOf(5) }

    LaunchedEffect(showSuccessBanner) {
        if (showSuccessBanner) {
            while (successCountdown > 0) {
                kotlinx.coroutines.delay(1000)
                successCountdown--
            }
            showSuccessBanner = false
        }
    }

    val isDeploying by DeployManager.isDeploying.collectAsStateWithLifecycle()
    val deployProgress by DeployManager.deployProgress.collectAsStateWithLifecycle()
    val currentStep by DeployManager.currentStep.collectAsStateWithLifecycle()

    LaunchedEffect(savedIp) { if (savedIp.isNotEmpty()) ip = savedIp }
    LaunchedEffect(savedLogin) { if (savedLogin.isNotEmpty()) login = savedLogin }
    LaunchedEffect(savedPassword) { if (savedPassword.isNotEmpty()) password = savedPassword }
    val animatedProgress by animateFloatAsState(
        targetValue = deployProgress,
        animationSpec = tween(durationMillis = 1200, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "progress"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Установка сервера", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))

        // ═══ SSH Доступ ═══
        CategoryCard("SSH Доступ", Icons.Default.Terminal) {
            OutlinedTextField(
                value = ip,
                onValueChange = {
                    ip = it.filter { c -> !c.isWhitespace() }
                    scope.launch { settingsStore.saveDeploy(ip, login, password, savedSshPort) }
                },
                label = { Text("IP сервера или домен") },
                placeholder = { Text("1.2.3.4 (без порта)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                enabled = !isDeploying,
            )

            Spacer(Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = login,
                    onValueChange = {
                        login = it.filter { c -> !c.isWhitespace() }
                        scope.launch { settingsStore.saveDeploy(ip, login, password, savedSshPort) }
                    },
                    label = { Text("Логин") },
                    placeholder = { Text("root") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    enabled = !isDeploying,
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it.filter { c -> !c.isWhitespace() }
                        scope.launch { settingsStore.saveDeploy(ip, login, password, savedSshPort) }
                    },
                    label = { Text("Пароль SSH") },
                    placeholder = { Text("password") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    enabled = !isDeploying,
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            SettingSwitchRow(
                icon = Icons.Default.SettingsEthernet,
                title = "Ручные порты",
                subtitle = "Настроить нестандартные порты",
                checked = savedManualPorts,
                enabled = !isDeploying,
                onCheckedChange = { enabled ->
                    scope.launch { settingsStore.saveManualPortsEnabled(enabled) }
                }
            )
        }

        // ═══ Прогресс деплоя ═══
        AnimatedVisibility(visible = isDeploying) {
            CategoryCard("Прогресс установки", Icons.Default.CloudUpload) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = currentStep,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${(animatedProgress * 100).toInt()}%",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            }
        }

        // ═══ Кнопки управления ═══
        CategoryCard("Управление", Icons.Default.Build) {
            Text("Обязательно задайте секреты перед первой установкой сервера.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 16.dp))

            FilledTonalButton(
                onClick = { showSecretsDialog = true },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(20.dp)
            ) {
                Icon(Icons.Default.Key, null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    if (savedManualPorts) "Секреты (BOT, Пароли, Порты)" else "Секреты (BOT, Пароли)",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = {
                        if (ip.isBlank() || password.isBlank() || savedMainPass.isBlank()) return@Button
                        val effectiveLogin = if (login.isBlank()) "root" else login
                        val effectiveDtlsPort = if (savedManualPorts) savedServerDtlsPort.coerceIn(1, 65535) else 56000
                        val effectiveWgPort = if (savedManualPorts) savedServerWgPort.coerceIn(1, 65535) else 56001
                        val appContext = context.applicationContext
                        
                        DeployManager.scope.launch {
                            try {
                                DeployManager.startDeploy()
                                
                                // ВРЕМЕННО ОТКЛЮЧЕНО: Вызов сервиса DEPLOY_START, 
                                // так как если в TunnelService.kt он не прописан, Android убивает приложение.
                                // try {
                                //     val intent = Intent(appContext, TunnelService::class.java).apply { action = "DEPLOY_START" }
                                //     if (Build.VERSION.SDK_INT >= 26) appContext.startForegroundService(intent)
                                //     else appContext.startService(intent)
                                // } catch (e: Exception) { e.printStackTrace() }

                                val success = performDeploy(
                                    context = appContext,
                                    host = ip, user = effectiveLogin, pass = password, port = savedSshPort.toIntOrNull() ?: 22,
                                    mainPass = savedMainPass, adminId = savedAdminId, botToken = savedBotToken,
                                    dtlsPort = effectiveDtlsPort, wgPort = effectiveWgPort,
                                    onProgress = { p, s -> DeployManager.updateProgress(p, s) }
                                )
                                
                                if (success) {
                                    successCountdown = 5
                                    showSuccessBanner = true
                                } else {
                                    // Выводим уведомление, если деплой упал внутри performDeploy
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, "Ошибка деплоя! Проверьте Логи.", Toast.LENGTH_LONG).show()
                                    }
                                }
                            } catch (e: Exception) {
                                DeployManager.writeError("Критическая ошибка: ${e.message}")
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "Краш: ${e.message}", Toast.LENGTH_LONG).show()
                                }
                                DeployManager.stopDeploy("Ошибка: ${e.message}")
                            } finally {
                                // try { appContext.startService(Intent(appContext, TunnelService::class.java).apply { action = "DEPLOY_STOP" }) } catch (_: Exception) {}
                            }
                        }
                    },
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = RoundedCornerShape(20.dp),
                    enabled = !isDeploying && ip.isNotBlank() && password.isNotBlank() && savedMainPass.isNotBlank()
                ) {
                    if (isDeploying) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.CloudUpload, null, Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(if (isDeploying) "Установка" else "Установить", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = {
                        if (ip.isBlank() || password.isBlank()) return@Button
                        showUninstallDialog = true
                    },
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    enabled = !isDeploying && ip.isNotBlank() && password.isNotBlank()
                ) {
                    Icon(Icons.Default.Delete, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Удалить", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // ═══ Success Banner ═══
        AnimatedVisibility(visible = showSuccessBanner, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Surface(
                shape = RoundedCornerShape(32.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp)
            ) {
                Row(modifier = Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(48.dp).background(Color(0xFF4CAF50).copy(alpha = 0.2f), CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF4CAF50))
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Успешно", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("Деплой успешно завершен ($successCountdown)", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        
        Spacer(Modifier.height(32.dp))
    }

    if (showSecretsDialog) {
        DeploySecretsDialog(
            settingsStore = settingsStore,
            initialMainPass = savedMainPass,
            initialAdminId = savedAdminId,
            initialBotToken = savedBotToken,
            initialSshPort = savedSshPort,
            manualPortsEnabled = savedManualPorts,
            initialServerDtlsPort = savedServerDtlsPort.toString(),
            initialServerWgPort = savedServerWgPort.toString(),
            onSaved = { _, _ -> },
            onDismiss = { showSecretsDialog = false }
        )
    }

    if (showUninstallDialog) {
        UninstallConfirmDialog(
            onDismiss = { showUninstallDialog = false },
            onConfirm = {
                showUninstallDialog = false
                val effectiveLogin = if (login.isBlank()) "root" else login
                val effectiveDtlsPort = if (savedManualPorts) savedServerDtlsPort.coerceIn(1, 65535) else 56000
                val effectiveWgPort = if (savedManualPorts) savedServerWgPort.coerceIn(1, 65535) else 56001
                DeployManager.scope.launch {
                    try {
                        DeployManager.startDeploy()
                        performUninstall(
                            host = ip, user = effectiveLogin, pass = password, port = savedSshPort.toIntOrNull() ?: 22,
                            dtlsPort = effectiveDtlsPort, wgPort = effectiveWgPort,
                            onProgress = { p, s -> DeployManager.updateProgress(p, s) }
                        )
                    } catch (_: Exception) {}
                }
            }
        )
    }
}

// ==================== Dialogs ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeploySecretsDialog(
    settingsStore: SettingsStore,
    initialMainPass: String,
    initialAdminId: String,
    initialBotToken: String,
    initialSshPort: String,
    manualPortsEnabled: Boolean,
    initialServerDtlsPort: String,
    initialServerWgPort: String,
    onSaved: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var passInput by rememberSaveable { mutableStateOf(initialMainPass) }
    var adminIdInput by rememberSaveable { mutableStateOf(initialAdminId) }
    var botTokenInput by rememberSaveable { mutableStateOf(initialBotToken) }
    var sshPortInput by rememberSaveable { mutableStateOf(if (initialSshPort.isBlank()) "22" else initialSshPort) }
    var dtlsPortInput by rememberSaveable { mutableStateOf(initialServerDtlsPort.ifBlank { "56000" }) }
    var wgPortInput by rememberSaveable { mutableStateOf(initialServerWgPort.ifBlank { "56001" }) }

    fun normalizePort(value: String, fallback: String): String {
        return value.toIntOrNull()?.takeIf { it in 1..65535 }?.toString() ?: fallback
    }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Column(modifier = Modifier.padding(28.dp).fillMaxWidth().verticalScroll(rememberScrollState())) {
                Text("Секреты", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(20.dp))

                OutlinedTextField(
                    value = passInput,
                    onValueChange = { passInput = it },
                    label = { Text("Пароль туннеля (любой)") },
                    placeholder = { Text("Задайте надежный пароль") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Text("Телеграм бот (Опционально)", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = adminIdInput,
                    onValueChange = { adminIdInput = it },
                    label = { Text("ID Админа") },
                    placeholder = { Text("ID из @getmyid_bot") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )

                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = botTokenInput,
                    onValueChange = { botTokenInput = it },
                    label = { Text("Токен Бота") },
                    placeholder = { Text("Токен от BotFather") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Text("Порты", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = sshPortInput,
                    onValueChange = { sshPortInput = it.filter(Char::isDigit).take(5) },
                    label = { Text("Порт SSH (для деплоя)") },
                    placeholder = { Text("22") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )

                if (manualPortsEnabled) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = dtlsPortInput,
                        onValueChange = { dtlsPortInput = it.filter(Char::isDigit).take(5) },
                        label = { Text("Порт DTLS сервера") },
                        placeholder = { Text("56000") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = wgPortInput,
                        onValueChange = { wgPortInput = it.filter(Char::isDigit).take(5) },
                        label = { Text("Порт WireGuard сервера") },
                        placeholder = { Text("56001") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }

                Spacer(Modifier.height(24.dp))
                
                Button(
                    onClick = {
                        val finalPort = if (sshPortInput.isBlank()) "22" else sshPortInput
                        val finalDtls = normalizePort(dtlsPortInput, "56000")
                        val finalWg = normalizePort(wgPortInput, "56001")
                        scope.launch {
                            settingsStore.saveDeploySecrets(passInput, adminIdInput, botTokenInput, finalPort)
                            settingsStore.savePorts(finalDtls.toInt(), finalWg.toInt(), settingsStore.listenPort.first())
                            onSaved(finalDtls, finalWg)
                            onDismiss()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(24.dp),
                    enabled = passInput.isNotBlank()
                ) { Text("Сохранить", fontWeight = FontWeight.Bold, fontSize = 16.sp) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UninstallConfirmDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    var confirmText by remember { mutableStateOf("") }
    val isConfirmed = confirmText.trim().lowercase() == "да"

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Column(modifier = Modifier.padding(28.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    "Удаление сервера",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
                Text(
                    "Будут удалены: бинарник, systemd-сервис, бот, конфигурация и правила firewall/NAT.\n\nЭто действие необратимо.",
                    style = MaterialTheme.typography.bodyMedium,
                    lineHeight = 24.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = confirmText,
                    onValueChange = { confirmText = it },
                    label = { Text("Введите «да» для подтверждения") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.error,
                    )
                )
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FilledTonalButton(
                        onClick = onDismiss, modifier = Modifier.weight(1f).height(56.dp),
                        shape = RoundedCornerShape(24.dp)
                    ) { Text("Отмена", fontWeight = FontWeight.Bold) }
                    
                    Button(
                        onClick = onConfirm, modifier = Modifier.weight(1f).height(56.dp),
                        shape = RoundedCornerShape(24.dp), enabled = isConfirmed,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        )
                    ) { Text("Удалить", fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

// ==================== Вспомогательные компоненты UI (TyrnGuard) ====================

@Composable
private fun CategoryCard(title: String, icon: ImageVector, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(26.dp),
        color = Color.Transparent,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.26f)),
        tonalElevation = 2.dp,
        shadowElevation = 3.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.surfaceContainerHigh,
                            MaterialTheme.colorScheme.surfaceContainer
                        )
                    )
                )
                .padding(22.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 20.dp)) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(38.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(20.dp))
                    }
                }
                Spacer(Modifier.width(12.dp))
                Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
            content()
        }
    }
}

@Composable
private fun SettingSwitchRow(icon: ImageVector, title: String, subtitle: String, checked: Boolean, enabled: Boolean = true, onCheckedChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().alpha(if(enabled) 1f else 0.5f).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(40.dp).background(MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape), contentAlignment = Alignment.Center) { Icon(icon, null, modifier = Modifier.size(20.dp)) }
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

// ==================== SSH Логика (WDTT) ====================

private class SSHClient(private val session: Session, private val pass: String) {

    fun exec(command: String, timeout: Long = CMD_TIMEOUT): String {
        if (!session.isConnected) {
            DeployManager.writeError("SSH exec: сессия разорвана перед командой: ${command.take(80)}")
            return "error: session is down"
        }

        var channel: ChannelExec? = null
        val result = StringBuilder()

        return try {
            channel = session.openChannel("exec") as ChannelExec
            val cmd = if (command.contains("sudo") && !command.contains("sudo -S")) {
                command.replace("sudo ", "sudo -S ")
            } else command

            channel.setCommand(cmd)
            val outStream = channel.outputStream
            val input = channel.inputStream
            val err = channel.errStream
            channel.connect(15000)

            if (cmd.contains("sudo -S")) {
                outStream.write("$pass\n".toByteArray())
                outStream.flush()
            }

            val reader = input.bufferedReader()
            val errReader = err.bufferedReader()
            val startTime = System.currentTimeMillis()
            val progressRegex = Regex("^WDTT_PROGRESS\\|(\\d+\\.?\\d*)\\|(.+)$")

            while (!channel.isClosed || reader.ready() || errReader.ready()) {
                if (System.currentTimeMillis() - startTime > timeout) {
                    DeployManager.writeError("SSH timeout (${timeout/1000}s): ${command.take(80)}")
                    try { channel.disconnect() } catch (_: Exception) {}
                    return "error: timeout"
                }

                if (reader.ready()) {
                    val line = reader.readLine()
                    if (line != null) {
                        val match = progressRegex.find(line.trim())
                        if (match != null) {
                            val p = match.groupValues[1].toFloatOrNull() ?: 0f
                            DeployManager.updateProgress(p, match.groupValues[2])
                        } else if (!line.contains("WDTT_PROGRESS")) {
                            val clean = line.replace(Regex("\u001B\\[[;\\d]*m"), "")
                            result.appendLine(clean)
                            if (clean.contains("[✗]") || clean.contains("FAIL") ||
                                (clean.contains("error", true) && !clean.contains("2>/dev/null"))) {
                                DeployManager.writeError("REMOTE: $clean")
                                TunnelManager.addDeployErrorLog("REMOTE: $clean")
                            }
                        }
                    }
                }
                if (errReader.ready()) {
                    val line = errReader.readLine()
                    if (line != null && !line.contains("password for")) {
                        val clean = line.replace(Regex("\u001B\\[[;\\d]*m"), "")
                        result.appendLine(clean)
                        if (clean.isNotBlank() && !clean.startsWith("Warning:")) {
                            DeployManager.writeError("STDERR: $clean")
                            TunnelManager.addDeployErrorLog("STDERR: $clean")
                        }
                    }
                }
                if (!reader.ready() && !errReader.ready()) Thread.sleep(100)
            }

            result.toString()
        } catch (e: Exception) {
            DeployManager.writeError("SSH exec error: ${e.message} | cmd: ${command.take(80)}")
            TunnelManager.addDeployErrorLog("SSH exec error: ${e.message}")
            "error: ${e.message}"
        } finally {
            try { channel?.disconnect() } catch (_: Exception) {}
        }
    }

    fun upload(localFile: File, remotePath: String) {
        if (!session.isConnected) {
            DeployManager.writeError("SSH upload: сессия разорвана")
            throw Exception("Session is down")
        }
        var sftp: ChannelSftp? = null
        try {
            sftp = session.openChannel("sftp") as ChannelSftp
            sftp.connect(15000)
            sftp.put(localFile.absolutePath, remotePath)
        } catch (e: Exception) {
            DeployManager.writeError("SFTP upload error: ${e.message} | file: ${localFile.name}")
            throw e
        } finally {
            try { sftp?.disconnect() } catch (_: Exception) {}
        }
    }
}

private fun createSSHSession(host: String, user: String, pass: String, port: Int = 22): Session {
    val jsch = JSch()
    val session = jsch.getSession(user, host, port)
    session.setPassword(pass)
    session.setConfig(Properties().apply {
        put("StrictHostKeyChecking", "no")
        put("ServerAliveInterval", "10")
        put("ServerAliveCountMax", "6")
        put("ConnectTimeout", "15000")
        put("PreferredAuthentications", "password,keyboard-interactive")
    })
    session.connect(20000)
    return session
}

private fun shellQuote(value: String): String {
    return "'" + value.replace("'", "'\"'\"'") + "'"
}

private fun rootCommand(command: String): String {
    val quoted = shellQuote(command)
    return "if command -v sudo >/dev/null 2>&1; then sudo bash -c $quoted; " +
        "elif [ \"\$(id -u)\" = \"0\" ]; then bash -c $quoted; " +
        "else echo 'error: root privileges required and sudo not found'; exit 1; fi"
}

private fun File.containsBinaryToken(token: String): Boolean {
    val data = readBytes()
    val needle = token.toByteArray()
    if (needle.isEmpty() || data.size < needle.size) return false
    for (i in 0..data.size - needle.size) {
        var matched = true
        for (j in needle.indices) {
            if (data[i + j] != needle[j]) {
                matched = false
                break
            }
        }
        if (matched) return true
    }
    return false
}

private fun isUnsafeLegacyServerAsset(serverFile: File): Boolean {
    return serverFile.containsBinaryToken("/etc/wireguard") ||
        (serverFile.containsBinaryToken("wg0") && !serverFile.containsBinaryToken("wdtt0"))
}

// ==================== Деплой ====================

private suspend fun performDeploy(
    context: Context,
    host: String, user: String, pass: String, port: Int,
    mainPass: String, adminId: String, botToken: String,
    dtlsPort: Int, wgPort: Int,
    onProgress: (Float, String) -> Unit
): Boolean = withContext(Dispatchers.IO) {
    var session: Session? = null
    try {
        onProgress(0.02f, "Подключение по SSH...")
        session = createSSHSession(host, user, pass, port)
        DeployManager.activeSession = session
        val ssh = SSHClient(session, pass)

        onProgress(0.05f, "Подготовка файлов...")
        val passArg = if (mainPass.isNotBlank()) "-password \"$mainPass\" " else ""
        val adminArg = if (adminId.isNotBlank()) "-admin \"$adminId\" " else ""
        val botArg = if (botToken.isNotBlank()) "-bot-token \"$botToken\" " else ""
        val args = "$passArg$adminArg$botArg".trim()

        val scriptFile = File(context.cacheDir, "deploy.sh")
        val serverFile = File(context.cacheDir, "server")
        try {
            context.assets.open("deploy.sh").use { inp -> FileOutputStream(scriptFile).use { out -> inp.copyTo(out) } }
            context.assets.open("server").use { inp -> FileOutputStream(serverFile).use { out -> inp.copyTo(out) } }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) { Toast.makeText(context, "ОШИБКА: Файлы deploy.sh или server не найдены в папке assets!", Toast.LENGTH_LONG).show() }
            DeployManager.stopDeploy("Файлы не найдены в assets")
            return@withContext false
        }

        onProgress(0.06f, "Загрузка на сервер...")
        ssh.upload(scriptFile, "/tmp/deploy.sh")
        ssh.upload(serverFile, "/tmp/wdtt-server")
        scriptFile.delete()
        serverFile.delete()

        onProgress(0.08f, "Установка...")
        val output = ssh.exec(
            rootCommand("env WDTT_ARGS=${shellQuote(args)} WDTT_DTLS_PORT=$dtlsPort WDTT_WG_PORT=$wgPort bash /tmp/deploy.sh"),
            timeout = CMD_TIMEOUT
        )

        if (output.contains("✅") || output.contains("Деплой успешно") || output.contains("active")) {
            DeployManager.stopDeploy("success")
            return@withContext true
        } else if (output.contains("error:")) {
            withContext(Dispatchers.Main) { Toast.makeText(context, "Ошибка скрипта на сервере. Ответ: ${output.take(50)}", Toast.LENGTH_LONG).show() }
            DeployManager.stopDeploy("Ошибка выполнения скрипта")
            return@withContext false
        } else {
            DeployManager.stopDeploy("success")
            return@withContext true
        }

    } catch (e: Exception) {
        // ВОТ ЗДЕСЬ ПРИЛОЖЕНИЕ ПОКАЖЕТ ТОЧНУЮ ОШИБКУ ПОДКЛЮЧЕНИЯ
        val errorMsg = e.message ?: "Неизвестная ошибка"
        withContext(Dispatchers.Main) { 
            Toast.makeText(context, "СБОЙ SSH: $errorMsg", Toast.LENGTH_LONG).show() 
        }
        DeployManager.stopDeploy("Ошибка: ${errorMsg.take(50)}")
        return@withContext false
    } finally {
        try { session?.disconnect() } catch (_: Exception) {}
        DeployManager.activeSession = null
    }
}

private suspend fun performUninstall(
    host: String, user: String, pass: String, port: Int,
    dtlsPort: Int, wgPort: Int,
    onProgress: (Float, String) -> Unit
) = withContext(Dispatchers.IO) {
    var session: Session? = null
    try {
        onProgress(0.05f, "Подключение...")
        session = createSSHSession(host, user, pass, port)
        DeployManager.activeSession = session
        val ssh = SSHClient(session, pass)

        onProgress(0.15f, "Остановка сервиса...")
        ssh.exec(
            rootCommand(
                "systemctl unmask wdtt 2>/dev/null || true; " +
                    "systemctl stop wdtt 2>/dev/null || true; " +
                    "systemctl disable wdtt 2>/dev/null || true; " +
                    "rm -f /etc/systemd/system/wdtt.service; " +
                    "systemctl daemon-reload 2>/dev/null || true"
            ),
            timeout = 15000L
        )

        onProgress(0.30f, "Удаление через deploy.sh...")
        ssh.exec(rootCommand("[ -f /tmp/deploy.sh ] && env WDTT_DTLS_PORT=$dtlsPort WDTT_WG_PORT=$wgPort bash /tmp/deploy.sh uninstall 2>/dev/null || true"), timeout = 30000L)

        onProgress(0.45f, "Удаление бинарника...")
        ssh.exec(rootCommand("pkill -x wdtt-server 2>/dev/null || true; rm -f /usr/local/bin/wdtt-server"), timeout = 10000L)

        onProgress(0.60f, "Очистка firewall...")
        ssh.exec(
            rootCommand(
                "if command -v iptables >/dev/null 2>&1; then " +
                    "for i in 1 2 3 4 5; do " +
                    "for iface in $(ls /sys/class/net 2>/dev/null || true); do " +
                    "iptables -t nat -D POSTROUTING -s 10.66.66.0/24 -o \"${'$'}iface\" -m comment --comment WDTT_MANAGED -j MASQUERADE 2>/dev/null || true; " +
                    "done; " +
                    "iptables -D INPUT -p udp --dport $dtlsPort -m comment --comment WDTT_MANAGED -j ACCEPT 2>/dev/null || true; " +
                    "iptables -D INPUT -p udp --dport $wgPort -m comment --comment WDTT_MANAGED -j ACCEPT 2>/dev/null || true; " +
                    "iptables -D INPUT -p udp --dport 56000 -m comment --comment WDTT_MANAGED -j ACCEPT 2>/dev/null || true; " +
                    "iptables -D INPUT -p udp --dport 56001 -m comment --comment WDTT_MANAGED -j ACCEPT 2>/dev/null || true; " +
                    "iptables -D FORWARD -i wdtt0 -m comment --comment WDTT_MANAGED -j ACCEPT 2>/dev/null || true; " +
                    "iptables -D FORWARD -o wdtt0 -m comment --comment WDTT_MANAGED -j ACCEPT 2>/dev/null || true; " +
                    "done; fi; " +
                    "if command -v nft >/dev/null 2>&1; then " +
                    "nft delete table ip wdtt 2>/dev/null || true; " +
                    "nft delete table inet wdtt 2>/dev/null || true; " +
                    "nft delete table inet wdtt_mangle 2>/dev/null || true; " +
                    "fi"
            ),
            timeout = 15000L
        )

        onProgress(0.75f, "Удаление WDTT-интерфейса...")
        ssh.exec(
            rootCommand(
                "ip link show wdtt0 >/dev/null 2>&1 && ip link del wdtt0 2>/dev/null || true; " +
                    "rm -rf /etc/wdtt"
            ),
            timeout = 10000L
        )

        onProgress(0.90f, "Очистка sysctl...")
        ssh.exec(rootCommand("rm -f /etc/sysctl.d/99-wdtt.conf; sysctl --system >/dev/null 2>&1 || true"), timeout = 15000L)

        onProgress(1.0f, "Готово!")
        DeployManager.stopDeploy("success")

    } catch (e: Exception) {
        DeployManager.writeError("Uninstall error: ${e.message}")
        DeployManager.stopDeploy("Ошибка: ${e.message?.take(100)}")
    } finally {
        try { session?.disconnect() } catch (_: Exception) {}
        DeployManager.activeSession = null
    }
}
