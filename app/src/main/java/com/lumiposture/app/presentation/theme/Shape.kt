package com.lumiposture.app.presentation.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val PillShape = CircleShape
val GlassCardShape = RoundedCornerShape(24.dp)
val InnerTileShape = RoundedCornerShape(16.dp)
val SheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

val LumiShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = InnerTileShape,
    large = GlassCardShape,
    extraLarge = RoundedCornerShape(28.dp),
)
