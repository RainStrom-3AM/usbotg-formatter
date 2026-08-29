package dev.kilo.usbotg.model

enum class FsType(val label: String, val beta: Boolean = false) {
    FAT16("FAT16"),
    FAT32("FAT32"),
    EXFAT("exFAT"),
    NTFS("NTFS", beta = true),
    EXT4("ext4", beta = true),
    UNKNOWN("Unknown");

    val isSupported: Boolean
        get() = this != UNKNOWN
}
