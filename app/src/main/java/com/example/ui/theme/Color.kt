package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// Executive Light Theme Palette (Clean, Modern, High Contrast with Black Text)
val ObsidianBg = Color(0xFFF8FAFC)        // Slate 50 - Ultra-clean crisp light workspace background
val ObsidianSurface = Color(0xFFFFFFFF)   // Pure white crisp card & container surface
val ObsidianCard = Color(0xFFF1F5F9)      // Slate 100 - High-contrast elevated element background
val ObsidianBorder = Color(0xFFCBD5E1)    // Slate 300 - Distinct clean borders and dividers

val CyanAccent = Color(0xFF0284C7)        // Ocean Sky Blue 600 - Rich, authoritative, readable
val CyanAccentGlow = Color(0xFF0EA5E9)    // Sky 500
val VioletAccent = Color(0xFF6366F1)      // Indigo/Violet 500
val IndigoAccent = Color(0xFF4338CA)      // Indigo 700

val EmeraldVerified = Color(0xFF059669)   // Emerald 600 - High contrast success green
val AmberPending = Color(0xFFD97706)      // Amber 600 - Warning / pending
val RoseError = Color(0xFFDC2626)         // Red 600 - Error / alert

val TextPrimary = Color(0xFF0F172A)       // Slate 900 - Deep crisp black text
val TextSecondary = Color(0xFF334155)     // Slate 700 - High readability secondary text
val TextTertiary = Color(0xFF64748B)      // Slate 500 - Metadata & subtle hints

// Material 3 mappings for Light Theme
val LightPrimary = CyanAccent
val LightOnPrimary = Color(0xFFFFFFFF)
val LightPrimaryContainer = Color(0xFFE0F2FE)
val LightOnPrimaryContainer = Color(0xFF0369A1)

val LightSecondary = VioletAccent
val LightOnSecondary = Color(0xFFFFFFFF)
val LightSecondaryContainer = Color(0xFFEEF2FF)
val LightOnSecondaryContainer = Color(0xFF3730A3)

val LightBackground = ObsidianBg
val LightOnBackground = TextPrimary
val LightSurface = ObsidianSurface
val LightOnSurface = TextPrimary
val LightSurfaceVariant = ObsidianCard
val LightOnSurfaceVariant = TextSecondary
val LightOutline = ObsidianBorder


