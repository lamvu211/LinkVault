package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Theme 1: Denim Cool (Ocean Blue / Slate - Male 1)
private val DenimLight = lightColorScheme(
    primary = Color(0xFF1976D2), // Ocean Blue
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFBBDEFB), // Light Blue
    onPrimaryContainer = Color(0xFF0D47A1),
    secondary = Color(0xFF37474F), // Slate Grey
    onSecondary = Color(0xFFFFFFFF),
    tertiary = Color(0xFF546E7A),
    background = Color(0xFFF0F4F8), // Icy Soft Blue Background
    onBackground = Color(0xFF1A2129),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1A1F26),
    surfaceVariant = Color(0xFFE1EBF5),
    onSurfaceVariant = Color(0xFF37474F),
    outline = Color(0xFFCFD8DC),
    outlineVariant = Color(0xFFB0BEC5)
)

private val DenimDark = darkColorScheme(
    primary = Color(0xFF90CAF9),
    onPrimary = Color(0xFF0D47A1),
    primaryContainer = Color(0xFF1565C0),
    onPrimaryContainer = Color(0xFFE3F2FD),
    secondary = Color(0xFFB0BEC5),
    onSecondary = Color(0xFF263238),
    tertiary = Color(0xFF90A4AE),
    background = Color(0xFF101622),
    onBackground = Color(0xFFECEFF1),
    surface = Color(0xFF1E293B),
    onSurface = Color(0xFFECEFF1),
    surfaceVariant = Color(0xFF2E3D52),
    onSurfaceVariant = Color(0xFFCFD8DC),
    outline = Color(0xFF455A64),
    outlineVariant = Color(0xFF546E7A)
)

// Theme 2: Forest Jade (Sage Green / Teal - Male 2)
private val ForestLight = lightColorScheme(
    primary = Color(0xFF2E7D32), // Forest green
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFC8E6C9), // Sage accent
    onPrimaryContainer = Color(0xFF1B5E20),
    secondary = Color(0xFF2E3D30), // Dark green charcoal
    onSecondary = Color(0xFFFFFFFF),
    tertiary = Color(0xFF556B2F),
    background = Color(0xFFF1F5F1), // Sage wood background
    onBackground = Color(0xFF19211A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF191F1A),
    surfaceVariant = Color(0xFFE3EBE3),
    onSurfaceVariant = Color(0xFF2E3D30),
    outline = Color(0xFFC8D6C8),
    outlineVariant = Color(0xFFB0C2B0)
)

private val ForestDark = darkColorScheme(
    primary = Color(0xFFA5D6A7),
    onPrimary = Color(0xFF1B5E20),
    primaryContainer = Color(0xFF2E7D32),
    onPrimaryContainer = Color(0xFFE8F5E9),
    secondary = Color(0xFFCCD4CD),
    onSecondary = Color(0xFF1E231F),
    tertiary = Color(0xFFA5B8A5),
    background = Color(0xFF101712),
    onBackground = Color(0xFFE9F1EA),
    surface = Color(0xFF1B231D),
    onSurface = Color(0xFFE9F1EA),
    surfaceVariant = Color(0xFF29332A),
    onSurfaceVariant = Color(0xFFCCD4CD),
    outline = Color(0xFF384439),
    outlineVariant = Color(0xFF4C5B4D)
)

// Theme 3: Blossom Rose (Warm Pink / Soft Rose - Female 1)
private val BlossomLight = lightColorScheme(
    primary = Color(0xFFD81B60), // Elegant Pink
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFCE4EC), // Blossom accent
    onPrimaryContainer = Color(0xFF880E4F),
    secondary = Color(0xFF4A148C), // Plum deep
    onSecondary = Color(0xFFFFFFFF),
    tertiary = Color(0xFF8D6E63),
    background = Color(0xFFFFF0F5), // Lavender soft pink background
    onBackground = Color(0xFF2D161F),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF2A161E),
    surfaceVariant = Color(0xFFFAD1DF),
    onSurfaceVariant = Color(0xFF4A148C),
    outline = Color(0xFFF5B7CE),
    outlineVariant = Color(0xFFF09BB8)
)

