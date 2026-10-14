package com.example.llmcar.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.llmcar.data.db.HistoryEntity
import com.example.llmcar.ui.theme.PaletteColors
import com.example.llmcar.voice.VoiceEvent
import com.example.llmcar.voice.VoiceInputController
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Оверлей ввода запроса + градиентное поле с историей.
 *
 * Состав сверху вниз:
 *   1) ChatHintBox    — градиентное поле с последними N сообщениями
 *   2) InputPill      — пилюля ввода с иконками «сообщения», «микрофон», «отправить»
 *
 * Поведение:
 *   - открывается по тапу на круглой кнопке;
 *   - при появлении сразу запускает микрофон;
 *   - тап по полю ввода глушит микрофон и включает клавиатуру;
 *   - тап по полю истории или по иконке «сообщения» открывает ChatScreen;
 *   - долгий тап (1 сек) на сообщении в истории → DropdownMenu «Отправить запрос»;
 *   - тап вне оверлея — закрывает.
 */
@Composable
fun OverlayInputOverlay(
    palette: PaletteColors,
    pillHeightDp: Int = 56,
    historyHeightPercent: Int = 35,
    history: List<HistoryEntity>,
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
    val historyListState = rememberLazyListState()

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
                            stopListening()
                        }
                    }
                    is VoiceEvent.Error -> stopListening()
                }
            }
        }
    }

    // Автостарт микрофона при появлении оверлея
    LaunchedEffect(Unit) { startListening() }

    // Автоскролл истории вниз при её изменении
    LaunchedEffect(history.size) {
        if (history.isNotEmpty()) {
            historyListState.animateScrollToItem(history.size - 1)
        }
    }

    DisposableEffect(Unit) { onDispose { stopListening() } }

    // Высота истории от высоты экрана
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val screenHeightDp = configuration.screenHeightDp
    val historyHeightDp = (screenHeightDp * historyHeightPercent / 100).dp

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ============ Градиентное поле с историей ============
        if (history.isNotEmpty() && historyHeightPercent > 0) {
            ChatHintBox(
                palette = palette,
                heightDp = historyHeightDp,
                history = history,
                listState = historyListState,
                onMessageLongPress = { entity ->
                    // Нажатие на сообщение → в поле ввода оверлея
                    text = entity.userText
                },
                onOpenChat = onOpenChat
            )
            Spacer(Modifier.height(6.dp))
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
                // ---- Поле ввода ----
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

                // ---- Иконка микрофона (индикатор) ----
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

                // ---- Иконка «сообщения» → открывает ChatScreen ----
                Box(
                    modifier = Modifier
                        .size((pillHeightDp - 20).dp)
                        .clip(CircleShape)
                        .clickable { onOpenChat() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Chat,
                        contentDescription = "Открыть чат",
                        tint = palette.accent.copy(alpha = 0.85f),
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

// =============================================================
// Градиентное поле с последними сообщениями чата
// =============================================================

@Composable
private fun ChatHintBox(
    palette: PaletteColors,
    heightDp: androidx.compose.ui.unit.Dp,
    history: List<HistoryEntity>,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onMessageLongPress: (HistoryEntity) -> Unit,
    onOpenChat: () -> Unit
) {
    // Держим открытое меню (какое сообщение выбрано)
    var menuForEntity by remember { mutableStateOf<HistoryEntity?>(null) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(heightDp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        palette.accent.copy(alpha = 0.20f),
                        palette.accent.copy(alpha = 0.55f),
                        palette.accent.copy(alpha = 0.80f)
                    )
                )
            )
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { onOpenChat() }
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(history) { entity ->
                ChatHintItem(
                    entity = entity,
                    palette = palette,
                    onLongPress = { menuForEntity = entity }
                )
            }
        }

        // DropdownMenu с «Отправить запрос»
        menuForEntity?.let { entity ->
            DropdownMenu(
                expanded = true,
                onDismissRequest = { menuForEntity = null }
            ) {
                DropdownMenuItem(
                    text = { Text("Отправить запрос") },
                    onClick = {
                        onMessageLongPress(entity)
                        menuForEntity = null
                    }
                )
            }
        }
    }
}

@Composable
private fun ChatHintItem(
    entity: HistoryEntity,
    palette: PaletteColors,
    onLongPress: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(palette.pillBgStart.copy(alpha = 0.55f))
            .pointerInput(entity.id) {
                detectTapGestures(
                    onLongPress = { onLongPress() }
                )
            }
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = "→ " + entity.userText,
            color = palette.accentSoft,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2
        )
        if (entity.assistantText.isNotBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = entity.assistantText,
                color = palette.inputText.copy(alpha = 0.85f),
                fontSize = 12.sp,
                maxLines = 4
            )
        }
    }
}
