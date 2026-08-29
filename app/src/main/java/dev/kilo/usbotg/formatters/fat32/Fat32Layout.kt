package dev.kilo.usbotg.formatters.fat32

data class Fat32Layout(
    val sectorSize: Int,
    val totalSectors: Long,
    val sectorsPerCluster: Int,
    val reservedSectors: Int,
    val numFats: Int,
    val rootCluster: Int,
    val fsinfoSector: Int,
    val backupBootSector: Int,
    val fatSize: Long,
    val fat1Lba: Long,
    val fat2Lba: Long,
    val rootDirLba: Long,
    val clusterCount: Long
) {
    companion object {
        const val MIN_CLUSTERS = 65525L
        const val MAX_CLUSTERS = 268435445L

        fun compute(
            totalSectors: Long,
            sectorSize: Int,
            clusterSizeSectors: Int? = null
        ): Fat32Layout {
            require(totalSectors > 0) { "Drive has no sectors" }
            require(sectorSize >= 512 && sectorSize <= 4096 && (sectorSize and (sectorSize - 1)) == 0) {
                "Unsupported sector size $sectorSize"
            }

            val reservedSectors = 32
            val numFats = 2
            var spc = (clusterSizeSectors ?: chooseSectorsPerCluster(totalSectors, sectorSize))
                .coerceAtLeast(1)

            var result: Fat32Layout? = null
            var guard = 0
            while (result == null && guard++ < 32) {
                val tmp1 = totalSectors - reservedSectors
                val tmp2Half = ((256 * spc) + numFats) / 2
                val fatSize = (tmp1 + (tmp2Half - 1)) / tmp2Half
                val clusters = (totalSectors - (reservedSectors + numFats * fatSize)) / spc

                result = when {
                    clusters in MIN_CLUSTERS..MAX_CLUSTERS ->
                        build(sectorSize, totalSectors, spc, fatSize, clusters, reservedSectors, numFats)
                    clusters < MIN_CLUSTERS -> {
                        if (spc <= 1) error("Drive too small for FAT32 (needs at least $MIN_CLUSTERS clusters)")
                        spc /= 2
                        null
                    }
                    else -> {
                        if (spc >= 128) error("Drive too large for FAT32 (exceeds $MAX_CLUSTERS clusters)")
                        spc *= 2
                        null
                    }
                }
            }
            return result ?: error("Unable to compute a valid FAT32 layout")
        }

        private fun build(
            sectorSize: Int,
            totalSectors: Long,
            spc: Int,
            fatSize: Long,
            clusters: Long,
            reserved: Int,
            numFats: Int
        ): Fat32Layout {
            val fat1Lba = reserved.toLong()
            val fat2Lba = fat1Lba + fatSize
            val rootDirLba = fat2Lba + fatSize
            return Fat32Layout(
                sectorSize = sectorSize,
                totalSectors = totalSectors,
                sectorsPerCluster = spc,
                reservedSectors = reserved,
                numFats = numFats,
                rootCluster = 2,
                fsinfoSector = 1,
                backupBootSector = 6,
                fatSize = fatSize,
                fat1Lba = fat1Lba,
                fat2Lba = fat2Lba,
                rootDirLba = rootDirLba,
                clusterCount = clusters
            )
        }

        fun chooseSectorsPerCluster(totalSectors: Long, sectorSize: Int): Int {
            val sizeMB = totalSectors * sectorSize / (1024 * 1024)
            return when {
                sizeMB < 64 -> 1
                sizeMB < 128 -> 2
                sizeMB < 256 -> 4
                sizeMB < 512 -> 8
                sizeMB < 1024 -> 16
                sizeMB < 2048 -> 32
                else -> 64
            }
        }
    }
}
