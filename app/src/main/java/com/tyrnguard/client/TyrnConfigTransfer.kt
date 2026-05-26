package com.tyrnguard.client

import android.util.Base64
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

object TyrnConfigTransfer {
    const val LINK_PREFIX = "tyrnguard://config?data="

    const val KIND_ALL = "all"
    const val KIND_SERVERS = "servers"
    const val KIND_ACTIVE_SERVER = "active_server"
    const val KIND_NETWORK = "network"
    const val KIND_PERFORMANCE = "performance"
    const val KIND_INTERFACE = "interface"

    private const val SCHEMA = "tyrnguard.config.v2"
    private const val SECTION_SERVERS = "servers"
    private const val SECTION_NETWORK = "network"
    private const val SECTION_PERFORMANCE = "performance"
    private const val SECTION_INTERFACE = "interface"
    private const val SECTION_EXCEPTIONS = "exceptions"

    data class ImportResult(val appliedSections: List<String>, val serverCount: Int = 0) {
        val message: String
            get() = buildString {
                append("Импортировано: ")
                if (appliedSections.isEmpty()) append("ничего")
                else append(appliedSections.joinToString(", "))
                if (serverCount > 0) append(" ($serverCount серверов)")
            }
    }

    suspend fun buildExportLink(store: SettingsStore, kind: String): String {
        val json = buildExportJson(store, kind)
        val encoded = Base64.encodeToString(
            json.toString().toByteArray(Charsets.UTF_8),
            Base64.URL_SAFE or Base64.NO_WRAP
        )
        return "$LINK_PREFIX$encoded"
    }

    suspend fun importFromText(store: SettingsStore, text: String): ImportResult {
        val json = decodeConfigJson(text)
        return importJson(store, json)
    }

    suspend fun importJson(store: SettingsStore, json: JSONObject): ImportResult {
        if (json.optString("schema") == SCHEMA || json.has("payload")) {
            return importV2(store, json)
        }
        if (json.has("ip")) {
            val incoming = JSONArray().put(json)
            val merged = mergeServers(safeArray(store.savedServersJson.first()), incoming)
            store.saveServersList(merged.servers.toString())
            val ip = json.optString("ip").trim()
            if (ip.isNotBlank()) {
                store.save(
                    peer = ip,
                    vkHashes = store.vkHashes.first(),
                    secondaryVkHash = "",
                    workersPerHash = store.workersPerHash.first(),
                    protocol = store.protocol.first(),
                    listenPort = store.listenPort.first(),
                    sni = store.sni.first()
                )
                json.optString("password").trim().takeIf { it.isNotBlank() }?.let {
                    store.saveConnectionPassword(it)
                }
            }
            return ImportResult(listOf("серверы"), merged.changedCount)
        }
        if (json.has("peer") || json.has("hashes")) {
            val captchaMethod = json.optString("captchaSolveMethod", store.captchaSolveMethod.first())
            store.save(
                peer = json.optString("peer", store.peer.first()).trim(),
                vkHashes = json.optString("hashes", store.vkHashes.first()).trim(),
                secondaryVkHash = "",
                workersPerHash = json.optInt("workers", store.workersPerHash.first()).coerceIn(1, 512),
                protocol = if (json.optBoolean("tcp", false)) "tcp" else store.protocol.first(),
                listenPort = json.optInt("listenPort", store.listenPort.first()).coerceIn(1, 65535),
                sni = json.optString("sni", store.sni.first()).trim()
            )
            store.saveConnectionPassword(json.optString("password", store.connectionPassword.first()).trim())
            store.saveCaptchaMode(if (captchaMethod == "auto") "rjs" else "wv")
            store.saveCaptchaSolveMethod(captchaMethod)
            return ImportResult(listOf("подключение", "производительность"))
        }
        return ImportResult(emptyList())
    }

