package dev.ryazha.sassist.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import dev.ryazha.sassist.model.AppLanguage

val LocalAppLanguage = staticCompositionLocalOf { AppLanguage.Russian }

/** Use Russian first throughout Compose while keeping English available in Settings. */
@Composable
fun tr(russian: String, english: String): String =
    if (LocalAppLanguage.current == AppLanguage.Russian) russian else english
