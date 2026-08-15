package com.nemotron.voiceime.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.nemotron.voiceime.R
import com.nemotron.voiceime.dhizuku.ShizukuManager

/**
 * Widget de atajo: abre la app indicada aunque este congelada/oculta.
 * Un widget por app (targetActivity configurable via extra).
 */
class AppShortcutWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (id in appWidgetIds) {
            val pkg = appWidgetManager.getAppWidgetOptions(id)
                .getString(OPTION_PACKAGE) ?: DEFAULT_PACKAGE
            val activity = appWidgetManager.getAppWidgetOptions(id)
                .getString(OPTION_ACTIVITY) ?: DEFAULT_ACTIVITY
            val label = appWidgetManager.getAppWidgetOptions(id)
                .getString(OPTION_LABEL) ?: DEFAULT_LABEL

            val views = RemoteViews(context.packageName, R.layout.widget_shortcut)
            val appInfo = try { context.packageManager.getApplicationInfo(pkg, 0) } catch (_: Throwable) { null }
            if (appInfo != null) {
                val icon = android.graphics.drawable.Icon.createWithResource(pkg, appInfo.icon)
                views.setImageViewIcon(R.id.widgetIcon, icon)
            } else {
                views.setImageViewResource(R.id.widgetIcon, R.drawable.ic_launcher_foreground)
            }

            val openIntent = Intent(context, AppShortcutWidget::class.java).apply {
                action = ACTION_OPEN_APP
                putExtra(EXTRA_PACKAGE, pkg)
                putExtra(EXTRA_ACTIVITY, activity)
            }
            val pi = PendingIntent.getBroadcast(
                context,
                id,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widgetRoot, pi)

            appWidgetManager.updateAppWidget(id, views)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_OPEN_APP) {
            val pkg = intent.getStringExtra(EXTRA_PACKAGE) ?: DEFAULT_PACKAGE
            val activity = intent.getStringExtra(EXTRA_ACTIVITY) ?: DEFAULT_ACTIVITY
            if (ShizukuManager.hasPermission()) {
                Thread {
                    ShizukuManager.launchApp(pkg, activity)
                }.start()
            } else {
                try {
                    val launcher = context.packageManager.getLaunchIntentForPackage(pkg)
                    if (launcher != null) {
                        launcher.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(launcher)
                    }
                } catch (_: Throwable) {}
            }
        }
    }

    companion object {
        const val ACTION_OPEN_APP = "com.nemotron.voiceime.widget.OPEN_APP"
        const val EXTRA_PACKAGE = "pkg"
        const val EXTRA_ACTIVITY = "activity"
        const val OPTION_PACKAGE = "option_pkg"
        const val OPTION_ACTIVITY = "option_activity"
        const val OPTION_LABEL = "option_label"

        const val DEFAULT_PACKAGE = "com.ceti.escolomos"
        const val DEFAULT_ACTIVITY = "com.ceti.escolomos.MainActivity"
        const val DEFAULT_LABEL = "Escolomos"

        /** Si hay algun widget de Escolomos en el home. */
        fun hasAny(context: Context): Boolean {
            val am = AppWidgetManager.getInstance(context)
            val ids = am.getAppWidgetIds(ComponentName(context, AppShortcutWidget::class.java))
            return ids.isNotEmpty()
        }
    }
}
