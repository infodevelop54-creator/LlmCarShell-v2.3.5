package com.example.llmcar.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.llmcar.ui.theme.PaletteColors
import com.example.llmcar.voice.VoiceEvent
import com.example.llmcar.voice.VoiceInputController
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Оверлей ввода запроса.
 *
 * Поведение:
 *  - выезжает снизу, без фона, не перекрывает страницу;
 *  - при появлении СРАЗУ запускает микрофон (ASR);
 *  - частичные результаты распознавания льются в поле текста;
 *  - тап по полю ввода — глушит микрофон, включает клавиатуру;
 *  - три полоски сверху — открывают страницу чата (onOpenChat);
 *  - кнопка «отправить» шлёт текст в LLM и закрывает оверлей.
 */
@Composable
fun OverlayInputOverlay(
    palette: PaletteColors,
    pillHeightDp: Int = 56,
    onSend: (String) -> Unit,
    onOpenChat: () -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var text by remember { mutableStateOf("") }
    var isListening by remember { mutableStateOf(false) }
    var voiceJob by remember { mutableStateOf<Job?>(null) }
    val hasText = text.isNotBlank()
    val radius = pillHeightDp / 2

    val controller = remember { VoiceInputController(context) }
    val focusRequester = remember { FocusRequester() }

    fun stopListening() {
        voiceJob?.cancel()
        voiceJob = null
        controller.stop()
        isListening = false
    }

    fun startListening() {
        if (isListening) return
        val hasMic = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasMic) return
        isListening = true
        voiceJob = scope.launch {
            controller.listenOnce().collect { ev ->
                when (ev) {
                    is VoiceEvent.ReadyForSpeech -> {}
                    is VoiceEvent.RmsChanged -> {}
                    is VoiceEvent.Partial -> {
                        if (ev.text.isNotBlank()) text = ev.text
                    }
                    is VoiceEvent.Final -> {
                        if (ev.text.isNotBlank()) {
                            text = ev.text
                            // Автоотправка не делаем — даём пользователю
                            // увидеть результат и нажать «отправить».
                            stopListening()
                        }
                    }
                    is VoiceEvent.Error -> stopListening()
                }
            }
        }
    }

    // Автостарт микрофона при появлении оверлея
    LaunchedEffect(Unit) {
        startListening()
    }

    DisposableEffect(Unit) {
        onDispose { stopListening() }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ============ Три полоски (ручка) ============
        Box(
            modifier = Modifier
                .width(72.dp)
                .height(28.dp)
                .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
                .background(palette.pillBgEnd.copy(alpha = 0.9f))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { onOpenChat() },
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                repeat(3) {
                    Box(
                        Modifier
                            .width(28.dp)
                            .height(2.dp)
                            .background(
                                palette.accent.copy(alpha = 0.85f),
                                RoundedCornerShape(1.dp)
                            )
                    )
                }
            }
        }

        // ============ Пилюля ввода ============
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(pillHeightDp.dp)
                .clip(RoundedCornerShape(radius.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(palette.pillBgStart, palette.pillBgEnd)
                    )
                )
                .border(
                    width = 1.dp,
                    brush = Brush.horizontalGradient(
                        listOf(
                            palette.pillBorderDim,
                            palette.pillBorderHot,
                            palette.pillBorderDim
                        )
                    ),
                    shape = RoundedCornerShape(radius.dp)
                )
        ) {
            Row(
                Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // ---- Поле ввода: при тапе глушим микрофон и вызываем клавиатуру ----
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) {
                            stopListening()
                            focusRequester.requestFocus()
                        },
                    contentAlignment = Alignment.CenterStart
                ) {
                    BasicTextField(
                        value = text,
                        onValueChange = { text = it },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 20.dp, end = 8.dp)
                            .focusRequester(focusRequester),
                        textStyle = TextStyle(
                            color = palette.inputText,
                            fontSize = (pillHeightDp * 0.28f).sp
                        ),
                        cursorBrush = SolidColor(palette.accent),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = {
                            val t = text.trim()
                            if (t.isNotBlank()) {
                                text = ""
                                onSend(t)
                            }
                        }),
                        decorationBox = { inner ->
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (text.isEmpty()) {
                                    Text(
                                        if (isListening) "Говорите…"
                                        else "Введите запрос…",
                                        color = palette.placeholder,
                                        fontSize = (pillHeightDp * 0.28f).sp
                                    )
                                }
                                inner()
                            }
                        }
                    )
                }

                // ---- Индикатор микрофона ----
                Box(
                    modifier = Modifier
                        .size((pillHeightDp - 20).dp)
                        .clip(CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isListening) Icons.Default.Mic
                                      else Icons.Default.MicOff,
                        contentDescription = null,
                        tint = if (isListening) palette.accent
                               else palette.placeholder.copy(alpha = 0.6f),
                        modifier = Modifier.size((pillHeightDp * 0.32f).dp)
                    )
                }

                // ---- Кнопка отправки ----
                Box(
                    modifier = Modifier
                        .padding(end = 6.dp)
                        .size((pillHeightDp - 12).dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                listOf(palette.btnInner, palette.btnOuter)
                            )
                        )
                        .clickable(enabled = hasText) {
                            val t = text.trim()
                            if (t.isNotBlank()) {
                                text = ""
                                onSend(t)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "Отправить",
                        tint = if (hasText) palette.accentSoft
                               else palette.accentSoft.copy(alpha = 0.4f),
                        modifier = Modifier.size((pillHeightDp * 0.34f).dp)
                    )
                }
            }
        }
    }
}
