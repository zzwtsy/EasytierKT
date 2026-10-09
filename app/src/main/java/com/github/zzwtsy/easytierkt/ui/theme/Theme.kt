package com.github.zzwtsy.easytierkt.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val DarkColorScheme =
    darkColorScheme(
        primary = Blue80,
        onPrimary = BlueOn80,
        primaryContainer = BlueContainer80,
        onPrimaryContainer = BlueOnContainer80,
        secondary = BlueGrey80,
        onSecondary = BlueGreyOn80,
        secondaryContainer = BlueGreyContainer80,
        onSecondaryContainer = BlueGreyOnContainer80,
        tertiary = Cyan80,
        onTertiary = CyanOn80,
        tertiaryContainer = CyanContainer80,
        onTertiaryContainer = CyanOnContainer80,
    )

private val LightColorScheme =
    lightColorScheme(
        primary = Blue40,
        onPrimary = BlueOn40,
        primaryContainer = BlueContainer40,
        onPrimaryContainer = BlueOnContainer40,
        secondary = BlueGrey40,
        onSecondary = BlueGreyOn40,
        secondaryContainer = BlueGreyContainer40,
        onSecondaryContainer = BlueGreyOnContainer40,
        tertiary = Cyan40,
        onTertiary = CyanOn40,
        tertiaryContainer = CyanContainer40,
        onTertiaryContainer = CyanOnContainer40,
    )

/** M3 Expressive 风格的加大圆角基线：卡片走 medium，输入框走 extraSmall。 */
private val AppShapes =
    Shapes(
        extraSmall = RoundedCornerShape(12.dp),
        small = RoundedCornerShape(16.dp),
        medium = RoundedCornerShape(20.dp),
        large = RoundedCornerShape(28.dp),
        extraLarge = RoundedCornerShape(36.dp),
    )

/** M3 色板之外的语义扩展色；当前用于“已连接”状态。 */
@Immutable
data class ExtendedColors(
    val success: Color,
    val onSuccess: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
)

private val LightExtendedColors =
    ExtendedColors(
        success = Green40,
        onSuccess = GreenOn40,
        successContainer = GreenContainer40,
        onSuccessContainer = GreenOnContainer40,
    )

private val DarkExtendedColors =
    ExtendedColors(
        success = Green80,
        onSuccess = GreenOn80,
        successContainer = GreenContainer80,
        onSuccessContainer = GreenOnContainer80,
    )

val LocalExtendedColors = staticCompositionLocalOf { LightExtendedColors }

/** 当前主题的语义扩展色。 */
val extendedColors: ExtendedColors
    @Composable
    get() = LocalExtendedColors.current

@Composable
fun EasytierKTTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Android 12 及以上版本支持动态取色。
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme =
        when {
            dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                val context = LocalContext.current
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }

            darkTheme -> DarkColorScheme
            else -> LightColorScheme
        }

    CompositionLocalProvider(
        LocalExtendedColors provides if (darkTheme) DarkExtendedColors else LightExtendedColors,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = AppShapes,
            // MD3 Expressive：hero 交互使用弹簧物理动效。
            motionScheme = MotionScheme.expressive(),
            content = content,
        )
    }
}
