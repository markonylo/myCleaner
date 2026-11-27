package com.elingpro.cistac

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Process
import android.provider.Settings
import java.util.Locale

object Perms {

    /** Da li je korisnik dao "Pristup korišćenju" (Usage access). */
    fun hasUsageAccess(ctx: Context): Boolean {
        val appOps = ctx.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= 29) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName
            )
        }
        if (mode == AppOpsManager.MODE_DEFAULT) {
            return ctx.checkCallingOrSelfPermission(android.Manifest.permission.PACKAGE_USAGE_STATS) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun openUsageAccess(ctx: Context) {
        val i = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        try {
            ctx.startActivity(i.setData(Uri.parse("package:${ctx.packageName}")))
        } catch (e: Exception) {
            ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
    }

    /** Pristup svim fajlovima (potreban za brisanje temp fajlova). */
    fun hasAllFiles(ctx: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager()
        else ctx.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    fun openAllFiles(ctx: Context) {
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                ctx.startActivity(
                    Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                        .setData(Uri.parse("package:${ctx.packageName}"))
                )
            } catch (e: Exception) {
                ctx.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
        } else {
            openAppDetails(ctx, ctx.packageName)
        }
    }

    fun openAppDetails(ctx: Context, pkg: String) {
        ctx.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.parse("package:$pkg"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

object Usage {
    /**
     * Vreme poslednjeg korišćenja za svaki paket, gledajući unazad [days] dana.
     * Ako aplikacija nije korišćena u tom periodu, nema je u mapi.
     */
    fun lastUsedMap(ctx: Context, days: Int): Map<String, Long> {
        val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val start = now - days * 24L * 3600_000L
        val result = HashMap<String, Long>()
        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_BEST, start, now) ?: return result
        for (s in stats) {
            // Uzimamo samo stvarno korišćenje (vreme u prvom planu > 0)
            if (s.totalTimeInForeground <= 0 && s.lastTimeUsed <= start) continue
            val prev = result[s.packageName] ?: 0L
            if (s.lastTimeUsed > prev && s.totalTimeInForeground > 0) result[s.packageName] = s.lastTimeUsed
        }
        return result
    }
}

object Fmt {
    fun size(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format(Locale.US, "%.0f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024) return String.format(Locale.US, "%.1f MB", mb)
        return String.format(Locale.US, "%.2f GB", mb / 1024.0)
    }

    fun duration(ms: Long): String {
        val totalMin = ms / 60000
        val h = totalMin / 60
        val m = totalMin % 60
        return if (h > 0) "${h} h ${m} min" else "${m} min"
    }

    fun daysAgo(time: Long): String {
        if (time <= 0) return "nikad (u poslednjih godinu dana)"
        val d = (System.currentTimeMillis() - time) / (24L * 3600_000L)
        return "pre $d dana"
    }
}
