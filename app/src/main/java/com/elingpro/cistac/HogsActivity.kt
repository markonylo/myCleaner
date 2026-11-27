package com.elingpro.cistac

import android.app.ActivityManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Prikaz potrošača baterije po vremenu u prvom planu. Opcija gašenja pozadinskih procesa. */
class HogsActivity : AppCompatActivity() {

    private lateinit var adapter: CheckAdapter
    private lateinit var info: TextView
    private lateinit var progress: ProgressBar
    private val am get() = getSystemService(ACTIVITY_SERVICE) as ActivityManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = vbox(8)
        progress = ProgressBar(this)
        root.addView(progress)
        root.addView(text("⚡  Potrošači baterije", 20f, true))
        info = text("Računam…")
        root.addView(info)
        root.addView(text(
            "Sortovano po vremenu u prvom planu (poslednjih 7 dana). " +
                    "Android ne daje direktnu mA po aplikaciji — ovo je najbliža procena bez root-a.", 12f
        ).apply { alpha = 0.6f })

        val bar1 = hbox()
        bar1.addView(button("Označi top 5") { markTop5() }, lpWeight())
        bar1.addView(button("Poništi") { adapter.setAll(false) }, lpWeight())
        root.addView(bar1)

        val bar2 = hbox()
        bar2.addView(button("🔫  Ubij pozadinske procese") { killBg() }, lpWeight())
        bar2.addView(button("🔒  Ograniči bateriju") { restrictBattery() }, lpWeight())
        root.addView(bar2)

        root.addView(text(
            "„Ubij" gasi procese u RAM-u (efektivno, ali privremeno).\n" +
                    "„Ograniči bateriju" otvara Android postavke gde biramo „Ograničeno" – " +
                    "sistem tada spava aplikaciju posle nekorišćenja.", 12f
        ).apply { alpha = 0.6f })

        adapter = CheckAdapter(this, onChange = { updateInfo() }, onLong = { Perms.openAppDetails(this, it.key) })
        val list = ListView(this).apply { adapter = this@HogsActivity.adapter }
        root.addView(list, android.widget.LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        load()
    }

    override fun onResume() {
        super.onResume()
        // Osvježi prikaz čuvara
    }

    private fun load() {
        if (!Perms.hasUsageAccess(this)) {
            info.text = "Potreban je „Pristup korišćenju". Odobri ga na početnom ekranu."; return
        }
        progress.visibility = android.view.View.VISIBLE
        lifecycleScope.launch {
            val rows = withContext(Dispatchers.Default) { buildRows() }
            adapter.rows = rows
            adapter.notifyDataSetChanged()
            progress.visibility = android.view.View.GONE
            updateInfo()
        }
    }

    private fun buildRows(): MutableList<Row> {
        val usm = getSystemService(USAGE_STATS_SERVICE) as UsageStatsManager
        val pm = packageManager
        val now = System.currentTimeMillis()
        val week = 7 * 24L * 3600_000L
        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_WEEKLY, now - week, now)
            ?: return mutableListOf()

        // Vreme ekrana po aplikaciji (poslednjih 7 dana)
        val screenTime = HashMap<String, Long>()
        for (s in stats) {
            if (s.totalTimeInForeground <= 0) continue
            if (pm.getLaunchIntentForPackage(s.packageName) == null) continue
            screenTime[s.packageName] = (screenTime[s.packageName] ?: 0L) + s.totalTimeInForeground
        }

        // Broj pozadinskih pokretanja (heuristika za pozadinsko buđenje)
        val wakeCount = HashMap<String, Int>()
        try {
            val eventsStart = now - week
            val events = usm.queryEvents(eventsStart, now)
            val event = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                    if (pm.getLaunchIntentForPackage(event.packageName) != null)
                        wakeCount[event.packageName] = (wakeCount[event.packageName] ?: 0) + 1
                }
            }
        } catch (e: Exception) { /* nema pristupa */ }

        return screenTime.entries
            .sortedByDescending { it.value }
            .take(30)
            .map { (pkg, ms) ->
                val label = try { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() } catch (e: Exception) { pkg }
                val wakes = wakeCount[pkg] ?: 0
                Row(
                    key = pkg,
                    title = label,
                    subtitle = "Ekran: ${Fmt.duration(ms)}  •  pokretanja: $wakes",
                    icon = try { pm.getApplicationIcon(pkg) } catch (e: Exception) { null },
                    checked = false,
                    checkable = true,
                    sortValue = -ms
                )
            }.toMutableList()
    }

    private fun updateInfo() {
        val n = adapter.rows.size
        val c = adapter.checkedRows().size
        info.text = "Prikazujem top $n po vremenu ekrana. Označeno: $c"
    }

    private fun markTop5() {
        adapter.rows.forEachIndexed { i, r -> r.checked = i < 5 && r.checkable }
        adapter.notifyDataSetChanged()
        updateInfo()
    }

    // --- Gašenje pozadinskih procesa ---
    private fun killBg() {
        val sel = adapter.checkedRows()
        if (sel.isEmpty()) { Toast.makeText(this, "Označi aplikacije", Toast.LENGTH_SHORT).show(); return }
        AlertDialog.Builder(this)
            .setTitle("Ubij pozadinske procese")
            .setMessage(
                "Gasiće se ${sel.size} aplikacija iz pozadine:\n" +
                        sel.joinToString("\n") { "• ${it.title}" } +
                        "\n\nAko ih otvoriš ponovo, ponovo će se pokrenuti. Nastavljaš?"
            )
            .setPositiveButton("Gasi") { _, _ ->
                var cnt = 0
                for (r in sel) {
                    try { am.killBackgroundProcesses(r.key); cnt++ } catch (e: Exception) {}
                }
                Toast.makeText(this, "Ugašeno $cnt aplikacija", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Odustani", null)
            .show()
    }

    // --- Ograničavanje baterije u podešavanjima ---
    private fun restrictBattery() {
        val sel = adapter.checkedRows()
        if (sel.isEmpty()) { Toast.makeText(this, "Označi aplikacije", Toast.LENGTH_SHORT).show(); return }
        AlertDialog.Builder(this)
            .setTitle("Ograniči bateriju")
            .setMessage(
                "Otvoriću podešavanja za ${sel.size} aplikacija.\n" +
                        "Za svaku: tap na „Baterija" → odaberi „Ograničeno".\n" +
                        "Android će ih automatski gasiti posle nekorišćenja.\n\n" +
                        "Nastavljaš?"
            )
            .setPositiveButton("Otvori podešavanja") { _, _ ->
                for (r in sel) {
                    try {
                        startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                .setData(android.net.Uri.parse("package:${r.key}"))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    } catch (e: Exception) {}
                }
            }
            .setNegativeButton("Odustani", null)
            .show()
    }
}
