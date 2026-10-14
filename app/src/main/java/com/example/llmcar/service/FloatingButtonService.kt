package com.example.llmcar.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.llmcar.LlmCarApp
import com.example.llmcar.MainActivity
import com.example.llmcar.R
import com.example.llmcar.data.HistoryRepository
import com.example.llmcar.data.SettingsRepository
import com.example.llmcar.data.db.HistoryEntity
import com.example.llmcar.ui.ActivationIcon
import com.example.llmcar.ui.ActivationIconPickerDialog
import com.example.llmcar.ui.ChatViewModel
import com.example.llmcar.ui.OverlayInputOverlay
import com.example.llmcar.ui.OverlayRoundButton
import com.example.llmcar.ui.theme.VisualTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * v2.3.5 — круглая кнопка активации + оверлей ввода с градиентной историей.
 *
 * Два окна WindowManager:
 *   1) pillView    — круглая кнопка (drag через долгое удержание 1 сек,
 *                    смена иконки через удержание 2.5 сек, тап — открыть оверлей);
 *   2) overlayView — оверлей ввода + градиентное поле с историей.
 *
 * История берётся из Room (HistoryRepository.observe()).
 */
class FloatingButtonService : Service(), SavedStateRegistryOwner, LifecycleOwner {

    private lateinit var wm: WindowManager
    private lateinit var settings: SettingsRepository
    private lateinit var historyRepo: HistoryRepository
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // --------- Круглая кнопка ---------
    private var pillView: View? = null
    private lateinit var pillParams: WindowManager.LayoutParams

    // --------- Оверлей ввода ---------
    private var overlayView: View? = null
    private lateinit var overlayParams: WindowManager.LayoutParams

    // --------- История для оверлея ---------
    private val _history = MutableStateFlow<List<HistoryEntity>>(emptyList())
    private val historyFlow: StateFlow<List<HistoryEntity>> = _history.asStateFlow()
    private var historyJob: Job? = null

    // --------- Иконка кнопки (для перерисовки) ---------
    private var iconPickerVisible by mutableStateOf(false)

    // --------- Позиция кнопки ---------
    private var currentPosX: Int = 0
    private var currentPosY: Int = 0

    private val vmStore = ViewModelStore()
    private val vmFactory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ChatViewModel(application) as T
        }
    }
    private val chatVm: ChatViewModel by lazy {
        ViewModelProvider(vmStore, vmFactory)[ChatViewModel::class.java]
    }

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateController.savedStateRegistry

    override fun onCreate() {
        super.onCreate()
        savedStateController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
        startForeground(NOTIF_ID, notif())
        settings = SettingsRepository(this)
        historyRepo = HistoryRepository(this)
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        currentPosX = settings.overlayPosX
        currentPosY = settings.overlayPosY
        attachPill()
        startHistoryObserver()
    }

    // ============================================================
    // История из Room
    // ============================================================

    private fun startHistoryObserver() {
        historyJob = scope.launch {
            historyRepo.observe().collect { list ->
                // Берём только последние 20 записей
                _history.value = list.take(20)
            }
        }
    }

    // ============================================================
    // Окно 1: круглая кнопка
    // ============================================================

    @SuppressLint("ClickableViewAccessibility")
    private fun attachPill() {
        pillParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = currentPosX
            y = currentPosY
        }

        val cv = ComposeView(this)
        cv.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
        cv.setViewTreeLifecycleOwner(this)
        cv.setViewTreeSavedStateRegistryOwner(this)
        cv.setContent {
            PillRoot(
                settings = settings,
                showIconPicker = iconPickerVisible,
                onIconPickerDismiss = { iconPickerVisible = false },
                onTap = { showInputOverlay() },
                onStartDrag = { /* пока ничего */ },
                onMoveDrag = { dx, dy -> movePillBy(dx, dy) },
                onEndDrag = {
                    settings.setOverlayPosX(currentPosX)
                    settings.setOverlayPosY(currentPosY)
                },
                onShowIconPicker = { iconPickerVisible = true }
            )
        }
        pillView = cv
        wm.addView(pillView, pillParams)
    }

    private fun movePillBy(dx: Float, dy: Float) {
        val v = pillView ?: return
        currentPosX += dx.toInt()
        currentPosY += dy.toInt()
        pillParams.x = currentPosX
        pillParams.y = currentPosY
        try {
            wm.updateViewLayout(v, pillParams)
        } catch (_: Exception) {
        }
    }

    // ============================================================
    // Окно 2: оверлей ввода
    // ============================================================

    private fun showInputOverlay() {
        if (overlayView != null) return

        overlayParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }

        val cv = ComposeView(this)
        cv.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
        cv.setViewTreeLifecycleOwner(this)
        cv.setViewTreeSavedStateRegistryOwner(this)
        cv.setContent {
            OverlayRoot(
                settings = settings,
                historyFlow = historyFlow,
                onSend = { text ->
                    chatVm.send(text)
                    hideInputOverlay()
                },
                onOpenChat = {
                    hideInputOverlay()
                    openChat()
                },
                onClose = { hideInputOverlay() }
            )
        }
        overlayView = cv
        wm.addView(overlayView, overlayParams)
    }

    private fun hideInputOverlay() {
        val v = overlayView ?: return
        overlayView = null
        try {
            wm.removeView(v)
        } catch (_: Exception) {
        }
    }

    private fun openChat() {
        val i = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(MainActivity.EXTRA_SCROLL_TO_BOTTOM, true)
        }
        try {
            startActivity(i)
        } catch (_: Exception) {
        }
    }

    // ============================================================
    // Вспомогательное
    // ============================================================

    private fun overlayType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE

    private fun notif(): Notification {
        val pi = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, LlmCarApp.CHANNEL_FLOATING)
            .setContentTitle(getString(R.string.floating_notification_title))
            .setContentText(getString(R.string.floating_notification_text))
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        historyJob?.cancel()
        scope.cancel()
        hideInputOverlay()
        val v = pillView
        if (v != null) {
            try { wm.removeView(v) } catch (_: Exception) {}
        }
        pillView = null
        vmStore.clear()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val NOTIF_ID = 1002
    }
}

