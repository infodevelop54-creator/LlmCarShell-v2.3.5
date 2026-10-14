package com.example.llmcar.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.llmcar.ui.theme.PaletteColors

/**
 * Диалог выбора иконки кнопки активации.
 *
 *  onSelectVector(name)  — пользователь выбрал готовую иконку.
 *  onSelectUri(uri)      — пользователь выбрал свою картинку.
 *  onClear               — сбросить на presetIconStyle.
 */
@Composable
fun ActivationIconPickerDialog(
    palette: PaletteColors,
    currentVectorName: String?,
    currentUri: String?,
    onSelectVector: (String) -> Unit,
    onSelectUri: (String) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            onSelectUri(uri.toString())
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.pillBgStart,
        title = {
            Text(
                "Иконка кнопки",
                color = palette.accentSoft,
                fontSize = 18.sp
            )
        },
        text = {
            Column(Modifier.fillMaxWidth()) {
                // ---- Сетка готовых иконок ----
                LazyVerticalGrid(
                    columns = GridCells.Fixed(5),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 260.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(ActivationIcon.all) { (name, vector) ->
                        val isSelected = currentVectorName == name && currentUri == null
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isSelected) palette.accent.copy(alpha = 0.30f)
                                    else palette.pillBgEnd.copy(alpha = 0.8f)
                                )
                                .border(
                                    width = if (isSelected) 2.dp else 0.dp,
                                    color = if (isSelected) palette.accent
                                            else Color.Transparent,
                                    shape = CircleShape
                                )
                                .clickable { onSelectVector(name) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = vector,
                                contentDescription = name,
                                tint = palette.accentSoft,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                // ---- Раздел «Своя картинка» ----
                Text(
                    "Своя картинка",
                    color = palette.accentSoft,
                    fontSize = 14.sp
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = { pickImage.launch("image/*") }
                    ) {
                        Text(
                            if (currentUri.isNullOrBlank()) "Выбрать картинку"
                            else "Заменить картинку",
                            color = palette.accent
                        )
                    }
                    if (!currentUri.isNullOrBlank()) {
                        TextButton(onClick = onClear) {
                            Text("Сбросить", color = palette.accent)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Готово", color = palette.accent)
            }
        }
    )
}
