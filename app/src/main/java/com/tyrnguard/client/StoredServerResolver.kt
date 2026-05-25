package com.tyrnguard.client

import org.json.JSONArray

internal data class StoredServerConfig(
    val host: String,
    val password: String,
    val dtlsPort: Int,
)

internal fun normalizeStoredServerHost(value: String): String {
    val trimmed = value.trim()
    if (trimmed.isBlank()) return ""
    return trimmed
        .removePrefix("udp://")
        .removePrefix("tcp://")
        .removePrefix("turn://")
        .substringBefore("/")
        .let { host ->
            if (host.count { it == ':' } == 1) host.substringBefore(":") else host
        }
        .trim()
}

internal fun findStoredServerConfig(peer: String, serversJson: String): StoredServerConfig? {
    val peerHost = normalizeStoredServerHost(peer)
    if (peerHost.isBlank()) return null
    return try {
        val array = JSONArray(serversJson)
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val host = normalizeStoredServerHost(obj.optString("ip"))
            if (host == peerHost) {
                return StoredServerConfig(
                    host = host,
                    password = obj.optString("password").trim(),
                    dtlsPort = obj.optInt("dtlsPort", 56000).coerceIn(1, 65535),
                )
            }
        }
        null
    } catch (_: Exception) {
        null
    }
}

internal fun buildPeerWithSavedServerPort(peer: String, serversJson: String): String {
    val trimmed = peer.trim()
    if (trimmed.isBlank() || trimmed.contains(":")) return trimmed
    val dtlsPort = findStoredServerConfig(trimmed, serversJson)?.dtlsPort ?: 56000
    return "$trimmed:$dtlsPort"
}

internal fun resolveSavedServerPassword(
    peer: String,
    serversJson: String,
    fallbackPassword: String,
): String {
    val serverPassword = findStoredServerConfig(peer, serversJson)?.password.orEmpty()
    return serverPassword.ifBlank { fallbackPassword.trim() }
}