private val BlossomDark = darkColorScheme(
    primary = Color(0xFFF48FB1),
    onPrimary = Color(0xFF880E4F),
    primaryContainer = Color(0xFFAD1457),
    onPrimaryContainer = Color(0xFFFCE4EC),
    secondary = Color(0xFFF3CCD8),
    onSecondary = Color(0xFF2D121F),
    tertiary = Color(0xFFD7CCC8),
    background = Color(0xFF1C1014),
    onBackground = Color(0xFFFCE4EC),
    surface = Color(0xFF2D1B22),
    onSurface = Color(0xFFFCE4EC),
    surfaceVariant = Color(0xFF4D2434),
    onSurfaceVariant = Color(0xFFF3CCD8),
    outline = Color(0xFF6A3149),
    outlineVariant = Color(0xFF80425D)
)

// Theme 4: Peach Amber (Coral / Warm Gold - Female 2)
private val PeachLight = lightColorScheme(
    primary = Color(0xFFE65100), // Intense Amber Orange/Coral
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFE0B2), // Soft peach
    onPrimaryContainer = Color(0xFFE65100),
    secondary = Color(0xFF3E2723), // Dark espresso brown
    onSecondary = Color(0xFFFFFFFF),
    tertiary = Color(0xFF8C7D70),
    background = Color(0xFFFFF7EF), // Sun-kissed peach background
    onBackground = Color(0xFF2E1F18),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF2D201A),
    surfaceVariant = Color(0xFFF6E7D2),
    onSurfaceVariant = Color(0xFF3E2723),
    outline = Color(0xFFEEDAC0),
    outlineVariant = Color(0xFFDCBF9D)
)

private val PeachDark = darkColorScheme(
    primary = Color(0xFFFFB74D),
    onPrimary = Color(0xFF5D250F),
    primaryContainer = Color(0xFFD84315),
    onPrimaryContainer = Color(0xFFFFE0B2),
    secondary = Color(0xFFFFD1A9),
    onSecondary = Color(0xFF2E1A11),
    tertiary = Color(0xFFD7CCC8),
    background = Color(0xFF1B110D),
    onBackground = Color(0xFFFFE3D1),
    surface = Color(0xFF2D1C16),
    onSurface = Color(0xFFFFE3D1),
    surfaceVariant = Color(0xFF4E2D20),
    onSurfaceVariant = Color(0xFFFFD1A9),
    outline = Color(0xFF6E402D),
    outlineVariant = Color(0xFF804A35)
)

// Theme 5: Lavender Mist (Lilac / Deep Iris - Female 3)
private val LavenderLight = lightColorScheme(
    primary = Color(0xFF8E24AA), // Soft Lilac Purple
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFF3E5F5), // Lavender soft
    onPrimaryContainer = Color(0xFF4A148C),
    secondary = Color(0xFF311B92), // Cosmic Indigo
    onSecondary = Color(0xFFFFFFFF),
    tertiary = Color(0xFF6A1B9A),
    background = Color(0xFFFAF4FC), // Lavender-white background
    onBackground = Color(0xFF22162A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF211628),
    surfaceVariant = Color(0xFFECD5F2),
    onSurfaceVariant = Color(0xFF311B92),
    outline = Color(0xFFDCC1E8),
    outlineVariant = Color(0xFFC099D4)
)

private val LavenderDark = darkColorScheme(
    primary = Color(0xFFE1BEE7),
    onPrimary = Color(0xFF4A148C),
    primaryContainer = Color(0xFF7B1FA2),
    onPrimaryContainer = Color(0xFFF3E5F5),
    secondary = Color(0xFFD1C4E9),
    onSecondary = Color(0xFF1A0054),
    tertiary = Color(0xFFBA68C8),
    background = Color(0xFF16101B),
    onBackground = Color(0xFFF3E5F5),
    surface = Color(0xFF241A2D),
    onSurface = Color(0xFFF3E5F5),
    surfaceVariant = Color(0xFF452C53),
    onSurfaceVariant = Color(0xFFD1C4E9),
    outline = Color(0xFF67407A),
    outlineVariant = Color(0xFF815299)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = false,
    themeName: String = "denim",
    content: @Composable () -> Unit,
) {
    val colorScheme = when (themeName.lowercase()) {
        "denim" -> if (darkTheme) DenimDark else DenimLight
        "forest" -> if (darkTheme) ForestDark else ForestLight
        "blossom" -> if (darkTheme) BlossomDark else BlossomLight
        "peach" -> if (darkTheme) PeachDark else PeachLight
        "lavender" -> if (darkTheme) LavenderDark else LavenderLight
        else -> if (darkTheme) DenimDark else DenimLight
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
