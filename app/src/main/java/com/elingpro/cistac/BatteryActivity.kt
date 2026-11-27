package com.elingpro.cistac

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Bundle
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/** Stanje baterije u realnom vremenu: nivo, mA, procena vremena, temperatura. */
class BatteryActivity : AppCompatActivity() {

    private lateinit var tvLevel: TextView
    private lateinit var tvMa: TextView
    private lateinit var tvStatus: TextView
    private lateinit var tvTemp: TextView
    private lateinit var tvEst: TextView
    private lateinit var tvCap: TextView
    private lateinit var tvHealth: TextView
    private lateinit var tvLog: TextView

    private val history = ArrayDeque<Pair<Long, Int>>(120) // (time, level)
    private val maHistory = ArrayDeque<Long>(10)

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) = refresh(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = vbox()
        root.addView(text("🔋  Stanje baterije", 20f, true))
        tvLevel = text("", 40f, true)
        tvMa = text("", 20f, true)
        tvStatus = text("", 16f)
        tvTemp = text("", 15f)
        tvEst = text("", 15f)
        tvCap = text("", 14f)
        tvHealth = text("", 14f)
        tvLog = text("", 12f).apply { alpha = 0.75f }
        for (v in listOf(tvLevel, tvMa, tvStatus, tvTemp, tvEst, tvCap, tvHealth, tvLog))
            root.addView(v)
        setContentView(ScrollView(this).apply { addView(root, android.widget.LinearLayout.LayoutParams(-1, -1)) })
    }

    override fun onResume() {
        super.onResume()
        registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    }

    override fun onPause() {
        super.onPause()
        @Suppress("DEPRECATION")
        unregisterReceiver(receiver)
    }

    private fun refresh(i: Intent) {
        val bm = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val pct = if (scale > 0) level * 100 / scale else level
        val plugged = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
        val status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val tempTenths = i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)
        val health = i.getIntExtra(BatteryManager.EXTRA_HEALTH, 0)
        val voltage = i.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) // mV

        // --- Struja ---
        // CURRENT_NOW: pozitivna = punjenje, negativna = pražnjenje (na većini uređaja)
        val currentNow = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) // µA
        val currentMa = currentNow / 1000.0
        maHistory.addLast(currentNow.toLong())
        if (maHistory.size > 10) maHistory.removeFirst()
        val avgMa = maHistory.average() / 1000.0

        // --- Kapacitet ---
        val designCap = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_FULL_DESIGN)
        val fullCap = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_FULL)
        val remainCharge = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER) // µAh

        // --- Istorija nivoa (procena vremena) ---
        val now = System.currentTimeMillis()
        history.addLast(now to pct)
        if (history.size > 60) history.removeFirst()

        // Procena: koliko minuta treba da pređemo određeni nivo
        val estText: String
        if (history.size >= 3) {
            val oldest = history.first()
            val dLevel = history.last().second - oldest.second
            val dMs = history.last().first - oldest.first
            if (dMs > 0 && dLevel != 0) {
                val minutesPerPercent = dMs / dLevel / 60_000.0
                if (plugged != 0) {
                    val needed = 100 - pct
                    val minToFull = (needed * minutesPerPercent).toLong()
                    estText = "Puno za: ${Fmt.duration(minToFull * 60_000L)}"
                } else {
                    val minsRemaining = (pct * minutesPerPercent).toLong()
                    estText = "Ostalo: ${Fmt.duration(minsRemaining * 60_000L)}"
                }
            } else estText = "Računam procenu…"
        } else estText = "Prikupljam podatke za procenu…"

        // --- Prikaz ---
        val battSymbol = when {
            plugged != 0 -> "⚡"
            pct > 80 -> "🔋"
            pct > 40 -> "🔋"
            pct > 15 -> "🪫"
            else -> "🪫"
        }
        tvLevel.text = "$battSymbol $pct%"
        tvMa.text = when {
            plugged != 0 -> "⬆ +%.0f mA (punjenje)".format(kotlin.math.abs(currentMa))
            else -> "⬇ %.0f mA (pražnjenje)".format(kotlin.math.abs(currentMa))
        }
        tvStatus.text = "Status: ${statusText(status, plugged)} | Napon: $voltage mV"
        tvTemp.text = "Temperatura: %.1f °C".format(tempTenths / 10.0) + tempWarn(tempTenths / 10.0)
        tvEst.text = estText

        val capLine = StringBuilder()
        if (remainCharge > 0) capLine.append("Preostalo: ${Fmt.size(remainCharge.toLong()).replace("B","µAh")}  ")
        if (fullCap > 0) capLine.append("Kapacitet sada: ${fullCap / 1000} mAh  ")
        if (designCap > 0) capLine.append("Orig: ${designCap / 1000} mAh")
        if (fullCap > 0 && designCap > 0) {
            val health = fullCap * 100 / designCap
            capLine.append("  →  Zdravlje: $health%")
        }
        tvCap.text = capLine.toString().ifBlank { "Kapacitet: nije dostupno" }
        tvHealth.text = "Zdravlje baterije: ${healthText(health)}"
        tvLog.text = "Prosek (10 merenja): %.1f mA".format(avgMa) +
                if (voltage > 0 && kotlin.math.abs(currentMa) > 1)
                    "   Snaga: %.2f W".format(voltage / 1000.0 * kotlin.math.abs(currentMa) / 1000.0)
                else ""
    }

    private fun statusText(status: Int, plugged: Int) = when (status) {
        BatteryManager.BATTERY_STATUS_CHARGING -> "Puni se"
        BatteryManager.BATTERY_STATUS_DISCHARGING -> "Prazni se"
        BatteryManager.BATTERY_STATUS_FULL -> "Puno"
        BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Priključen, ne puni"
        else -> if (plugged != 0) "Priključen" else "Nije priključen"
    }

    private fun healthText(h: Int) = when (h) {
        BatteryManager.BATTERY_HEALTH_GOOD -> "Dobro ✅"
        BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Pregrevanje ⚠"
        BatteryManager.BATTERY_HEALTH_DEAD -> "Mrtva ❌"
        BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Previsok napon ⚠"
        BatteryManager.BATTERY_HEALTH_COLD -> "Prehladna ⚠"
        else -> "Nepoznato"
    }

    private fun tempWarn(t: Double) = when {
        t >= 45 -> " ⚠ VRELO – prestani da punIš!"
        t >= 40 -> " ⚠ toplo"
        t < 5 -> " ⚠ prehladno"
        else -> ""
    }
}
