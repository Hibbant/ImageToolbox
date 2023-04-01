package com.cookhelper.dynamic.theme

import android.Manifest
import android.app.WallpaperManager
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import androidx.annotation.FloatRange
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.graphics.ColorUtils
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.palette.graphics.Palette
import com.cookhelper.dynamic.theme.hct.Hct
import com.cookhelper.dynamic.theme.palettes.TonalPalette
import com.cookhelper.dynamic.theme.scheme.Scheme
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import kotlin.math.min

/**
 * DynamicTheme allows you to dynamically change the color scheme of the content hierarchy.
 * To do this you just need to update [DynamicThemeState].
 * @param state - current instance of [DynamicThemeState]
 * */
@Composable
public fun DynamicTheme(
    state: DynamicThemeState,
    typography: Typography = Typography(),
    density: Density = LocalDensity.current.run {
        Density(this.density, min(fontScale, 1f))
    },
    defaultColorTuple: ColorTuple,
    dynamicColor: Boolean = true,
    amoledMode: Boolean = false,
    isDarkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorTuple = getAppColorTuple(
        defaultColorTuple = defaultColorTuple,
        dynamicColor = dynamicColor,
        darkTheme = isDarkTheme
    )

    LaunchedEffect(colorTuple) {
        state.updateColorTuple(colorTuple)
    }

    val systemUiController = rememberSystemUiController()
    val useDarkIcons = !isDarkTheme

    SideEffect {
        systemUiController.setSystemBarsColor(
            color = Color.Transparent,
            darkIcons = useDarkIcons,
            isNavigationBarContrastEnforced = false
        )
    }

    val scheme = rememberColorScheme(
        amoledMode = amoledMode,
        isDarkTheme = isDarkTheme,
        colorTuple = state.colorTuple.value
    ).animateAllColors(tween(150))

    MaterialTheme(
        typography = typography,
        colorScheme = scheme,
        content = {
            CompositionLocalProvider(
                values = arrayOf(
                    LocalDynamicThemeState provides state,
                    LocalDensity provides density
                ),
                content = content
            )
        }
    )
}

@Composable
public fun ColorTupleItem(
    modifier: Modifier = Modifier,
    colorTuple: ColorTuple,
    content: (@Composable BoxScope.() -> Unit)? = null
) {
    val (primary, secondary, tertiary) = remember(colorTuple) {
        derivedStateOf {
            colorTuple.run {
                val hct = Hct.fromInt(colorTuple.primary.toArgb())
                val hue = hct.hue
                val chroma = hct.chroma

                val secondary = colorTuple.secondary?.toArgb().let {
                    if (it != null) {
                        TonalPalette.fromInt(it)
                    } else {
                        TonalPalette.fromHueAndChroma(hue, chroma / 3.0)
                    }
                }
                val tertiary = colorTuple.tertiary?.toArgb().let {
                    if (it != null) {
                        TonalPalette.fromInt(it)
                    } else {
                        TonalPalette.fromHueAndChroma(hue + 60.0, chroma / 2.0)
                    }
                }

                Triple(
                    primary,
                    colorTuple.secondary ?: Color(secondary.tone(70)),
                    colorTuple.tertiary ?: Color(tertiary.tone(70))
                )
            }
        }
    }.value.run {
        Triple(
            animateColorAsState(targetValue = first).value,
            animateColorAsState(targetValue = second).value,
            animateColorAsState(targetValue = third).value
        )
    }

    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
                .clip(CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Column(
                Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(primary)
                )
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(tertiary)
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(secondary)
                    )
                }
            }
            content?.invoke(this)
        }
    }
}

@Composable
public fun getAppColorTuple(
    defaultColorTuple: ColorTuple,
    dynamicColor: Boolean,
    darkTheme: Boolean
): ColorTuple {
    val context = LocalContext.current
    return remember(
        LocalLifecycleOwner.current.lifecycle.observeAsState().value,
        dynamicColor,
        darkTheme,
        defaultColorTuple
    ) {
        derivedStateOf {
            var colorTuple: ColorTuple
            val wallpaperManager = WallpaperManager.getInstance(context)
            val wallColors =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                    wallpaperManager
                        .getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
                } else null

            when {
                dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                    if (darkTheme) {
                        dynamicDarkColorScheme(context)
                    } else {
                        dynamicLightColorScheme(context)
                    }.run {
                        colorTuple = ColorTuple(
                            primary = primary,
                            secondary = secondary,
                            tertiary = tertiary
                        )
                    }
                }
                dynamicColor && wallColors != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1 -> {
                    colorTuple = ColorTuple(
                        primary = Color(wallColors.primaryColor.toArgb()),
                        secondary = wallColors.secondaryColor?.toArgb()?.let { Color(it) },
                        tertiary = wallColors.tertiaryColor?.toArgb()?.let { Color(it) }
                    )
                }
                dynamicColor && ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED -> {
                    colorTuple = ColorTuple(
                        primary = (wallpaperManager.drawable as BitmapDrawable).bitmap.extractPrimaryColor(),
                        secondary = null,
                        tertiary = null
                    )
                }
                else -> {
                    colorTuple = defaultColorTuple
                }
            }
            colorTuple
        }
    }.value
}

