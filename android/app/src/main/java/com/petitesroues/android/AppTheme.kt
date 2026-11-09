package com.petitesroues.android

import android.app.Activity
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

val Coral = Color(0xFFEB765C)
private val Navy = Color(0xFF142D4E)
private val Ivory = Color(0xFFF8F5EE)
private val type =
  Typography().let {
    it.copy(
      titleLarge =
        it.titleLarge.copy(
          fontWeight = FontWeight.SemiBold,
          fontSize = 21.sp,
          letterSpacing = (-0.5).sp,
        ),
      titleMedium = it.titleMedium.copy(fontWeight = FontWeight.SemiBold),
      bodyMedium = it.bodyMedium.copy(lineHeight = 22.sp),
    )
  }

/** Shared brand palette; browsing uses paper and navy, immersive media uses charcoal. */
@Composable
fun AppTheme(dark: Boolean = false, content: @Composable () -> Unit) {
  val view = LocalView.current
  DisposableEffect(view, dark) {
    val controller =
      (view.context as? Activity)?.let { WindowCompat.getInsetsController(it.window, view) }
    val statusLight = controller?.isAppearanceLightStatusBars ?: false
    val navigationLight = controller?.isAppearanceLightNavigationBars ?: false
    controller?.isAppearanceLightStatusBars = !dark
    controller?.isAppearanceLightNavigationBars = !dark
    onDispose {
      controller?.isAppearanceLightStatusBars = statusLight
      controller?.isAppearanceLightNavigationBars = navigationLight
    }
  }
  val colors =
    if (dark)
      darkColorScheme(
        primary = Coral,
        onPrimary = Color(0xFF231511),
        primaryContainer = Color(0xFF4B302C),
        onPrimaryContainer = Color(0xFFFFD8CE),
        secondary = Coral,
        background = Color(0xFF101216),
        onBackground = Color(0xFFE8E6E3),
        surface = Color(0xFF20242A),
        onSurface = Color(0xFFE8E6E3),
        surfaceVariant = Color(0xFF292E35),
        onSurfaceVariant = Color(0xFFB8BEC6),
        outline = Color(0xFF454A53),
        surfaceContainerHigh = Color(0xFF20242A),
      )
    else
      lightColorScheme(
        primary = Color(0xFFBD3B2B),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFFCE0D8),
        onPrimaryContainer = Color(0xFF90291E),
        secondary = Navy,
        background = Ivory,
        onBackground = Navy,
        surface = Ivory,
        onSurface = Navy,
        surfaceVariant = Color(0xFFEDE8DE),
        onSurfaceVariant = Color(0xFF707681),
        outline = Color(0xFFBEB9AF),
        outlineVariant = Color(0xFFE0DBD1),
        surfaceContainerHigh = Color(0xFFFFFCF7),
      )
  MaterialTheme(
    colorScheme = colors,
    typography = type,
    shapes =
      Shapes(
        medium = RoundedCornerShape(10.dp),
        large = RoundedCornerShape(16.dp),
        extraLarge = RoundedCornerShape(20.dp),
      ),
    content = content,
  )
}

/** The current wheel artwork, shared with the website, alongside the compact wordmark. */
@Composable
fun Brand() {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    Image(
      painterResource(R.drawable.brand_logo),
      contentDescription = null,
      modifier = Modifier.size(36.dp),
    )
    Text("petites roues", style = MaterialTheme.typography.titleLarge)
  }
}
