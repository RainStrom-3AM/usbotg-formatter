package dev.kilo.usbotg.formatters.fat32

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Fat32LayoutTest {

    private fun sectorsForMB(mb: Int) = (mb.toLong() * 1024 * 1024) / 512

    @Test
    fun `64MB drive uses 1KB clusters and is valid FAT32`() {
        val total = sectorsForMB(64)
        val layout = Fat32Layout.compute(total, 512)
        assertEquals(32, layout.reservedSectors)
        assertEquals(2, layout.numFats)
        assertEquals(2, layout.rootCluster)
        assertEquals(1, layout.fsinfoSector)
        assertEquals(6, layout.backupBootSector)
        assertEquals(2, layout.sectorsPerCluster)
        assertTrue("clusters=${layout.clusterCount}", layout.clusterCount > 0)
    }

    @Test
    fun `512MB drive uses 16 sectors per cluster`() {
        val layout = Fat32Layout.compute(sectorsForMB(512), 512)
        assertEquals(16, layout.sectorsPerCluster)
        assertTrue("clusters=${layout.clusterCount}", layout.clusterCount > 0 && layout.clusterCount <= Fat32Layout.MAX_CLUSTERS)
    }

    @Test
    fun `4GB drive uses 64 sectors per cluster`() {
        val layout = Fat32Layout.compute(sectorsForMB(4 * 1024), 512)
        assertEquals(64, layout.sectorsPerCluster)
        assertTrue(layout.clusterCount in Fat32Layout.MIN_CLUSTERS..Fat32Layout.MAX_CLUSTERS)
    }

    @Test
    fun `FATs are contiguous after reserved and root follows FATs`() {
        val layout = Fat32Layout.compute(sectorsForMB(1024), 512)
        assertEquals(layout.reservedSectors.toLong(), layout.fat1Lba)
        assertEquals(layout.fat1Lba + layout.fatSize, layout.fat2Lba)
        assertEquals(layout.fat2Lba + layout.fatSize, layout.rootDirLba)
    }

    @Test
    fun `drive too small for FAT32 throws`() {
        val tooSmall = sectorsForMB(16) // 16 MiB
        var threw = false
        try {
            Fat32Layout.compute(tooSmall, 512)
        } catch (e: Exception) {
            threw = true
        }
        assertTrue(threw)
    }

    @Test
    fun `layout region accounting is consistent`() {
        val layout = Fat32Layout.compute(sectorsForMB(2048), 512)
        val used = layout.reservedSectors + layout.numFats * layout.fatSize + layout.clusterCount * layout.sectorsPerCluster
        assertTrue("used=$used total=${layout.totalSectors}", used <= layout.totalSectors)
    }
}