    private suspend fun buildExportJson(store: SettingsStore, kind: String): JSONObject {
        val sections = when (kind) {
            KIND_SERVERS -> listOf(SECTION_SERVERS)
            KIND_ACTIVE_SERVER -> listOf(SECTION_SERVERS)
            KIND_NETWORK -> listOf(SECTION_NETWORK)
            KIND_PERFORMANCE -> listOf(SECTION_PERFORMANCE)
            KIND_INTERFACE -> listOf(SECTION_INTERFACE)
            else -> listOf(SECTION_SERVERS, SECTION_NETWORK, SECTION_PERFORMANCE, SECTION_INTERFACE, SECTION_EXCEPTIONS)
        }

        val payload = JSONObject()
        if (SECTION_SERVERS in sections) {
            payload.put(SECTION_SERVERS, buildServersPayload(store, activeOnly = kind == KIND_ACTIVE_SERVER))
        }
        if (SECTION_NETWORK in sections) {
            payload.put(
                SECTION_NETWORK,
                JSONObject()
                    .put("protocol", store.protocol.first())
                    .put("listenPort", store.listenPort.first())
                    .put("sni", store.sni.first())
                    .put("manualPortsEnabled", store.manualPortsEnabled.first())
                    .put("serverDtlsPort", store.serverDtlsPort.first())
                    .put("serverWgPort", store.serverWgPort.first())
                    .put("customMtu", store.customMtu.first())
                    .put("customDns", store.customDns.first())
                    .put("customDnsIp", store.customDnsIp.first())
            )
        }
        if (SECTION_PERFORMANCE in sections) {
            payload.put(
                SECTION_PERFORMANCE,
                JSONObject()
                    .put("vkHashes", store.vkHashes.first())
                    .put("workersPerHash", store.workersPerHash.first())
                    .put("captchaMode", store.captchaMode.first())
                    .put("captchaSolveMethod", store.captchaSolveMethod.first())
                    .put("captchaWbvSolveMethod", store.captchaWbvSolveMethod.first())
            )
        }
        if (SECTION_INTERFACE in sections) {
            payload.put(
                SECTION_INTERFACE,
                JSONObject()
                    .put("themeMode", store.themeMode.first())
                    .put("dynamicColor", store.useDynamicColor.first())
                    .put("themePalette", store.themePalette.first())
            )
        }
        if (SECTION_EXCEPTIONS in sections) {
            payload.put(
                SECTION_EXCEPTIONS,
                JSONObject()
                    .put("excludedApps", store.excludedApps.first())
                    .put("isWhitelist", store.isWhitelist.first())
            )
        }

        return JSONObject()
            .put("schema", SCHEMA)
            .put("version", 2)
            .put("app", "TyrnGuard")
            .put("appVersion", BuildConfig.VERSION_NAME)
            .put("kind", kind)
            .put("createdAt", System.currentTimeMillis())
            .put("sections", JSONArray(sections))
            .put("payload", payload)
    }

    private suspend fun buildServersPayload(store: SettingsStore, activeOnly: Boolean): JSONObject {
        val activePeer = store.peer.first()
        val source = safeArray(store.savedServersJson.first())
        val items = JSONArray()
        for (i in 0 until source.length()) {
            val server = normalizeServer(source.optJSONObject(i) ?: continue) ?: continue
            if (!activeOnly || server.optString("ip") == activePeer) {
                items.put(server)
            }
        }
        if (activeOnly && items.length() == 0 && activePeer.isNotBlank()) {
            items.put(
                JSONObject()
                    .put("id", UUID.randomUUID().toString())
                    .put("name", activePeer)
                    .put("ip", activePeer)
                    .put("password", store.connectionPassword.first())
                    .put("dtlsPort", store.serverDtlsPort.first())
                    .put("wgPort", store.serverWgPort.first())
            )
        }
        return JSONObject()
            .put("items", items)
            .put("activePeer", activePeer)
            .put("connectionPassword", store.connectionPassword.first())
    }

    private suspend fun importV2(store: SettingsStore, json: JSONObject): ImportResult {
        val payload = json.optJSONObject("payload") ?: json
        val applied = mutableListOf<String>()
        var serverCount = 0

        var peer = store.peer.first()
        var hashes = store.vkHashes.first()
        var workers = store.workersPerHash.first()
        var protocol = store.protocol.first()
        var listenPort = store.listenPort.first()
        var sni = store.sni.first()
        var shouldSaveConnection = false

        payload.optJSONObject(SECTION_SERVERS)?.let { serversPayload ->
            val incoming = serversPayload.optJSONArray("items") ?: JSONArray()
            val merged = mergeServers(safeArray(store.savedServersJson.first()), incoming)
            if (merged.changedCount > 0) {
                store.saveServersList(merged.servers.toString())
                serverCount = merged.changedCount
            }
            val activePeer = serversPayload.optString("activePeer").trim()
            if (activePeer.isNotBlank()) peer = activePeer
            val connectionPassword = serversPayload.optString("connectionPassword").trim()
            if (connectionPassword.isNotBlank()) {
                store.saveConnectionPassword(connectionPassword)
                shouldSaveConnection = true
            }
            applied += "серверы"
        }

        payload.optJSONObject(SECTION_NETWORK)?.let { network ->
            protocol = network.optString("protocol", protocol).takeIf { it == "tcp" || it == "udp" } ?: protocol
            listenPort = network.optInt("listenPort", listenPort).coerceIn(1, 65535)
            sni = network.optString("sni", sni).trim()
            store.saveManualPortsEnabled(network.optBoolean("manualPortsEnabled", store.manualPortsEnabled.first()))
            store.savePorts(
                serverDtlsPort = network.optInt("serverDtlsPort", store.serverDtlsPort.first()).coerceIn(1, 65535),
                serverWgPort = network.optInt("serverWgPort", store.serverWgPort.first()).coerceIn(1, 65535),
                listenPort = listenPort
            )
            store.saveCustomMtu(network.optInt("customMtu", store.customMtu.first()).coerceIn(0, 1500))
            store.saveCustomDns(network.optString("customDns", store.customDns.first()))
            store.saveCustomDnsIp(network.optString("customDnsIp", store.customDnsIp.first()).trim())
            applied += "сеть"
        }

        payload.optJSONObject(SECTION_PERFORMANCE)?.let { performance ->
            hashes = performance.optString("vkHashes", hashes).trim()
            workers = performance.optInt("workersPerHash", workers).coerceIn(1, 512)
            val captchaMode = performance.optString("captchaMode", store.captchaMode.first())
            val captchaMethod = performance.optString("captchaSolveMethod", store.captchaSolveMethod.first())
            val wbvMethod = performance.optString("captchaWbvSolveMethod", store.captchaWbvSolveMethod.first())
            store.saveCaptchaMode(captchaMode)
            store.saveCaptchaSolveMethod(captchaMethod)
            store.saveWbvCaptchaSolveMethod(wbvMethod)
            applied += "производительность"
        }

        payload.optJSONObject(SECTION_INTERFACE)?.let { ui ->
            store.saveThemeMode(ui.optString("themeMode", store.themeMode.first()))
            store.saveDynamicColor(ui.optBoolean("dynamicColor", store.useDynamicColor.first()))
            store.saveThemePalette(ui.optString("themePalette", store.themePalette.first()))
            applied += "интерфейс"
        }

        payload.optJSONObject(SECTION_EXCEPTIONS)?.let { exceptions ->
            store.saveExceptionsMode(
                packages = exceptions.optString("excludedApps", store.excludedApps.first()),
                isWhitelist = exceptions.optBoolean("isWhitelist", store.isWhitelist.first())
            )
            applied += "исключения"
        }

        if (applied.any { it in listOf("серверы", "сеть", "производительность") } || shouldSaveConnection) {
            store.save(
                peer = peer,
                vkHashes = hashes,
                secondaryVkHash = "",
                workersPerHash = workers,
                protocol = protocol,
                listenPort = listenPort,
                sni = sni
            )
        }

        return ImportResult(applied.distinct(), serverCount)
    }

