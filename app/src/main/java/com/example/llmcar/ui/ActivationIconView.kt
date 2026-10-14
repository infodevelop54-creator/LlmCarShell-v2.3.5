package com.example.llmcar.ui

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream

/**
 * Универсальный рендер иконки кнопки активации.
 *
 * 1) Если iconUri != null и валиден — грузит локальную картинку и рендерит её.
 * 2) Иначе, если vector != null — рендерит вектор (Material Icon).
 * 3) Иначе — рендерит fallbackVector (передаётся как обязательный).
 */
@Composable
fun ActivationIconView(
    iconUri: String?,
    vector: ImageVector?,
    fallbackVector: ImageVector,
    tint: Color,
    size: Dp,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var bitmap by remember(iconUri) { mutableStateOf<android.graphics.Bitmap?>(null) }

    LaunchedEffect(iconUri) {
        bitmap = null
        if (iconUri.isNullOrBlank()) return@LaunchedEffect
        bitmap = withContext(Dispatchers.IO) {
            try {
                val uri = Uri.parse(iconUri)
                val stream: InputStream? = when (uri.scheme) {
                    "content", "file" -> context.contentResolver.openInputStream(uri)
                    else -> null
                }
                stream?.use { BitmapFactory.decodeStream(it) }
            } catch (_: Exception) {
                null
            }
        }
    }

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        val bmp = bitmap
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape)
            )
        } else {
            Icon(
                imageVector = vector ?: fallbackVector,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(size)
            )
        }
    }
}
