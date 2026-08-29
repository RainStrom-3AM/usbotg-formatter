package dev.kilo.usbotg.usb

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.os.Build
import android.app.PendingIntent
import dev.kilo.usbotg.model.DriveInfo
import dev.kilo.usbotg.model.FsType
import me.jahnen.libaums.core.UsbMassStorageDevice
import me.jahnen.libaums.core.driver.BlockDeviceDriver
import me.jahnen.libaums.core.driver.BlockDeviceDriverFactory
import me.jahnen.libaums.core.usb.UsbCommunicationFactory
import java.io.IOException

class UsbDeviceManager(private val context: Context) {

    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager

    fun getConnectedDevices(): List<UsbMassStorageDevice> {
        return try {
            UsbMassStorageDevice.getMassStorageDevices(context).toList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun hasPermission(device: UsbMassStorageDevice): Boolean {
        return usbManager.hasPermission(device.usbDevice)
    }

    fun requestPermission(device: UsbMassStorageDevice, onResult: (Boolean) -> Unit) {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_MUTABLE
        } else {
            0
        }
        val pi = PendingIntent.getBroadcast(
            context,
            0,
            Intent(ACTION_USB_PERMISSION).setPackage(context.packageName),
            flags
        )
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                try {
                    context.unregisterReceiver(this)
                } catch (_: Exception) {
                }
                onResult(granted)
            }
        }
        context.registerReceiver(receiver, IntentFilter(ACTION_USB_PERMISSION))
        usbManager.requestPermission(device.usbDevice, pi)
    }

    fun openRawBlockDevice(device: UsbMassStorageDevice): BlockDeviceDriver {
        if (!usbManager.hasPermission(device.usbDevice)) {
            throw SecurityException("USB permission not granted")
        }
        return buildBlockDevice(device.usbDevice)
    }

    fun openDevice(device: UsbMassStorageDevice): DriveInfo {
        val block = openRawBlockDevice(device)
        val capacity = BlockDeviceReader.readCapacityBytes(block)
        val fs = BlockDeviceReader.detectFileSystem(block)
        val label = BlockDeviceReader.readVolumeLabel(block, fs)
        val usb = device.usbDevice
        return DriveInfo(
            deviceId = "${usb.vendorId}:${usb.productId}:${usb.deviceId}",
            productName = usb.productName ?: "USB Drive",
            manufacturer = usb.manufacturerName ?: "Unknown",
            capacityBytes = capacity,
            sectorSize = block.blockSize,
            currentFs = fs,
            volumeLabel = label
        )
    }

    private fun buildBlockDevice(usbDevice: UsbDevice): BlockDeviceDriver {
        val iface = (0 until usbDevice.interfaceCount)
            .asSequence()
            .map { usbDevice.getInterface(it) }
            .firstOrNull { isMassStorageInterface(it) }
            ?: throw IOException("No mass storage interface found")

        var outEndpoint: UsbEndpoint? = null
        var inEndpoint: UsbEndpoint? = null
        for (j in 0 until iface.endpointCount) {
            val ep = iface.getEndpoint(j)
            if (ep.type != UsbConstants.USB_ENDPOINT_XFER_BULK) continue
            if (ep.direction == UsbConstants.USB_DIR_OUT) outEndpoint = ep
            else inEndpoint = ep
        }
        if (outEndpoint == null || inEndpoint == null) {
            throw IOException("Mass storage bulk endpoints not found")
        }

        val comm = UsbCommunicationFactory.createUsbCommunication(
            usbManager,
            usbDevice,
            iface,
            outEndpoint,
            inEndpoint
        )
        val block = BlockDeviceDriverFactory.createBlockDevice(comm, 0)
        block.init()
        return block
    }

    private fun isMassStorageInterface(iface: UsbInterface): Boolean {
        return iface.interfaceClass == UsbConstants.USB_CLASS_MASS_STORAGE &&
            iface.interfaceSubclass == 6 &&
            iface.interfaceProtocol == 80
    }

    companion object {
        const val ACTION_USB_PERMISSION = "dev.kilo.usbotg.USB_PERMISSION"
    }
}