// =============================================================
// Compose: круг + диалог выбора иконки
// =============================================================

@Composable
private fun PillRoot(
    settings: SettingsRepository,
    showIconPicker: Boolean,
    onIconPickerDismiss: () -> Unit,
    onTap: () -> Unit,
    onStartDrag: () -> Unit,
    onMoveDrag: (Float, Float) -> Unit,
    onEndDrag: () -> Unit,
    onShowIconPicker: () -> Unit
) {
    val palette = VisualTheme.palette(settings.accentHue)
    val fallbackIcon = VisualTheme.presetIcon(settings.presetIconStyle)
    val chosenVector = ActivationIcon.vector(settings.activationIconName)

    OverlayRoundButton(
        palette = palette,
        icon = chosenVector,
        iconUri = settings.activationIconUri,
        fallbackIcon = fallbackIcon,
        onTap = onTap,
        onStartDrag = onStartDrag,
        onMoveDrag = onMoveDrag,
        onEndDrag = onEndDrag,
        onShowIconPicker = onShowIconPicker
    )

    if (showIconPicker) {
        ActivationIconPickerDialog(
            palette = palette,
            currentVectorName = settings.activationIconName,
            currentUri = settings.activationIconUri,
            onSelectVector = { name ->
                settings.setActivationIconName(name)
                settings.setActivationIconUri(null)
                onIconPickerDismiss()
            },
            onSelectUri = { uri ->
                settings.setActivationIconUri(uri)
                settings.setActivationIconName(null)
                onIconPickerDismiss()
            },
            onClear = {
                settings.setActivationIconName(null)
                settings.setActivationIconUri(null)
                onIconPickerDismiss()
            },
            onDismiss = onIconPickerDismiss
        )
    }
}

// =============================================================
// Compose: корень оверлея ввода (прозрачная подложка + контент снизу)
// =============================================================

@Composable
private fun OverlayRoot(
    settings: SettingsRepository,
    historyFlow: StateFlow<List<HistoryEntity>>,
    onSend: (String) -> Unit,
    onOpenChat: () -> Unit,
    onClose: () -> Unit
) {
    val palette = VisualTheme.palette(settings.accentHue)
    val pillHeightDp = VisualTheme.pillHeight(settings.pillSize)
    val history by historyFlow.collectAsState()

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClose
            ),
        contentAlignment = Alignment.BottomCenter
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = { /* проглатываем */ }
                )
        ) {
            OverlayInputOverlay(
                palette = palette,
                pillHeightDp = pillHeightDp,
                historyHeightPercent = settings.overlayChatHeightPercent,
                history = history,
                onSend = onSend,
                onOpenChat = onOpenChat,
                onClose = onClose
            )
        }
    }
}
