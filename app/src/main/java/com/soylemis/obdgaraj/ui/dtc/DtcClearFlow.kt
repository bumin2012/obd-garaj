package com.soylemis.obdgaraj.ui.dtc

import com.soylemis.obdgaraj.obd.ClearConfirmation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ClearStep { IDLE, WARNING_SHOWN, CONFIRMED }

/**
 * Kod silme için iki adımlı onay: önce uyarı gösterilir, kullanıcı onaylarsa tek
 * kullanımlık [ClearConfirmation] üretilir. Mode 04 başka yoldan gönderilemez.
 */
class DtcClearFlow {
    private val _step = MutableStateFlow(ClearStep.IDLE)
    val step: StateFlow<ClearStep> = _step.asStateFlow()

    fun request() {
        _step.value = ClearStep.WARNING_SHOWN
    }

    fun confirm(): ClearConfirmation? {
        if (_step.value != ClearStep.WARNING_SHOWN) return null
        _step.value = ClearStep.CONFIRMED
        return ClearConfirmation()
    }

    fun cancel() {
        _step.value = ClearStep.IDLE
    }

    fun reset() {
        _step.value = ClearStep.IDLE
    }
}
