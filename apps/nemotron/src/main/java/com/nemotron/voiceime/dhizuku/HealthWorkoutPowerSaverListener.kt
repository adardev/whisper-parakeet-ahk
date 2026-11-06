package com.nemotron.voiceime.dhizuku

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.service.notification.NotificationListenerService.Ranking
import android.util.Log
import com.nemotron.voiceime.guard.DndKeepAliveService
import java.util.concurrent.Executors

/** Watches Samsung Health's dedicated ongoing workout notification channel. */
class HealthWorkoutPowerSaverListener : NotificationListenerService() {
    private val worker = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "HealthWorkoutPowerSaver").apply { isDaemon = true }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        worker.execute {
            if (!DndKeepAliveService.isFit3Connected(this)) {
                HealthWorkoutPowerSaver.syncForFit3State(this, fit3Connected = false)
                return@execute
            }
            val live = activeNotifications.orEmpty().mapNotNull { sbn ->
                if (isWorkoutNotification(sbn) &&
                    !HealthWorkoutPowerSaver.isWorkoutFinished(sbn.notification)) sbn.key else null
            }.toSet()
            Log.i(TAG, "Listener conectado; entrenamientos activos=${live.size}")
            HealthWorkoutPowerSaver.reconcile(this, live)
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification, rankingMap: RankingMap) {
        // Android delivers all apps' notifications to this listener. Filter
        // before ranking lookup, extras access, preferences or Bluetooth IPC.
        if (sbn.packageName != HEALTH_PACKAGE || !sbn.isOngoing) return
        val ranking = Ranking()
        val channelName = if (rankingMap.getRanking(sbn.key, ranking)) ranking.channel?.name else null
        val workoutTitle = sbn.notification.extras
            ?.getCharSequence(android.app.Notification.EXTRA_TITLE)
        if (!HealthWorkoutPowerSaver.isWorkoutNotification(
                this, sbn.packageName, channelName, workoutTitle, sbn.isOngoing
            )) return
        worker.execute {
            if (HealthWorkoutPowerSaver.isWorkoutFinished(sbn.notification)) {
                Log.i(TAG, "Samsung Health terminó el entrenamiento: ${sbn.key}")
                HealthWorkoutPowerSaver.notificationRemoved(this, sbn.key)
            } else {
                HealthWorkoutPowerSaver.notificationPosted(this, sbn.key)
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification, rankingMap: RankingMap) {
        if (sbn.packageName != HEALTH_PACKAGE) return
        if (!HealthWorkoutPowerSaver.isTrackedNotification(this, sbn.key)) return
        worker.execute {
            HealthWorkoutPowerSaver.notificationRemoved(this, sbn.key)
        }
    }

    private fun isWorkoutNotification(sbn: StatusBarNotification): Boolean {
        if (sbn.packageName != HEALTH_PACKAGE || !sbn.isOngoing) return false
        val ranking = Ranking()
        if (!currentRanking.getRanking(sbn.key, ranking)) return false
        val workoutTitle = sbn.notification.extras
            ?.getCharSequence(android.app.Notification.EXTRA_TITLE)
        return sbn.isOngoing && HealthWorkoutPowerSaver.isWorkoutNotification(
            this, sbn.packageName, ranking.channel?.name, workoutTitle, sbn.isOngoing
        )
    }

    override fun onDestroy() {
        worker.shutdown()
        super.onDestroy()
    }

    companion object {
        private const val HEALTH_PACKAGE = "com.sec.android.app.shealth"
        private const val TAG = "HealthWorkoutListener"
    }
}
