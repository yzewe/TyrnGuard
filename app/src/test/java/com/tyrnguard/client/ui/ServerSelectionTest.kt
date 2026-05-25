package com.tyrnguard.client.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ServerSelectionTest {
    @Test
    fun selectedServerPasswordWinsOverStoredConnectionPassword() {
        val activeServer = TyrnGuardServer(
            name = "Estonia",
            ip = "94.183.170.27",
            password = "fresh-server-password",
        )

        val password = resolveConnectionPassword(
            activeServer = activeServer,
            storedConnectionPassword = "stale-password",
        )

        assertEquals("fresh-server-password", password)
    }

    @Test
    fun selectedServerMatchesPeerWithPort() {
        val servers = listOf(
            TyrnGuardServer(name = "Other", ip = "62.60.253.74", password = "wrong"),
            TyrnGuardServer(name = "Estonia", ip = "94.183.170.27", password = "right"),
        )

        val activeServer = resolveActiveServer(
            servers = servers,
            peer = "94.183.170.27:56000",
        )

        assertEquals("Estonia", activeServer?.name)
    }
}
