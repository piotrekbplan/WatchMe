package pl.watchme.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object WatchMeColors {
    val Gold = Color(0xFFF5C518)
    val GoldDark = Color(0xFFC99700)
    val OnGold = Color(0xFF111111)
    val Night = Color(0xFF121212)
    val NightSurface = Color(0xFF1C1C1E)
    val NightSurfaceVariant = Color(0xFF2A2A2D)
    val NightOnSurfaceVariant = Color(0xFFB3B3B8)
    val Day = Color(0xFFF6F6F6)
    val DaySurfaceVariant = Color(0xFFE7E7EA)
    val DayOnSurfaceVariant = Color(0xFF55555C)
}

private val DarkColors = darkColorScheme(
    primary = WatchMeColors.Gold,
    onPrimary = WatchMeColors.OnGold,
    secondary = WatchMeColors.Gold,
    onSecondary = WatchMeColors.OnGold,
    background = WatchMeColors.Night,
    onBackground = Color.White,
    surface = WatchMeColors.NightSurface,
    onSurface = Color.White,
    surfaceVariant = WatchMeColors.NightSurfaceVariant,
    onSurfaceVariant = WatchMeColors.NightOnSurfaceVariant,
    surfaceContainer = WatchMeColors.NightSurface,
    surfaceContainerHigh = WatchMeColors.NightSurfaceVariant,
)

private val LightColors = lightColorScheme(
    primary = WatchMeColors.GoldDark,
    onPrimary = WatchMeColors.OnGold,
    secondary = WatchMeColors.GoldDark,
    onSecondary = WatchMeColors.OnGold,
    background = WatchMeColors.Day,
    onBackground = WatchMeColors.OnGold,
    surface = Color.White,
    onSurface = WatchMeColors.OnGold,
    surfaceVariant = WatchMeColors.DaySurfaceVariant,
    onSurfaceVariant = WatchMeColors.DayOnSurfaceVariant,
)

private val WatchMeTypography = Typography().run {
    copy(
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.Bold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

val RatingTextStyle = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)

@Composable
fun WatchMeTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = WatchMeTypography,
        content = content,
    )
}
