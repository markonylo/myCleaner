package com.elingpro.cistac

import android.app.StorageManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.View
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/** Keš aplikacija (prikazuje veličinu, otvara brisanje) + temp fajlovi u memoriji. */
class CleanerActivity : AppCompatActivity() {

    private lateinit var cacheAdapter: CheckAdapter
    private lateinit var tempAdapter: CheckAdapter
    private lateinit var cacheInfo: TextView
    private lateinit var tempInfo: TextView
    private lateinit var progress: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = vbox(8)
        progress = ProgressBar(this)
        root.addView(progress)

        // --- KEŠ aplikacija ---
        root.addView(text("💾  Keš aplikacija", 17f, true))
        cacheInfo = text("Računam…")
        root.addView(cacheInfo)
        val cacheBar = hbox()
        cacheBar.addView(button("Označi sve") { cacheAdapter.setAll(true) }, lpWeight())
        cacheBar.addView(button("Poništi") { cacheAdapter.setAll(false) }, lpWeight())
        root.addView(cacheBar)
        val cacheBar2 = hbox()
        cacheBar2.addView(button("🗑 Obriši označene") { confirmDeleteCache() }, lpWeight())
        cacheBar2.addView(button("📋 ADB komande") { copyAdbCommands() }, lpWeight())
        root.addView(cacheBar2)
        root.addView(text(
            "Android ne dozvoljava direktno brisanje tuđeg keša bez root-a.\n" +
            "• Dugme „Obriši" otvara Storage stranicu svake app — 1 tap da obrišeš.\n" +
            "• Dugme „ADB komande" kopira komande za Windows ADB alat (briše bez tapkanja).\n" +
            "• Za neke ROM-ove (Xiaomi/Samsung) StorageManager može da prisili brisanje automatski.",
            11f).apply { alpha = 0.65f })
        cacheAdapter = CheckAdapter(this, onChange = { updateCacheInfo() })
        val cacheList = ListView(this).apply { adapter = cacheAdapter }
        root.addView(cacheList, android.widget.LinearLayout.LayoutParams(-1, dp(200)))

