package dev.kilo.usbotg.formatters

import dev.kilo.usbotg.model.FsType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import me.jahnen.libaums.core.driver.BlockDeviceDriver
import dev.kilo.usbotg.formatters.fat32.Fat32Layout
import dev.kilo.usbotg.formatters.fat32.Fat32Structures
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Random

class Fat32Formatter : Formatter {

    override val fsType: FsType = FsType.FAT32

    override fun format(block: BlockDeviceDriver, params: FormatParams): Flow<FormatEvent> = flow {
        var stage = "Initializing"
        try {
            val sectorSize = block.blockSize
            val totalSectors = block.blocks

            stage = "Computing FAT32 layout"
            emit(FormatEvent.Progress(2, stage))
            val layout = try {
                Fat32Layout.compute(totalSectors, sectorSize, params.clusterSizeSectors)
            } catch (e: Exception) {
                emit(FormatEvent.Failure(2, stage, e.message ?: "Unsupported drive for FAT32"))
                return@flow
            }

            val volumeLabel = Fat32Structures.normalizeVolumeLabel(params.volumeLabel)
            val volumeId = Random().nextInt()

            val boot = Fat32Structures.buildBootSector(
                sectorSize = sectorSize,
                sectorsPerCluster = layout.sectorsPerCluster,
                reservedSectors = layout.reservedSectors,
                numFats = layout.numFats,
                totalSectors = totalSectors,
                fatSize = layout.fatSize,
                fsinfoSector = layout.fsinfoSector,
                backupBootSector = layout.backupBootSector,
                volumeLabel = volumeLabel,
                volumeId = volumeId
            )
            val fsinfo = Fat32Structures.buildFsInfo(sectorSize, 0xFFFFFFFFL, 2)
            val fatHead = Fat32Structures.buildFatFirstSector(sectorSize)

            stage = "Writing boot sector"
            emit(FormatEvent.Progress(10, stage))
            block.write(0, wrap(boot))

            stage = "Writing FSInfo sector"
            emit(FormatEvent.Progress(20, stage))
            block.write(layout.fsinfoSector.toLong(), wrap(fsinfo))

            stage = "Writing backup boot sector"
            emit(FormatEvent.Progress(25, stage))
            block.write(layout.backupBootSector.toLong(), wrap(boot))

            stage = "Writing FAT (1/2)"
            emit(FormatEvent.Progress(35, stage))
            block.write(layout.fat1Lba, wrap(fatHead))
            writeZeros(block, layout.fat1Lba + 1, layout.fatSize - 1, sectorSize)

            stage = "Writing FAT (2/2)"
            emit(FormatEvent.Progress(55, stage))
            block.write(layout.fat2Lba, wrap(fatHead))
            writeZeros(block, layout.fat2Lba + 1, layout.fatSize - 1, sectorSize)

            stage = "Writing empty root directory"
            emit(FormatEvent.Progress(75, stage))
            writeZeros(block, layout.rootDirLba, layout.sectorsPerCluster.toLong(), sectorSize)

            stage = "Verifying written data"
            emit(FormatEvent.Progress(90, stage))
            if (!verify(block, layout, fatHead)) {
                emit(FormatEvent.Failure(90, stage, "Write verification failed: read-back mismatch"))
                return@flow
            }

            emit(FormatEvent.Success(100, "FAT32 format complete"))
        } catch (e: Exception) {
            emit(FormatEvent.Failure(0, stage, e.message ?: e.toString()))
        }
    }

    private fun wrap(bytes: ByteArray): ByteBuffer =
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

    private fun writeZeros(block: BlockDeviceDriver, startLba: Long, sectorCount: Long, sectorSize: Int) {
        if (sectorCount <= 0) return
        var remaining = sectorCount
        var lba = startLba
        while (remaining > 0) {
            val n = minOf(remaining, 2048L)
            val buf = ByteBuffer.allocate((n * sectorSize).toInt())
            block.write(lba, buf)
            lba += n
            remaining -= n
        }
    }

    private fun verify(block: BlockDeviceDriver, layout: Fat32Layout, fatHead: ByteArray): Boolean {
        val sectorSize = block.blockSize

        val boot = readSector(block, 0, sectorSize)
        if (boot[510] != 0x55.toByte() || boot[511] != 0xAA.toByte()) return false
        if (!String(boot.sliceArray(82..89), Charsets.US_ASCII).startsWith("FAT32")) return false

        val fs = readSector(block, layout.fsinfoSector.toLong(), sectorSize)
        val fsBuf = ByteBuffer.wrap(fs).order(ByteOrder.LITTLE_ENDIAN)
        if (fsBuf.getInt(0) != Fat32Structures.FSINFO_LEAD_SIG) return false
        if (fsBuf.getInt(508) != Fat32Structures.FSINFO_TRAIL_SIG) return false

        val f1 = readSector(block, layout.fat1Lba, sectorSize)
        val f2 = readSector(block, layout.fat2Lba, sectorSize)
        if (!f1.contentEquals(fatHead)) return false
        if (!f2.contentEquals(fatHead)) return false

        return true
    }

    private fun readSector(block: BlockDeviceDriver, lba: Long, sectorSize: Int): ByteArray {
        val buf = ByteBuffer.allocate(sectorSize).order(ByteOrder.LITTLE_ENDIAN)
        block.read(lba, buf)
        return buf.array()
    }
}
