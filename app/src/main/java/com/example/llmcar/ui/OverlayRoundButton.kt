package com.example.llmcar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.llmcar.ui.theme.PaletteColors
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Круглая кнопка активации ассистента с тремя жестами:
 *
 *   - тап (без движения, < 1 сек)        → onTap (открыть оверлей ввода)
 *   - удержание 1 сек без движения        → onStartDrag (перетаскивание включается)
 *   - удержание 2.5 сек без движения      → onShowIconPicker (диалог выбора иконки)
 *   - свайп > 12 px сразу                → сразу drag, не ждём секунду
 *
 * Реальную позицию кнопки на экране задаёт WindowManager в
 * FloatingButtonService. Здесь мы только ловим жесты и отдаём наружу.
 */
@Composable
fun OverlayRoundButton(
    palette: PaletteColors,
    icon: ImageVector?,
    iconUri: String?,
    fallbackIcon: ImageVector,
    size: Dp = 64.dp,
    onTap: () -> Unit,
    onStartDrag: () -> Unit,
    onMoveDrag: (dx: Float, dy: Float) -> Unit,
    onEndDrag: () -> Unit,
    onShowIconPicker: () -> Unit
) {
    val scope = rememberCoroutineScope()

    // Таймеры удержания. Каждый взводится на ACTION_DOWN, отменяется
    // при движении или поднятии пальца.
    var longPressJob: Job? = null
    var veryLongPressJob: Job? = null

    // Текущее состояние жеста
    var isDragging by remember { mutableStateOf(false) }
    var movedFromDown by remember { mutableStateOf(false) }
    var downPosition by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = Modifier
            .size(size + 16.dp)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = { offset ->
                        downPosition = offset
                        isDragging = false
                        movedFromDown = false

                        // Отменяем предыдущие таймеры (если были)
                        longPressJob?.cancel()
                        veryLongPressJob?.cancel()

                        // Таймер 1: удержание 1 сек → включить drag
                        longPressJob = scope.launch {
                            delay(1000L)
                            // Если за это время не сдвинулись — включаем режим drag
                            if (!movedFromDown && !isDragging) {
                                isDragging = true
                                onStartDrag()
                            }
                        }

                        // Таймер 2: удержание 2.5 сек → выбор иконки
                        veryLongPressJob = scope.launch {
                            delay(2500L)
                            // Если всё ещё держим без движения — диалог иконки
                            if (!movedFromDown && !isDragging) {
                                longPressJob?.cancel()
                                onShowIconPicker()
                            }
                        }

                        // Ждём release — если палец ушёл, отменяем таймеры
                        tryAwaitRelease()

                        val wasDragging = isDragging
                        val wasMoved = movedFromDown

                        longPressJob?.cancel()
                        veryLongPressJob?.cancel()
                        longPressJob = null
                        veryLongPressJob = null

                        if (wasDragging) {
                            isDragging = false
                            onEndDrag()
                        } else if (!wasMoved) {
                            onTap()
                        }
                        movedFromDown = false
                    }
                )
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = {
                        movedFromDown = true
                        // Если drag начался раньше 1 сек — отменяем «долгое удержание»
                        longPressJob?.cancel()
                        veryLongPressJob?.cancel()
                        isDragging = true
                        onStartDrag()
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        onMoveDrag(dragAmount.x, dragAmount.y)
                    },
                    onDragEnd = {
                        isDragging = false
                        onEndDrag()
                    },
                    onDragCancel = {
                        isDragging = false
                        onEndDrag()
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        // Внешнее свечение
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
                ),
            contentAlignment = Alignment.Center
        ) {
            ActivationIconView(
                iconUri = iconUri,
                vector = icon,
                fallbackVector = fallbackIcon,
                tint = palette.accentSoft,
                size = size * 0.48f
            )
        }
    }
}
