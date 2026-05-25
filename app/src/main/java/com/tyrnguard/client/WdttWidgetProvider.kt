package com.tyrnguard.client

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class WdttWidgetProvider : AppWidgetProvider() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (id in appWidgetIds) {
            updateWidget(context, appWidgetManager, id)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == "TOGGLE_VPN") {
            handleToggle(context)
        }
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, WdttWidgetProvider::class.java))
        for (id in ids) {
            updateWidget(context, manager, id)
        }
    }

    private fun handleToggle(context: Context) {
        if (TunnelManager.running.value) {
            context.startService(Intent(context, TunnelService::class.java).apply { action = "STOP" })
        } else {
            scope.launch {
                val store = SettingsStore(context)
                val peer = store.peer.first()
                val hashes = store.vkHashes.first()
                val serversJson = store.savedServersJson.first()
                if (peer.isNotBlank() && hashes.isNotBlank()) {
                    val intent = Intent(context, TunnelService::class.java).apply {
                        action = "START"
                        putExtra("peer", buildPeerWithSavedServerPort(peer, serversJson))
                        putExtra("vk_hashes", hashes)
                        putExtra("secondary_vk_hash", store.secondaryVkHash.first())
                        putExtra("workers_per_hash", store.workersPerHash.first())
                        putExtra("port", store.listenPort.first())
                        putExtra("sni", store.sni.first())
                        putExtra("protocol", store.protocol.first())
                        putExtra("captcha_mode", store.captchaMode.first())
                        putExtra("captcha_solve_method", store.captchaSolveMethod.first())
                        putExtra("connection_password", resolveSavedServerPassword(peer, serversJson, store.connectionPassword.first()))
                    }
                    if (Build.VERSION.SDK_INT >= 26) {
                        context.startForegroundService(intent)
                    } else {
                        context.startService(intent)
                    }
                }
            }
        }
    }

    private fun updateWidget(context: Context, manager: AppWidgetManager, id: Int) {
        val views = RemoteViews(context.packageName, R.layout.wdtt_widget)
        val isRunning = TunnelManager.running.value

        if (isRunning) {
            views.setInt(R.id.widget_button, "setBackgroundResource", R.drawable.widget_bg_active)
            views.setImageViewResource(R.id.widget_button, R.drawable.ic_widget_shield)
        } else {
            views.setInt(R.id.widget_button, "setBackgroundResource", R.drawable.widget_bg_inactive)
            views.setImageViewResource(R.id.widget_button, R.drawable.ic_widget_lock)
        }

        val intent = Intent(context, WdttWidgetProvider::class.java).apply { action = "TOGGLE_VPN" }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_button, pendingIntent)

        manager.updateAppWidget(id, views)
    }
}
