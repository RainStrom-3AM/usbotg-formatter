package dev.kilo.usbotg.formatters.fat32

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class Fat32StructuresTest {

    private fun intAt(bytes: ByteArray, offset: Int): Int =
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).getInt(offset)

    @Test
    fun `boot sector has 55AA signature and FAT32 label`() {
        val boot = Fat32Structures.buildBootSector(
            sectorSize = 512, sectorsPerCluster = 1, reservedSectors = 32, numFats = 2,
            totalSectors = 131072, fatSize = 1000, fsinfoSector = 1, backupBootSector = 6,
            volumeLabel = "TESTVOL", volumeId = 0x12345678
        )
        assertEquals(0x55.toByte(), boot[510])
        assertEquals(0xAA.toByte(), boot[511])
        assertEquals("FAT32   ", String(boot.sliceArray(82..89), Charsets.US_ASCII))
        assertEquals("MSDOS5.0", String(boot.sliceArray(3..10), Charsets.US_ASCII))
        assertEquals(512, intAt(boot, 11))
        assertEquals(2, boot[16].toInt() and 0xFF)
        assertEquals(2, intAt(boot, 44))
    }

    @Test
    fun `fsinfo sector has correct signatures`() {
        val fs = Fat32Structures.buildFsInfo(512, 0xFFFFFFFFL, 2)
        assertEquals(Fat32Structures.FSINFO_LEAD_SIG, intAt(fs, 0))
        assertEquals(Fat32Structures.FSINFO_STRUCT_SIG, intAt(fs, 484))
        assertEquals(Fat32Structures.FSINFO_TRAIL_SIG, intAt(fs, 508))
    }

    @Test
    fun `FAT first sector has media and root EOC entries`() {
        val fat = Fat32Structures.buildFatFirstSector(512)
        assertEquals(0x0FFFFFF8, intAt(fat, 0))
        assertEquals(0x0FFFFFFF, intAt(fat, 4))
        assertEquals(0x0FFFFFFF, intAt(fat, 8))
    }

    @Test
    fun `volume label normalization pads to 11 and uppercases`() {
        assertEquals("NO NAME   ", Fat32Structures.normalizeVolumeLabel(null))
        assertEquals("TESTVOL    ", Fat32Structures.normalizeVolumeLabel("testvol"))
        assertEquals("AB CD      ", Fat32Structures.normalizeVolumeLabel("ab*cd"))
        assertEquals("TOOLONGNAM", Fat32Structures.normalizeVolumeLabel("TOOLONGNAMEEXTRA"))
    }

    @Test
    fun `boot and fsinfo lengths match sector size`() {
        val boot = Fat32Structures.buildBootSector(
            sectorSize = 512, sectorsPerCluster = 1, reservedSectors = 32, numFats = 2,
            totalSectors = 131072, fatSize = 1000, fsinfoSector = 1, backupBootSector = 6,
            volumeLabel = "X", volumeId = 1
        )
        assertEquals(512, boot.size)
        assertArrayEquals(boot, Fat32Structures.buildBootSector(
            sectorSize = 512, sectorsPerCluster = 1, reservedSectors = 32, numFats = 2,
            totalSectors = 131072, fatSize = 1000, fsinfoSector = 1, backupBootSector = 6,
            volumeLabel = "X", volumeId = 1
        ))
    }
}
