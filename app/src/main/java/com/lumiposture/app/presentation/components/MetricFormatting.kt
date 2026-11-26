package com.lumiposture.app.presentation.components

import java.util.Locale

fun Float?.metric(unit: String, decimals: Int = 1): String =
    if (this == null || !isFinite()) "Tidak tersedia" else String.format(Locale.US, "%.${decimals}f %s", this, unit)
