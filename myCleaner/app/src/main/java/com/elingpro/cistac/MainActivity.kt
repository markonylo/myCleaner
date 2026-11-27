package com.elingpro.cistac

import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var usageStatus: TextView
    private lateinit var filesStatus: TextView
    private lateinit var guardStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = vbox()

        root.addView(text("Dozvole", 18f, true))
        usageStatus = text("")
        root.addView(usageStatus)
        root.addView(button("Dozvoli pristup korišćenju") { Perms.openUsageAccess(this) }, lpMatchWrap())
        filesStatus = text("")
        root.addView(filesStatus)
        root.addView(button("Dozvoli pristup svim fajlovima") { Perms.openAllFiles(this) }, lpMatchWrap())

        root.addView(text(" ", 8f))
        root.addView(text("Alati", 18f, true))
        val big = { label: String, cls: Class<*> ->
            button(label) { startActivity(Intent(this, cls)) }.apply {
                minimumHeight = dp(64)
                textSize = 17f
            }
        }
        root.addView(big("🧹  Neiskorišćene aplikacije (30+ dana)", UnusedAppsActivity::class.java), lpMatchWrap())
        root.addView(big("🗑  Keš i temp fajlovi", CleanerActivity::class.java), lpMatchWrap())
        root.addView(big("🔋  Stanje baterije (mA)", BatteryActivity::class.java), lpMatchWrap())
        root.addView(big("⚡  Potrošači baterije i čuvar", HogsActivity::class.java), lpMatchWrap())

        guardStatus = text("", 13f).apply { alpha = 0.75f }
        root.addView(guardStatus)

        root.addView(text(
            "Sistemske (fabričke) aplikacije ne mogu da se uklone iz obične aplikacije. " +
                    "Za njih koristi Windows ADB alat koji ide uz ovu aplikaciju.", 12f
        ).apply { alpha = 0.6f })

        setContentView(ScrollView(this).apply { addView(root, LinearLayout.LayoutParams(-1, -2)) })
    }

    override fun onResume() {
        super.onResume()
        usageStatus.text = if (Perms.hasUsageAccess(this)) "✅ Pristup korišćenju: odobren"
        else "❌ Pristup korišćenju: NIJE odobren (potreban za liste aplikacija i keš)"
        filesStatus.text = if (Perms.hasAllFiles(this)) "✅ Pristup fajlovima: odobren"
        else "❌ Pristup fajlovima: NIJE odobren (potreban za brisanje temp fajlova)"
        val p = getSharedPreferences(GuardWorker.PREFS, MODE_PRIVATE)
        val on = p.getBoolean(GuardWorker.KEY_ENABLED, false)
        val watched = p.getStringSet(GuardWorker.KEY_WATCHED, emptySet())!!.size
        val last = p.getString(GuardWorker.KEY_LAST_LOG, "") ?: ""
        guardStatus.text = if (on) "Čuvar baterije: UKLJUČEN, prati $watched aplikacija. $last"
        else "Čuvar baterije: isključen"
    }
}
