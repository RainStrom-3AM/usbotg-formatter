package dev.kilo.usbotg.formatters

import me.jahnen.libaums.core.driver.BlockDeviceDriver
import java.nio.ByteBuffer

class FakeBlockDevice(
    private val sectorSize: Int,
    private val sectorCount: Long
) : BlockDeviceDriver {

    private val data = ByteArray((sectorCount * sectorSize).toInt())

    override val blockSize: Int = sectorSize
    override val blocks: Long = sectorCount

    override fun init() {}

    override fun read(deviceOffset: Long, buffer: ByteBuffer) {
        val srcOff = (deviceOffset * sectorSize).toInt()
        val len = buffer.remaining()
        buffer.put(data, srcOff, len)
    }

    override fun write(deviceOffset: Long, buffer: ByteBuffer) {
        val dstOff = (deviceOffset * sectorSize).toInt()
        val len = buffer.remaining()
        buffer.get(data, dstOff, len)
    }

    fun sector(lba: Long): ByteArray {
        val start = (lba * sectorSize).toInt()
        return data.copyOfRange(start, start + sectorSize)
    }
}
