package dev.kilo.usbotg.formatters

import dev.kilo.usbotg.model.FsType
import kotlinx.coroutines.flow.Flow
import me.jahnen.libaums.core.driver.BlockDeviceDriver

data class FormatParams(
    val volumeLabel: String? = null,
    val clusterSizeSectors: Int? = null
)

sealed interface FormatEvent {
    val percent: Int
    val stage: String

    data class Progress(override val percent: Int, override val stage: String) : FormatEvent
    data class Success(override val percent: Int = 100, override val stage: String = "Done") : FormatEvent
    data class Failure(override val percent: Int, override val stage: String, val message: String) : FormatEvent
}

interface Formatter {
    val fsType: FsType
    fun format(block: BlockDeviceDriver, params: FormatParams = FormatParams()): Flow<FormatEvent>
}