@Composable
public fun Lifecycle.observeAsState(): State<Lifecycle.Event> {
    val state = remember { mutableStateOf(Lifecycle.Event.ON_ANY) }
    DisposableEffect(this) {
        val observer = LifecycleEventObserver { _, event ->
            state.value = event
        }
        this@observeAsState.addObserver(observer)
        onDispose {
            this@observeAsState.removeObserver(observer)
        }
    }
    return state
}

/**
 * This function animates colors when current color scheme changes.
 *
 * @param animationSpec Animation that will be applied when theming option changes.
 * @return [ColorScheme] with animated colors.
 */
@Composable
private fun ColorScheme.animateAllColors(animationSpec: AnimationSpec<Color>): ColorScheme {

    /**
     * Wraps color into [animateColorAsState].
     *
     * @return Animated [Color].
     */
    @Composable
    fun Color.animateColor() = animateColorAsState(this, animationSpec).value

    return this.copy(
        primary = primary.animateColor(),
        onPrimary = onPrimary.animateColor(),
        primaryContainer = primaryContainer.animateColor(),
        onPrimaryContainer = onPrimaryContainer.animateColor(),
        inversePrimary = inversePrimary.animateColor(),
        secondary = secondary.animateColor(),
        onSecondary = onSecondary.animateColor(),
        secondaryContainer = secondaryContainer.animateColor(),
        onSecondaryContainer = onSecondaryContainer.animateColor(),
        tertiary = tertiary.animateColor(),
        onTertiary = onTertiary.animateColor(),
        tertiaryContainer = tertiaryContainer.animateColor(),
        onTertiaryContainer = onTertiaryContainer.animateColor(),
        background = background.animateColor(),
        onBackground = onBackground.animateColor(),
        surface = surface.animateColor(),
        onSurface = onSurface.animateColor(),
        surfaceVariant = surfaceVariant.animateColor(),
        onSurfaceVariant = onSurfaceVariant.animateColor(),
        surfaceTint = surfaceTint.animateColor(),
        inverseSurface = inverseSurface.animateColor(),
        inverseOnSurface = inverseOnSurface.animateColor(),
        error = error.animateColor(),
        onError = onError.animateColor(),
        errorContainer = errorContainer.animateColor(),
        onErrorContainer = onErrorContainer.animateColor(),
        outline = outline.animateColor(),
    )
}


public fun Bitmap.extractPrimaryColor(default: Int = 0, blendWithVibrant: Boolean = true): Color {
    fun Int.blend(
        color: Int,
        @FloatRange(from = 0.0, to = 1.0) fraction: Float = 0.5f
    ): Int = ColorUtils.blendARGB(this, color, fraction)

    val palette = Palette
        .from(this)
        .generate()

    return Color(
        palette.getDominantColor(default).run {
            if (blendWithVibrant) blend(palette.getVibrantColor(default))
            else this
        }
    )
}

public data class ColorTuple(
    val primary: Color,
    val secondary: Color? = null,
    val tertiary: Color? = null
)

/**
 * Creates and remember [DynamicThemeState] instance
 * */
@Composable
public fun rememberDynamicThemeState(
    initialColorTuple: ColorTuple = ColorTuple(
        primary = MaterialTheme.colorScheme.primary,
        secondary = MaterialTheme.colorScheme.secondary,
        tertiary = MaterialTheme.colorScheme.tertiary,
    )
): DynamicThemeState {
    return remember {
        DynamicThemeState(initialColorTuple)
    }
}

@Stable
public class DynamicThemeState(
    initialColorTuple: ColorTuple
) {
    public val colorTuple: MutableState<ColorTuple> = mutableStateOf(initialColorTuple)

    public fun updateColor(color: Color) {
        colorTuple.value = ColorTuple(primary = color, secondary = null, tertiary = null)
    }

    public fun updateColorTuple(newColorTuple: ColorTuple) {
        colorTuple.value = newColorTuple
    }

    public fun updateColorByImage(bitmap: Bitmap) {
        updateColor(bitmap.extractPrimaryColor())
    }
}

@Composable
public fun rememberColorScheme(
    isDarkTheme: Boolean,
    amoledMode: Boolean,
    colorTuple: ColorTuple
): ColorScheme {
    return remember(colorTuple, isDarkTheme, amoledMode) {
        if (isDarkTheme) {
            Scheme.darkContent(colorTuple.primary.toArgb()).toDarkThemeColorScheme(colorTuple)
        } else {
            Scheme.lightContent(colorTuple.primary.toArgb()).toLightThemeColorScheme(colorTuple)
        }.let {
            if (amoledMode && isDarkTheme) {
                it.copy(background = Color.Black, surface = Color.Black)
            } else it
        }.run {
            copy(
                outlineVariant = onSecondaryContainer
                    .copy(alpha = 0.2f)
                    .compositeOver(surfaceColorAtElevation(6.dp))
            )
        }
    }
}

