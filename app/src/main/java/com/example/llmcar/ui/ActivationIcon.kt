package com.example.llmcar.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assistant
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Rocket
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Star
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Набор предустановленных векторных иконок для кнопки активации.
 * Порядок отображения — как в списке ниже.
 */
object ActivationIcon {

    /** Пары (имя, вектор). Имя сохраняется в SettingsRepository. */
    val all: List<Pair<String, ImageVector>> = listOf(
        "AutoAwesome" to Icons.Default.AutoAwesome,
        "Assistant"   to Icons.Default.Assistant,
        "SmartToy"    to Icons.Default.SmartToy,
        "Psychology"  to Icons.Default.Psychology,
        "Face"        to Icons.Default.Face,
        "Rocket"      to Icons.Default.Rocket,
        "Bolt"        to Icons.Default.Bolt,
        "Chat"        to Icons.Default.Chat,
        "Forum"       to Icons.Default.Forum,
        "Star"        to Icons.Default.Star,
        "Mic"         to Icons.Default.Mic,
        "Build"       to Icons.Default.Build,
        "Map"         to Icons.Default.Map,
        "MusicNote"   to Icons.Default.MusicNote,
        "GridView"    to Icons.Default.GridView
    )

    /** Вектор по имени. null, если имя не найдено (тогда используется presetIconStyle). */
    fun vector(name: String?): ImageVector? =
        if (name == null) null
        else all.firstOrNull { it.first == name }?.second

    /** Все доступные имена (для UI). */
    fun names(): List<String> = all.map { it.first }
}
