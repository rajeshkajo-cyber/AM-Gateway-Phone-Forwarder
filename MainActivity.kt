package com.amgateway.smsforwarder

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.text.format.DateUtils
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {
    private lateinit var prefs: Prefs
    private lateinit var status: TextView
    private lateinit var logView: TextView
    private lateinit var pairingInput: EditText
    private lateinit var sendersInput: EditText
    private val ui = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() { refresh(); ui.postDelayed(this, 4000) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = Prefs(this)
        status = findViewById(R.id.status)
        logView = findViewById(R.id.log)
        pairingInput = findViewById(R.id.pairing)
        sendersInput = findViewById(R.id.senders)
        sendersInput.setText(prefs.senders)

        findViewById<Button>(R.id.btnSave).setOnClickListener { savePairing() }
        findViewById<Button>(R.id.btnTest).setOnClickListener { sendTest() }
        findViewById<Button>(R.id.btnPerm).setOnClickListener { askSmsPermission() }
        findViewById<Button>(R.id.btnBattery).setOnClickListener { openBatterySettings() }
        findViewById<Button>(R.id.btnApp).setOnClickListener {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        }
        findViewById<Button>(R.id.btnFlush).setOnClickListener {
            Thread {
                val s = Uploader.flush(this)
                runOnUiThread { toast(if (s == Uploader.Flush.DONE) "Queue is empty" else "Not sent yet - see log"); refresh() }
            }.start()
        }
        findViewById<Button>(R.id.btnUnpair).setOnClickListener {
            prefs.clearPairing(); pairingInput.setText(""); prefs.log("Unpaired"); refresh()
        }
        if (prefs.isPaired) SyncWorker.schedulePeriodic(this)
    }

    override fun onResume() { super.onResume(); ui.post(ticker) }
    override fun onPause() { super.onPause(); ui.removeCallbacks(ticker) }

    private fun savePairing() {
        val senders = sendersInput.text.toString().trim()
        if (senders.split(',', ' ').none { it.filter { ch -> ch.isLetterOrDigit() }.length >= 3 }) {
            toast("Enter the SMS sender IDs (same as on the dashboard), e.g. BHARATPE, HDFCBK"); return
        }
        val code = pairingInput.text.toString()
        if (code.isNotBlank()) {
            val d = Pairing.parse(code)
            if (d == null) { toast("Pairing code is not valid. Copy the whole code from the dashboard."); return }
            prefs.url = d.url; prefs.conn = d.conn; prefs.secret = d.secret
            pairingInput.setText("")
            prefs.log("Paired with " + d.url.removePrefix("https://").substringBefore('/'))
        } else if (!prefs.isPaired) {
            toast("Paste the pairing code from the dashboard first"); return
        }
        prefs.senders = senders
        SyncWorker.schedulePeriodic(this)
        if (!hasSmsPermission()) askSmsPermission()
        sendTest()
    }

    private fun sendTest() {
        if (!prefs.isPaired) { toast("Not paired yet"); return }
        toast("Contacting gateway...")
        Thread {
            val r = Uploader.ping(this)
            prefs.log(if (r.ok) "Test OK - gateway reachable" else "Test failed: " + r.msg)
            runOnUiThread { toast(if (r.ok) "Connected to the gateway" else r.msg); refresh() }
        }.start()
    }

    private fun hasSmsPermission() = ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED

    private fun askSmsPermission() {
        if (hasSmsPermission()) toast("SMS permission already allowed")
        else ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECEIVE_SMS), 11)
    }

    private fun openBatterySettings() {
        val pm = getSystemService(PowerManager::class.java)
        try {
            if (pm != null && !pm.isIgnoringBatteryOptimizations(packageName))
                startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
            else startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        } catch (e: Exception) {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        }
    }

    private fun refresh() {
        val sb = StringBuilder()
        sb.append(if (prefs.isPaired) "Paired: yes  (" + prefs.url.removePrefix("https://").substringBefore('/') + ")\n" else "Paired: NO - paste the pairing code below\n")
        sb.append(if (hasSmsPermission()) "SMS permission: allowed\n" else "SMS permission: NOT allowed - tap \"Allow SMS\"\n")
        val pm = getSystemService(PowerManager::class.java)
        sb.append(if (pm != null && pm.isIgnoringBatteryOptimizations(packageName)) "Background: unrestricted\n" else "Background: may be stopped by battery saver - tap \"Battery: allow background\"\n")
        sb.append("Waiting to send: " + QueueDb.get(this).count() + " SMS\n")
        if (prefs.lastOk > 0L) sb.append("Last contact with gateway: " + DateUtils.getRelativeTimeSpanString(prefs.lastOk, System.currentTimeMillis(), DateUtils.SECOND_IN_MILLIS) + "\n")
        if (prefs.lastError.isNotEmpty()) sb.append("Problem: " + prefs.lastError + "\n")
        status.text = sb.toString().trimEnd()
        logView.text = prefs.logText().ifEmpty { "(nothing yet)" }
    }

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_LONG).show()
}
