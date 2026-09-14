package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

private val ModernLightColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightOnPrimaryContainer,
    secondary = LightSecondary,
    onSecondary = LightOnSecondary,
    secondaryContainer = LightSecondaryContainer,
    onSecondaryContainer = LightOnSecondaryContainer,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOutline
)

/**
 * App theme.
 *
 * ## Why the text scale is applied via [LocalDensity] and not via the typography
 *
 * Scaling only [androidx.compose.material3.Typography] was not enough. These screens were written
 * with roughly 150 literal `fontSize = 12.sp`-style values rather than
 * `MaterialTheme.typography.*`, so a scaled type ramp left almost the entire app unchanged and the
 * Text Size setting looked broken.
 *
 * Overriding `LocalDensity.fontScale` fixes that at the root: `sp` is converted to pixels using the
 * density's `fontScale`, so **every** `sp` value in the tree scales — the literals and the
 * typography alike. `density` itself is left untouched, so `dp` paddings and component sizes keep
 * their designed values.
 *
 * Two consequences worth knowing:
 * - The typography passed to [MaterialTheme] must stay at its **baseline** scale. Passing
 *   `appTypography(fontScale)` here as well would scale those styles twice.
 * - The user's Android system font size is respected and composed with, not replaced, because the
 *   app scale multiplies the incoming `fontScale` rather than overwriting it.
 */
@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = false,
    dynamicColor: Boolean = false,
    /** Whole-app text scale from Settings. 1.0 is the designed baseline. */
    fontScale: Float = 1.0f,
    content: @Composable () -> Unit,
) {
    val base = LocalDensity.current
    val scaled = remember(base, fontScale) {
        Density(
            density = base.density,
            fontScale = base.fontScale * fontScale.coerceIn(MIN_FONT_SCALE, MAX_FONT_SCALE)
        )
    }

    CompositionLocalProvider(LocalDensity provides scaled) {
        MaterialTheme(
            colorScheme = ModernLightColorScheme,
            // Baseline ramp on purpose — LocalDensity above is what applies the user's scale.
            typography = Typography,
            content = content
        )
    }
}

/**
 * Renders [content] as if the user had picked [optionScale], regardless of what is selected now.
 *
 * Used by the Text Size picker in Settings so each option previews itself at its true size rather
 * than at a size relative to the current selection.
 *
 * [currentScale] must be the scale the surrounding theme is applying. It cannot be inferred: by the
 * time this composable runs, `LocalDensity.fontScale` already has it baked in, so it has to be
 * divided back out to recover the system-only scale before the option's own is applied.
 */
@Composable
fun PreviewAtFontScale(
    optionScale: Float,
    currentScale: Float,
    content: @Composable () -> Unit,
) {
    val base = LocalDensity.current
    val applied = currentScale.coerceIn(MIN_FONT_SCALE, MAX_FONT_SCALE)
    val target = remember(base, optionScale, applied) {
        val systemOnly = base.fontScale / applied
        Density(
            density = base.density,
            fontScale = systemOnly * optionScale.coerceIn(MIN_FONT_SCALE, MAX_FONT_SCALE)
        )
    }
    CompositionLocalProvider(LocalDensity provides target) {
        content()
    }
}
