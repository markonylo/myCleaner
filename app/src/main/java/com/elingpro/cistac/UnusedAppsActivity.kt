package com.elingpro.cistac

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class UnusedAppsActivity : AppCompatActivity() {

    private lateinit var adapter: CheckAdapter
    private lateinit var info: TextView
    private lateinit var progress: ProgressBar
    private val queue = ArrayDeque<String>()
    private var showSystem = false

    private val uninstallLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            uninstallNext()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = vbox(8)
        info = text("Učitavam…")
        progress = ProgressBar(this)
        root.addView(info)
        root.addView(progress)

        val bar1 = hbox()
        bar1.addView(button("Označi sve") { adapter.setAll(true) }, lpWeight())
        bar1.addView(button("Poništi") { adapter.setAll(false) }, lpWeight())
        bar1.addView(button("Sistemske: ne") {}.also { b ->
            b.setOnClickListener {
                showSystem = !showSystem
                b.text = if (showSystem) "Sistemske: da" else "Sistemske: ne"
                load()
            }
        }, lpWeight())
        root.addView(bar1)

        val bar2 = hbox()
        bar2.addView(button("Deinstaliraj označene") { confirmUninstall() }, lpWeight())
        bar2.addView(button("Kopiraj za ADB") { copyForAdb() }, lpWeight())
        root.addView(bar2)

        root.addView(text("Dodir = označi. Dugi dodir = podešavanja aplikacije.", 12f).apply { alpha = 0.6f })

        adapter = CheckAdapter(this, onChange = { updateInfo() }, onLong = { Perms.openAppDetails(this, it.key) })
        val list = ListView(this).apply { adapter = this@UnusedAppsActivity.adapter }
        root.addView(list, android.widget.LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        if (queue.isEmpty()) load()
    }

    private fun load() {
        if (!Perms.hasUsageAccess(this)) {
            progress.visibility = android.view.View.GONE
            info.text = "Potreban je „Pristup korišćenju“. Vrati se na početni ekran i odobri ga."
            return
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
        val pm = packageManager
        val lastUsed = Usage.lastUsedMap(this, 365)
        val now = System.currentTimeMillis()
        val month = 30L * 24 * 3600_000L
        val rows = mutableListOf<Row>()

        @Suppress("DEPRECATION")
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        for (ai in apps) {
            if (ai.packageName == packageName) continue
            val isSystem = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val isUpdatedSystem = (ai.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
            // Prikazujemo samo aplikacije koje imaju ikonicu u meniju
            if (pm.getLaunchIntentForPackage(ai.packageName) == null) continue
            if (isSystem && !showSystem) continue

            val installTime = try {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(ai.packageName, 0).firstInstallTime
            } catch (e: Exception) { 0L }
            // Tek instalirane ne nudimo
            if (!isSystem && now - installTime < month) continue

            val last = lastUsed[ai.packageName] ?: 0L
            if (last > 0 && now - last < month) continue

            val label = pm.getApplicationLabel(ai).toString()
            val sub = buildString {
                append("Poslednje korišćenje: ").append(Fmt.daysAgo(last))
                if (isSystem) append(if (isUpdatedSystem) "  •  sistemska (ažurirana)" else "  •  sistemska – ukloni preko ADB")
            }
            rows += Row(
                key = ai.packageName,
                title = label,
                subtitle = sub + "\n" + ai.packageName,
                icon = try { pm.getApplicationIcon(ai) } catch (e: Exception) { null },
                checked = false,
                checkable = true,
                sortValue = last,
                extra = isSystem
            )
        }
        rows.sortBy { it.sortValue } // najduže nekorišćene prve
        return rows
    }

    private fun updateInfo() {
        val n = adapter.rows.size
        val c = adapter.checkedRows().size
        info.text = "Nekorišćeno duže od 30 dana: $n aplikacija. Označeno: $c"
    }

    private fun confirmUninstall() {
        val sel = adapter.checkedRows()
        if (sel.isEmpty()) { Toast.makeText(this, "Ništa nije označeno", Toast.LENGTH_SHORT).show(); return }
        val user = sel.filter { it.extra != true }
        val sys = sel.filter { it.extra == true }
        val msg = buildString {
            append("Deinstaliraće se ${user.size} aplikacija. Android će za svaku tražiti potvrdu.\n\n")
            append(user.joinToString("\n") { "• " + it.title })
            if (sys.isNotEmpty()) {
                append("\n\n${sys.size} sistemskih se preskače – za njih upotrebi „Kopiraj za ADB“ i Windows alat.")
            }
        }
        AlertDialog.Builder(this)
            .setTitle("Potvrda brisanja")
            .setMessage(msg)
            .setPositiveButton("Deinstaliraj") { _, _ ->
                queue.clear(); queue.addAll(user.map { it.key }); uninstallNext()
            }
            .setNegativeButton("Odustani", null)
            .show()
    }

    private fun uninstallNext() {
        val pkg = queue.removeFirstOrNull()
        if (pkg == null) { load(); return }
        try {
            @Suppress("DEPRECATION")
            val i = Intent(Intent.ACTION_UNINSTALL_PACKAGE, Uri.parse("package:$pkg"))
                .putExtra(Intent.EXTRA_RETURN_RESULT, true)
            uninstallLauncher.launch(i)
        } catch (e: Exception) {
            uninstallNext()
        }
    }

    private fun copyForAdb() {
        val sel = adapter.checkedRows().ifEmpty { adapter.rows }
        val txt = sel.joinToString("\n") { it.key }
        val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("paketi", txt))
        Toast.makeText(this, "Kopirano ${sel.size} paketa. Nalepi ih u paketi.txt za ADB alat.", Toast.LENGTH_LONG).show()
    }
}