private fun Scheme.toDarkThemeColorScheme(
    colorTuple: ColorTuple
): ColorScheme {
    val hct = Hct.fromInt(colorTuple.primary.toArgb())
    val hue = hct.hue
    val chroma = hct.chroma

    val a2 = colorTuple.secondary?.toArgb().let {
        if (it != null) {
            TonalPalette.fromInt(it)
        } else {
            TonalPalette.fromHueAndChroma(hue, chroma / 3.0)
        }
    }
    val a3 = colorTuple.tertiary?.toArgb().let {
        if (it != null) {
            TonalPalette.fromInt(it)
        } else {
            TonalPalette.fromHueAndChroma(hue + 60.0, chroma / 2.0)
        }
    }

    return darkColorScheme(
        primary = Color(primary),
        onPrimary = Color(onPrimary),
        primaryContainer = Color(primaryContainer),
        onPrimaryContainer = Color(onPrimaryContainer),
        inversePrimary = Color(inversePrimary),
        secondary = Color(a2?.tone(80) ?: secondary),
        onSecondary = Color(a2?.tone(20) ?: onSecondary),
        secondaryContainer = Color(a2?.tone(30) ?: secondaryContainer),
        onSecondaryContainer = Color(a2?.tone(90) ?: onSecondaryContainer),
        tertiary = Color(a3?.tone(80) ?: tertiary),
        onTertiary = Color(a3?.tone(20) ?: onTertiary),
        tertiaryContainer = Color(a3?.tone(30) ?: tertiaryContainer),
        onTertiaryContainer = Color(a3?.tone(90) ?: onTertiaryContainer),
        background = Color(background),
        onBackground = Color(onBackground),
        surface = Color(surface),
        onSurface = Color(onSurface),
        surfaceVariant = Color(surfaceVariant),
        onSurfaceVariant = Color(onSurfaceVariant),
        surfaceTint = Color(primary),
        inverseSurface = Color(inverseSurface),
        inverseOnSurface = Color(inverseOnSurface),
        error = Color(error),
        onError = Color(onError),
        errorContainer = Color(errorContainer),
        onErrorContainer = Color(onErrorContainer),
        outline = Color(outline),
        outlineVariant = Color(outlineVariant),
        scrim = Color(scrim),
    )
}

private fun Scheme.toLightThemeColorScheme(
    colorTuple: ColorTuple
): ColorScheme {
    val hct = Hct.fromInt(colorTuple.primary.toArgb())
    val hue = hct.hue
    val chroma = hct.chroma

    val a2 = colorTuple.secondary?.toArgb().let {
        if (it != null) {
            TonalPalette.fromInt(it)
        } else {
            TonalPalette.fromHueAndChroma(hue, chroma / 3.0)
        }
    }
    val a3 = colorTuple.tertiary?.toArgb().let {
        if (it != null) {
            TonalPalette.fromInt(it)
        } else {
            TonalPalette.fromHueAndChroma(hue + 60.0, chroma / 2.0)
        }
    }

    return lightColorScheme(
        primary = Color(primary),
        onPrimary = Color(onPrimary),
        primaryContainer = Color(primaryContainer),
        onPrimaryContainer = Color(onPrimaryContainer),
        inversePrimary = Color(inversePrimary),
        secondary = Color(a2?.tone(40) ?: secondary),
        onSecondary = Color(a2?.tone(100) ?: onSecondary),
        secondaryContainer = Color(a2?.tone(90) ?: secondaryContainer),
        onSecondaryContainer = Color(a2?.tone(10) ?: onSecondaryContainer),
        tertiary = Color(a3?.tone(40) ?: tertiary),
        onTertiary = Color(a3?.tone(100) ?: onTertiary),
        tertiaryContainer = Color(a3?.tone(90) ?: tertiaryContainer),
        onTertiaryContainer = Color(a3?.tone(10) ?: onTertiaryContainer),
        background = Color(background),
        onBackground = Color(onBackground),
        surface = Color(surface),
        onSurface = Color(onSurface),
        surfaceVariant = Color(surfaceVariant),
        onSurfaceVariant = Color(onSurfaceVariant),
        surfaceTint = Color(primary),
        inverseSurface = Color(inverseSurface),
        inverseOnSurface = Color(inverseOnSurface),
        error = Color(error),
        onError = Color(onError),
        errorContainer = Color(errorContainer),
        onErrorContainer = Color(onErrorContainer),
        outline = Color(outline),
        outlineVariant = Color(outlineVariant),
        scrim = Color(scrim),
    )
}

public val LocalDynamicThemeState: ProvidableCompositionLocal<DynamicThemeState> =
    compositionLocalOf { error("Not present") }