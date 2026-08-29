package dev.kilo.usbotg.model

data class DriveInfo(
    val deviceId: String,
    val productName: String,
    val manufacturer: String,
    val capacityBytes: Long,
    val sectorSize: Int,
    val currentFs: FsType,
    val volumeLabel: String? = null
) {
    val capacityText: String
        get() = formatBytes(capacityBytes)

    private fun formatBytes(bytes: Long): String {
        val units = listOf("B", "KB", "MB", "GB", "TB")
        var value = bytes.toDouble()
        var i = 0
        while (value >= 1024 && i < units.lastIndex) {
            value /= 1024
            i++
        }
        return "%.1f %s".format(value, units[i])
    }
}
