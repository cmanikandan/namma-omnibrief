package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// Namma Vibrant Light Palette (Warm, Social, High Contrast with Near-Black Text)
//
// Bengaluru at golden hour: jacaranda magenta, gulmohar coral-orange and deep indigo,
// laid over a barely-tinted warm off-white. Still a LIGHT theme with dark readable text.
//
// NOTE: the `Obsidian*` identifiers are historical names from the original dark palette.
// The names lie, the values are light. They are referenced widely - do not rename them.

val ObsidianBg = Color(0xFFFFF8F5) // Warm blush off-white - soft, alive workspace background
val ObsidianSurface = Color(0xFFFFFFFF) // Pure white card & container surface
val ObsidianCard = Color(0xFFFFF0EA) // Soft peach tint - elevated element background
val ObsidianBorder = Color(0xFFF2D9D1) // Warm blush border - visible but never harsh

// Accents are tuned to clear WCAG AA (4.5:1) against the *warmest* surface they can land on
// (ChipBlush), not just against pure white - headings and status labels sit on those tints.
val CyanAccent = Color(0xFFC91F66) // Jacaranda Magenta - primary accent (5.4:1 white, 4.8:1 chip)
val CyanAccentGlow = Color(0xFFF7457F) // Hot pink glow - decorative only (highlights, gradients)
val VioletAccent = Color(0xFF7C3AED) // Vivid Violet 600 - secondary accent (5.7:1 white)
val IndigoAccent = Color(0xFF4C1D95) // Deep Indigo 900 - grounding tone for gradients & emphasis

val EmeraldVerified = Color(0xFF0C7E56) // Deep Emerald - success / verified (5.1:1 white)
val AmberPending = Color(0xFFAD5700) // Gulmohar Amber - warning / pending (5.1:1 white)
val RoseError = Color(0xFFCE1740) // Crimson Rose - error / alert (5.5:1 white)

val TextPrimary = Color(0xFF1A1220) // Warm plum-black - maximum readability (16:1+ on background)
val TextSecondary = Color(0xFF554150) // Warm slate-plum - high readability secondary text (9:1)
val TextTertiary = Color(0xFF7D6577) // Muted mauve - metadata & subtle hints (5.2:1, still AA)

// Expressive extras (gradients, chips, elevation) - additive, safe to use anywhere.
val SunsetGradient: List<Color> =
  listOf(
    Color(0xFFFF8A3D), // Gulmohar orange
    Color(0xFFFF4E6A), // Coral pink
    Color(0xFFD6246E), // Jacaranda magenta
  )

val VioletGradient: List<Color> =
  listOf(
    Color(0xFFD6246E), // Jacaranda magenta
    Color(0xFF7C3AED), // Vivid violet
    Color(0xFF4C1D95), // Deep indigo
  )

val BrandGradient: List<Color> =
  listOf(
    Color(0xFFFCAF45), // Warm amber
    Color(0xFFE1306C), // Magenta pink
    Color(0xFF833AB4), // Electric purple
  )

val ChipBlush = Color(0xFFFFEDE4) // Soft warm tint for chip / pill backgrounds
val SurfaceElevated = Color(0xFFFFFCFA) // Card surface, a shade warmer than pure white

// Material 3 mappings for Light Theme
val LightPrimary = CyanAccent
val LightOnPrimary = Color(0xFFFFFFFF)
val LightPrimaryContainer = Color(0xFFFFE4EF)
val LightOnPrimaryContainer = Color(0xFF8C0F47)

val LightSecondary = VioletAccent
val LightOnSecondary = Color(0xFFFFFFFF)
val LightSecondaryContainer = Color(0xFFEDE7FE)
val LightOnSecondaryContainer = Color(0xFF4C1D95)

val LightBackground = ObsidianBg
val LightOnBackground = TextPrimary
val LightSurface = ObsidianSurface
val LightOnSurface = TextPrimary
val LightSurfaceVariant = ObsidianCard
val LightOnSurfaceVariant = TextSecondary
val LightOutline = ObsidianBorder
