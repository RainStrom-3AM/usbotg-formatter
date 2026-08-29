package dev.kilo.usbotg

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import me.jahnen.libaums.core.UsbMassStorageDevice
import dev.kilo.usbotg.model.DriveInfo
import dev.kilo.usbotg.usb.UsbDeviceManager

class MainActivity : ComponentActivity() {

    private lateinit var usbManager: UsbDeviceManager
    private val detected = mutableStateListOf<UsbMassStorageDevice>()
    private val opened = mutableStateListOf<DriveInfo>()

    private val attachReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> refresh()
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    detected.clear()
                    opened.clear()
                    refresh()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        usbManager = UsbDeviceManager(this)
        refresh()

        val filter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        registerReceiver(attachReceiver, filter)

        setContent {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color(0xFF0F1115)
            ) {
                DetectionDebugScreen(
                    detected = detected,
                    opened = opened,
                    onRequest = { device -> requestAndOpen(device) }
                )
            }
        }
    }

    private fun refresh() {
        detected.clear()
        detected.addAll(usbManager.getConnectedDevices())
    }

    private fun requestAndOpen(device: UsbMassStorageDevice) {
        if (usbManager.hasPermission(device)) {
            runCatching { opened.add(usbManager.openDevice(device)) }
            return
        }
        usbManager.requestPermission(device) { granted ->
            if (granted) {
                runCatching { opened.add(usbManager.openDevice(device)) }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        runCatching { unregisterReceiver(attachReceiver) }
    }
}

@Composable
private fun DetectionDebugScreen(
    detected: List<UsbMassStorageDevice>,
    opened: List<DriveInfo>,
    onRequest: (UsbMassStorageDevice) -> Unit
) {
    val primary = Color(0xFF00D9A3)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("USB OTG Formatter — Module 1 (detection)", color = Color(0xFFF5F5F5))
        Text(
            "Detected devices: ${detected.size}",
            color = Color(0xFF9AA0A6),
            modifier = Modifier.padding(vertical = 8.dp)
        )
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(detected) { device ->
                val name = device.usbDevice.productName ?: "USB Drive"
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(name, color = Color(0xFFF5F5F5))
                    Button(onClick = { onRequest(device) }) {
                        Text("Open")
                    }
                }
            }
            items(opened) { info ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                ) {
                    Text(info.productName, color = primary)
                    Text("Capacity: ${info.capacityText}", color = Color(0xFF9AA0A6))
                    Text("Sector: ${info.sectorSize} B", color = Color(0xFF9AA0A6))
                    Text("Current FS: ${info.currentFs.label}", color = Color(0xFF9AA0A6))
                }
            }
        }
    }
}
