package com.lumiposture.app.domain.usecase

import javax.inject.Inject

class CalculateBaselineCalibrationUseCase @Inject constructor() {

    fun computeMedianIpd(samples: List<Float>): Float? {
        val valid = samples.asSequence().filter { it.isFinite() && it > 10f }.sorted().toList()
        if (valid.size < 20) return null
        if (valid[valid.size * 9 / 10] - valid[valid.size / 10] > valid[valid.size / 2] * 0.2f) return null
        val mid = valid.size / 2
        return if ((valid.size % 2) == 0) {
            (valid[mid - 1] + valid[mid]) / 2f
        } else {
            valid[mid]
        }
    }
}
