package dev.kilo.usbotg.formatters.fat32

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale

object Fat32Structures {

    const val BOOT_SIGNATURE = 0xAA55
    const val FSINFO_LEAD_SIG = 0x41615252
    const val FSINFO_STRUCT_SIG = 0x61417272
    const val FSINFO_TRAIL_SIG = 0xAA550000.toInt()

    fun normalizeVolumeLabel(label: String?): String {
        if (label.isNullOrBlank()) return "NO NAME   "
        val allowed = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789!#\$%&'()-@^_`{}~ "
        val cleaned = label.uppercase(Locale.US)
            .map { if (it in allowed) it else ' ' }
            .joinToString("")
        return cleaned.take(11).padEnd(11, ' ')
    }

    fun buildBootSector(
        sectorSize: Int,
        sectorsPerCluster: Int,
        reservedSectors: Int,
        numFats: Int,
        totalSectors: Long,
        fatSize: Long,
        fsinfoSector: Int,
        backupBootSector: Int,
        volumeLabel: String,
        volumeId: Int
    ): ByteArray {
        val buf = ByteBuffer.allocate(sectorSize).order(ByteOrder.LITTLE_ENDIAN)

        buf.put(byteArrayOf(0xEB.toByte(), 0x58, 0x90.toByte())) // jump instruction
        buf.put("MSDOS5.0".toByteArray(Charsets.US_ASCII))       // OEM name (8 bytes)

        buf.putShort(sectorSize.toShort())                       // BPB_BytsPerSec
        buf.put(sectorsPerCluster.toByte())                     // BPB_SecPerClus
        buf.putShort(reservedSectors.toShort())                  // BPB_RsvdSecCnt
        buf.put(numFats.toByte())                               // BPB_NumFATs
        buf.putShort(0)                                         // BPB_RootEntCnt (0 for FAT32)
        buf.putShort(0)                                         // BPB_TotSec16 (0 for FAT32)
        buf.put(0xF8.toByte())                                  // BPB_Media (fixed disk)
        buf.putShort(0)                                         // BPB_FATSz16 (0 for FAT32)
        buf.putShort(0x20)                                      // BPB_SecPerTrk
        buf.putShort(0x40)                                      // BPB_NumHeads
        buf.putInt(0)                                           // BPB_HiddSec
        buf.putInt(totalSectors.toInt())                        // BPB_TotSec32 (low 32 bits)
        buf.putInt(fatSize.toInt())                             // BPB_FATSz32
        buf.putShort(0)                                         // BPB_ExtFlags
        buf.putShort(0)                                         // BPB_FSVer
        buf.putInt(2)                                           // BPB_RootClus
        buf.putShort(fsinfoSector.toShort())                    // BPB_FSInfo
        buf.putShort(backupBootSector.toShort())                // BPB_BkBootSec
        repeat(12) { buf.put(0) }                               // BPB_Reserved
        buf.put(0x80.toByte())                                  // BS_DrvNum
        buf.put(0)                                              // BS_Reserved
        buf.put(0x29.toByte())                                  // BS_BootSig
        buf.putInt(volumeId)                                    // BS_VolID
        buf.put(volumeLabel.toByteArray(Charsets.US_ASCII))     // BS_VolLab (11 bytes)
        buf.put("FAT32   ".toByteArray(Charsets.US_ASCII))      // BS_FilSysType (8 bytes)

        while (buf.position() < 510) buf.put(0)                 // boot code / padding
        buf.put(0x55.toByte())
        buf.put(0xAA.toByte())
        return buf.array()
    }

    fun buildFsInfo(sectorSize: Int, freeCount: Long, nextFree: Int): ByteArray {
        val buf = ByteBuffer.allocate(sectorSize).order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(FSINFO_LEAD_SIG)            // lead signature
        repeat(480) { buf.put(0) }            // reserved
        buf.putInt(FSINFO_STRUCT_SIG)          // struct signature
        buf.putInt(freeCount.toInt())          // FSI_Free_Count
        buf.putInt(nextFree)                   // FSI_Nxt_Free
        repeat(12) { buf.put(0) }             // reserved
        buf.putInt(FSINFO_TRAIL_SIG)           // trail signature (occupies offsets 508-511)
        return buf.array()
    }

    fun buildFatFirstSector(sectorSize: Int): ByteArray {
        val buf = ByteBuffer.allocate(sectorSize).order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(0x0FFFFFF8)   // FAT[0] media + EOC marker
        buf.putInt(0x0FFFFFFF)   // FAT[1] clean, no dirty flag
        buf.putInt(0x0FFFFFFF)   // FAT[2] root directory (cluster 2) end-of-chain
        return buf.array()
    }
}