    private fun decodeConfigJson(text: String): JSONObject {
        val trimmed = text.trim()
        val linkIndex = trimmed.indexOf(LINK_PREFIX)
        if (linkIndex >= 0) {
            val start = linkIndex + LINK_PREFIX.length
            val encoded = trimmed.substring(start)
                .trim()
                .takeWhile { !it.isWhitespace() && it != '"' && it != '\'' && it != '<' && it != '>' }
                .trimEnd('&')
            val decoded = Base64.decode(encoded, Base64.URL_SAFE or Base64.NO_WRAP)
            return JSONObject(String(decoded, Charsets.UTF_8))
        }
        val jsonStart = trimmed.indexOf('{')
        val jsonEnd = trimmed.lastIndexOf('}')
        if (jsonStart >= 0 && jsonEnd > jsonStart) {
            return JSONObject(trimmed.substring(jsonStart, jsonEnd + 1))
        }
        throw IllegalArgumentException("Config link or JSON not found")
    }

    private fun mergeServers(existing: JSONArray, incoming: JSONArray): MergeResult {
        val output = JSONArray()
        val indexById = mutableMapOf<String, Int>()
        val indexByIp = mutableMapOf<String, Int>()

        fun putServer(server: JSONObject) {
            val index = output.length()
            output.put(server)
            server.optString("id").takeIf { it.isNotBlank() }?.let { indexById[it] = index }
            server.optString("ip").takeIf { it.isNotBlank() }?.let { indexByIp[it] = index }
        }

        for (i in 0 until existing.length()) {
            normalizeServer(existing.optJSONObject(i) ?: continue)?.let(::putServer)
        }

        var changed = 0
        for (i in 0 until incoming.length()) {
            val server = normalizeServer(incoming.optJSONObject(i) ?: continue) ?: continue
            val id = server.optString("id")
            val ip = server.optString("ip")
            val targetIndex = indexById[id] ?: indexByIp[ip]
            if (targetIndex != null) {
                output.put(targetIndex, server)
            } else {
                putServer(server)
            }
            changed++
        }
        return MergeResult(output, changed)
    }

    private fun normalizeServer(source: JSONObject): JSONObject? {
        val ip = source.optString("ip").trim()
        if (ip.isBlank()) return null
        return JSONObject()
            .put("id", source.optString("id").ifBlank { UUID.randomUUID().toString() })
            .put("name", source.optString("name").ifBlank { ip })
            .put("ip", ip)
            .put("password", source.optString("password").trim())
            .put("dtlsPort", source.optInt("dtlsPort", 56000).coerceIn(1, 65535))
            .put("wgPort", source.optInt("wgPort", 56001).coerceIn(1, 65535))
    }

    private fun safeArray(json: String): JSONArray {
        return runCatching { JSONArray(json.ifBlank { "[]" }) }.getOrElse { JSONArray() }
    }

    private data class MergeResult(val servers: JSONArray, val changedCount: Int)
}