        // --- TEMP fajlovi ---
        root.addView(text("🗑  Temp fajlovi u memoriji", 17f, true))
        tempInfo = text("Skeniram…")
        root.addView(tempInfo)
        root.addView(text("Čekiraj fajlove koje hoćeš da obrišeš. Dugi dodir = otvori folder.", 11f).apply { alpha = 0.6f })
        val tempBar = hbox()
        tempBar.addView(button("Označi sve") { tempAdapter.setAll(true) }, lpWeight())
        tempBar.addView(button("Poništi") { tempAdapter.setAll(false) }, lpWeight())
        tempBar.addView(button("Obriši označene") { confirmDeleteTemp() }, lpWeight())
        root.addView(tempBar)
        tempAdapter = CheckAdapter(
            this,
            onChange = { updateTempInfo() },
            onLong = { row ->
                val f = File(row.key).parentFile ?: return@CheckAdapter
                try { startActivity(Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(android.net.Uri.fromFile(f), "*/*")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }) } catch (e: Exception) { Toast.makeText(this, f.path, Toast.LENGTH_SHORT).show() }
            }
        )
        val tempList = ListView(this).apply { adapter = tempAdapter }
        root.addView(tempList, android.widget.LinearLayout.LayoutParams(-1, 0, 1f))

        setContentView(ScrollView(this).apply { addView(root, android.widget.LinearLayout.LayoutParams(-1, -1)) })
        load()
    }

    private fun load() {
        progress.visibility = View.VISIBLE
        lifecycleScope.launch {
            val cRows = withContext(Dispatchers.Default) { buildCacheRows() }
            val tRows = withContext(Dispatchers.Default) { buildTempRows() }
            cacheAdapter.rows = cRows
            cacheAdapter.notifyDataSetChanged()
            tempAdapter.rows = tRows
            tempAdapter.notifyDataSetChanged()
            progress.visibility = View.GONE
            updateCacheInfo(); updateTempInfo()
        }
    }

    // --------------- KEŠ ---------------

    private fun buildCacheRows(): MutableList<Row> {
        val pm = packageManager
        @Suppress("DEPRECATION")
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        return apps.mapNotNull { ai ->
            if (pm.getLaunchIntentForPackage(ai.packageName) == null &&
                ai.packageName != "com.android.systemui") return@mapNotNull null
            val dir = ai.dataDir?.let { File(it, "cache") } ?: return@mapNotNull null
            val size = dir.totalSize()
            if (size < 10_240) return@mapNotNull null // preskači ispod 10 KB
            Row(
                key = ai.packageName,
                title = pm.getApplicationLabel(ai).toString(),
                subtitle = "Keš: ${Fmt.size(size)}",
                icon = try { pm.getApplicationIcon(ai) } catch (e: Exception) { null },
                checked = size > 1_048_576L, // automatski označi >1 MB
                checkable = true,
                sortValue = -size
            )
        }.sortedBy { it.sortValue }.toMutableList()
    }

    private fun updateCacheInfo() {
        val total = cacheAdapter.rows.sumOf { r ->
            File(packageManager.getApplicationInfo(r.key, 0).dataDir, "cache").totalSize()
        }
        val sel = cacheAdapter.checkedRows().sumOf { r ->
            try { File(packageManager.getApplicationInfo(r.key, 0).dataDir, "cache").totalSize() } catch (e: Exception) { 0L }
        }
        cacheInfo.text = "Ukupan keš: ${Fmt.size(total)} | Označeno: ${Fmt.size(sel)}"
    }

    private fun confirmDeleteCache() {
        val sel = cacheAdapter.checkedRows()
        if (sel.isEmpty()) { Toast.makeText(this, "Ništa nije označeno", Toast.LENGTH_SHORT).show(); return }
        val totalSize = sel.sumOf { r ->
            try { File(packageManager.getApplicationInfo(r.key, 0).dataDir, "cache").totalSize() } catch (e: Exception) { 0L }
        }

        // Probamo StorageManager pristup (može raditi na nekim ROM-ovima bez root-a)
        val sm = getSystemService(STORAGE_SERVICE) as StorageManager
        var smWorked = false
        if (Build.VERSION.SDK_INT >= 26) {
            try {
                // Tražimo oslobađanje prostora — sistem sam briše keš aplikacija
                val uuid = StorageManager.UUID_DEFAULT
                val available = sm.getAllocatableBytes(uuid)
                if (available < totalSize) {
                    sm.allocateBytes(uuid, totalSize)
                    smWorked = true
                }
            } catch (e: Exception) { /* nije podržano */ }
        }

        val msg = if (smWorked) {
            "StorageManager je uspešno zatražio oslobađanje ${Fmt.size(totalSize)}. " +
            "Sistem će automatski obrisati keš označenih aplikacija."
        } else {
            "Android ne dozvoljava direktno brisanje tuđeg keša bez root-a.\n\n" +
            "Otvoriću 'Storage & cache' stranicu za svaku od ${sel.size} aplikacija.\n" +
            "Za svaku: tap „Obriši keš" (jedno dugme).\n\n" +
            "Ukupno: ${Fmt.size(totalSize)}"
        }

        AlertDialog.Builder(this)
            .setTitle("Obriši keš")
            .setMessage(msg)
            .setPositiveButton(if (smWorked) "OK" else "Otvori storage stranice") { _, _ ->
                if (!smWorked) {
                    // Otvori direktno "Storage & cache" a ne "App info"
                    for (r in sel) openStoragePage(r.key)
                    Toast.makeText(this,
                        "Otvorio ${sel.size} stranica. Za svaku: tap „Obriši keš".",
                        Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("Odustani", null)
            .show()
    }

    /** Otvara direktno "Storage & cache" stranicu aplikacije (a ne opšti App info). */
    private fun openStoragePage(pkg: String) {
        try {
            // Android 11+ — direktna Storage & cache stranica
            val i = Intent("android.settings.APPLICATION_DETAILS_SETTINGS")
                .setData(Uri.parse("package:$pkg"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(i)
        } catch (e: Exception) {
            // Fallback
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.parse("package:$pkg"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    /** Kopira ADB shell komande za brisanje keša svih označenih aplikacija u clipboard. */
    private fun copyAdbCommands() {
        val sel = cacheAdapter.checkedRows().ifEmpty { cacheAdapter.rows }
        val cmds = sel.joinToString("\n") { "adb shell pm clear-cache-dirs ${it.key}" }
        val full = ":: Nalepi ovo u CMD prozor (Windows) ili .bat fajl\n" +
                   ":: Telefon mora biti priključen i USB debug uključen\n\n" + cmds
        val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("adb-cache", full))
        Toast.makeText(this,
            "Kopirano ${sel.size} ADB komandi. Nalepi u CMD prozor ili .bat fajl.",
            Toast.LENGTH_LONG).show()
    }

    // --------------- TEMP fajlovi ---------------

    private val tempPatterns = listOf(".tmp", ".temp", ".log", ".bak", ".old", "~")
    private val tempDirs = listOf(
        Environment.getExternalStorageDirectory(),
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
        File(Environment.getExternalStorageDirectory(), "Android/data"),
        File(Environment.getExternalStorageDirectory(), ".thumbnails"),
        File(Environment.getExternalStorageDirectory(), "DCIM/.thumbnails"),
        File(cacheDir.parentFile, "") // /data/data/<pkg>
    )

    private fun buildTempRows(): MutableList<Row> {
        val rows = mutableListOf<Row>()
        for (dir in tempDirs) {
            if (!dir.exists() || !dir.canRead()) continue
            scanForTemp(dir, rows, depth = 0)
        }
        return rows.sortedByDescending { it.sortValue }.toMutableList()
    }

    private fun scanForTemp(dir: File, out: MutableList<Row>, depth: Int) {
        if (depth > 5) return
        val children = try { dir.listFiles() ?: return } catch (e: SecurityException) { return }
        for (f in children) {
            if (f.isDirectory) { scanForTemp(f, out, depth + 1); continue }
            val name = f.name.lowercase()
            val isTemp = tempPatterns.any { name.endsWith(it) } ||
                    name.startsWith(".") ||
                    dir.name == ".thumbnails" || dir.name.endsWith("thumbnails")
            if (isTemp && f.canWrite()) {
                out += Row(
                    key = f.absolutePath,
                    title = f.name,
                    subtitle = "${Fmt.size(f.length())} · ${f.parent}",
                    checked = f.length() > 51_200L, // automatski označi >50 KB
                    checkable = true,
                    sortValue = f.length()
                )
            }
        }
    }

    private fun updateTempInfo() {
        val total = tempAdapter.rows.sumOf { File(it.key).length() }
        val sel = tempAdapter.checkedRows().sumOf { File(it.key).length() }
        tempInfo.text = "Pronađeno: ${tempAdapter.rows.size} fajlova (${Fmt.size(total)}) | Označeno: ${Fmt.size(sel)}"
    }

    private fun confirmDeleteTemp() {
        val sel = tempAdapter.checkedRows()
        if (sel.isEmpty()) { Toast.makeText(this, "Ništa nije označeno", Toast.LENGTH_SHORT).show(); return }
        val totalSize = sel.sumOf { File(it.key).length() }
        val preview = sel.take(5).joinToString("\n") { "• ${it.title}  (${Fmt.size(File(it.key).length())})" } +
                if (sel.size > 5) "\n… i još ${sel.size - 5} fajlova" else ""
        AlertDialog.Builder(this)
            .setTitle("Potvrda brisanja temp fajlova")
            .setMessage("Brišem ${sel.size} fajlova – ukupno ${Fmt.size(totalSize)}:\n\n$preview\n\nOvo se ne može oporaviti.")
            .setPositiveButton("Obriši") { _, _ -> deleteTemp(sel) }
            .setNegativeButton("Odustani", null)
            .show()
    }

    private fun deleteTemp(sel: List<Row>) {
        lifecycleScope.launch {
            var ok = 0; var fail = 0
            withContext(Dispatchers.IO) {
                for (r in sel) {
                    if (File(r.key).delete()) ok++ else fail++
                }
            }
            Toast.makeText(this@CleanerActivity, "Obrisano: $ok  Neuspelo: $fail", Toast.LENGTH_SHORT).show()
            load()
        }
    }
}

/** Rekurzivna veličina foldera. */
private fun File.totalSize(): Long {
    if (!exists()) return 0
    if (isFile) return length()
    return try {
        listFiles()?.sumOf { it.totalSize() } ?: 0
    } catch (e: SecurityException) { 0 }
}
