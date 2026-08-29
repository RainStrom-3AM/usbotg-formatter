package dev.kilo.usbotg.usb

import me.jahnen.libaums.core.driver.BlockDeviceDriver
import dev.kilo.usbotg.model.FsType
import java.nio.ByteBuffer
import java.nio.ByteOrder

object BlockDeviceReader {

    fun readCapacityBytes(block: BlockDeviceDriver): Long {
        return block.blockSize.toLong() * block.blocks
    }

    fun detectFileSystem(block: BlockDeviceDriver): FsType {
        val sector = readSector(block, 0) ?: return FsType.UNKNOWN
        val b = sector.array()

        val oem = String(b.sliceArray(3..7), Charsets.US_ASCII).trim()
        when (oem) {
            "EXFAT" -> return FsType.EXFAT
            "NTFS" -> return FsType.NTFS
        }

        val bootSig = b[510].toInt() and 0xFF == 0x55 && b[511].toInt() and 0xFF == 0xAA
        if (bootSig) {
            val fat32 = String(b.sliceArray(82..87), Charsets.US_ASCII)
            if (fat32.startsWith("FAT32")) return FsType.FAT32
            val fat12_16 = String(b.sliceArray(54..61), Charsets.US_ASCII)
            if (fat12_16.startsWith("FAT16")) return FsType.FAT16
            if (fat12_16.startsWith("FAT12")) return FsType.FAT16
        }

        val extMagic = (b[1081].toInt() and 0xFF shl 8) or (b[1080].toInt() and 0xFF)
        if (extMagic == 0xEF53) return FsType.EXT4

        return FsType.UNKNOWN
    }

    fun readVolumeLabel(block: BlockDeviceDriver, fs: FsType): String? {
        if (fs != FsType.FAT16 && fs != FsType.FAT32 && fs != FsType.EXFAT) return null
        val sector = readSector(block, 0) ?: return null
        val b = sector.array()
        val raw = String(b.sliceArray(71..81), Charsets.US_ASCII)
        val label = raw.trim { it <= ' ' }
        return if (label.isEmpty() || label == "NO NAME") null else label
    }

    private fun readSector(block: BlockDeviceDriver, lba: Long): ByteBuffer? {
        val buffer = ByteBuffer.allocate(block.blockSize).order(ByteOrder.LITTLE_ENDIAN)
        return try {
            block.read(lba, buffer)
            buffer.rewind()
            buffer
        } catch (e: Exception) {
            null
        }
    }
}
