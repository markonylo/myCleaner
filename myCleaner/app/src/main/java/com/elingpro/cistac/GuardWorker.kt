package com.elingpro.cistac

import android.app.ActivityManager
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Periodični radnik koji svakih 15 minuta proverava koje "watched" aplikacije
 * nisu korišćene duže od 10 minuta i gasi im pozadinske procese.
 */
class GuardWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    companion object {
        const val PREFS = "guard_prefs"
        const val KEY_ENABLED = "enabled"
        const val KEY_WATCHED = "watched_pkgs"
        const val KEY_LAST_LOG = "last_log"
        const val TAG = "battery_guard"

        fun enable(ctx: Context, pkgs: Set<String>) {
            val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            prefs.edit().putBoolean(KEY_ENABLED, true).putStringSet(KEY_WATCHED, pkgs).apply()
            val req = PeriodicWorkRequestBuilder<GuardWorker>(15, TimeUnit.MINUTES)
                .addTag(TAG).build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
                TAG, ExistingPeriodicWorkPolicy.KEEP, req
            )
        }

        fun disable(ctx: Context) {
            val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            prefs.edit().putBoolean(KEY_ENABLED, false).apply()
            WorkManager.getInstance(ctx).cancelUniqueWork(TAG)
        }
    }

    override suspend fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_ENABLED, false)) return Result.success()

        val watched = prefs.getStringSet(KEY_WATCHED, emptySet()) ?: return Result.success()
        val lastUsed = if (Perms.hasUsageAccess(applicationContext))
            Usage.lastUsedMap(applicationContext, 1) else emptyMap()

        val am = applicationContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val tenMin = 10L * 60_000L
        val now = System.currentTimeMillis()
        val killed = mutableListOf<String>()

        for (pkg in watched) {
            val last = lastUsed[pkg] ?: 0L
            if (last > 0 && now - last > tenMin) {
                try { am.killBackgroundProcesses(pkg); killed += pkg } catch (e: Exception) {}
            } else if (last <= 0) {
                // ako nema podataka, gasimo slobodno
                try { am.killBackgroundProcesses(pkg); killed += pkg } catch (e: Exception) {}
            }
        }

        if (killed.isNotEmpty()) {
            val ts = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(now)
            prefs.edit().putString(KEY_LAST_LOG, "Poslednje gašenje $ts: ${killed.size} app").apply()
        }

        return Result.success()
    }
}
