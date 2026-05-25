package com.tyrnguard.client.ui

import com.tyrnguard.client.normalizeStoredServerHost

internal fun normalizeServerHost(value: String): String {
    return normalizeStoredServerHost(value)
}

internal fun resolveActiveServer(
    servers: List<TyrnGuardServer>,
    peer: String,
): TyrnGuardServer? {
    val activeHost = normalizeServerHost(peer)
    return servers.firstOrNull { normalizeServerHost(it.ip) == activeHost }
}

internal fun resolveConnectionPassword(
    activeServer: TyrnGuardServer?,
    storedConnectionPassword: String,
): String {
    val serverPassword = activeServer?.password?.trim().orEmpty()
    return serverPassword.ifBlank { storedConnectionPassword.trim() }
}
