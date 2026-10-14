package com.example.llmcar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.llmcar.ui.theme.PaletteColors

/**
 * Круглая кнопка активации ассистента.
 * Цвет и иконка берутся из настроек (accentHue / presetIconStyle) —
 * то есть те же, что у оверлея и полей ввода.
 *
 * Позиционирование делает WindowManager (см. FloatingButtonService),
 * поэтому внутри нет Modifier.offset или fillMaxSize.
 */
@Composable
fun OverlayRoundButton(
    palette: PaletteColors,
    icon: ImageVector,
    size: Dp = 64.dp,
    onClick: () -> Unit
) {
    // Внешнее свечение
    Box(
        modifier = Modifier.size(size + 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(size + 16.dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            palette.glow,
                            palette.glow.copy(alpha = 0.25f),
                            Color.Transparent
                        )
                    ),
                    shape = CircleShape
                )
        )
        // Сама кнопка
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(palette.btnInner, palette.btnOuter)
                    )
                )
                .border(
                    width = 1.dp,
                    color = palette.pillBorderHot,
                    shape = CircleShape
                )
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = "Открыть ассистента",
                tint = palette.accentSoft,
                modifier = Modifier.size(size * 0.42f)
            )
        }
    }
}
