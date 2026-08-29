package dev.kilo.usbotg.formatters

import dev.kilo.usbotg.formatters.fat32.Fat32Structures
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class Fat32FormatterTest {

    private fun sectorsForMB(mb: Int) = (mb.toLong() * 1024 * 1024) / 512

    @Test
    fun `formatting a fake 64MB drive reports success and writes valid structures`() = runBlocking {
        val dev = FakeBlockDevice(sectorSize = 512, sectorCount = sectorsForMB(64))
        val formatter = Fat32Formatter()

        val events = formatter.format(dev, FormatParams(volumeLabel = "TESTVOL")).toList()
        val last = events.last()

        assertTrue("Expected success, got $last", last is FormatEvent.Success)

        val boot = dev.sector(0)
        assertEquals(0x55.toByte(), boot[510])
        assertEquals(0xAA.toByte(), boot[511])
        assertEquals("FAT32   ", String(boot.sliceArray(82..89), Charsets.US_ASCII))
        assertEquals("TESTVOL    ", String(boot.sliceArray(71..81), Charsets.US_ASCII))

        val fs = dev.sector(1)
        assertEquals(
            Fat32Structures.FSINFO_LEAD_SIG,
            ByteBuffer.wrap(fs).order(ByteOrder.LITTLE_ENDIAN).getInt(0)
        )

        val fat1 = dev.sector(32)
        val fat2 = dev.sector(32 + (fatSizeSectors(dev)))
        assertTrue(fat1.contentEquals(fat2))
        assertEquals(0x0FFFFFF8, ByteBuffer.wrap(fat1).order(ByteOrder.LITTLE_ENDIAN).getInt(0))
    }

    private fun fatSizeSectors(dev: FakeBlockDevice): Long {
        val boot = dev.sector(0)
        val buf = ByteBuffer.wrap(boot).order(ByteOrder.LITTLE_ENDIAN)
        return buf.getInt(36).toLong() and 0xFFFFFFFFL
    }

    @Test
    fun `formatting emits ordered progress then success`() = runBlocking {
        val dev = FakeBlockDevice(sectorSize = 512, sectorCount = sectorsForMB(64))
        val events = Fat32Formatter().format(dev, FormatParams()).toList()
        assertTrue(events.first() is FormatEvent.Progress)
        assertTrue(events.last() is FormatEvent.Success)
    }
}
