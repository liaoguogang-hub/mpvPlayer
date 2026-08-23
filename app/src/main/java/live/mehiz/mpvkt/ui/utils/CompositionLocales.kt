package live.mehiz.mpvkt.ui.utils

import androidx.compose.runtime.compositionLocalOf
import androidx.navigation3.runtime.NavBackStack
import live.mehiz.mpvkt.presentation.Screen

@Suppress("CompositionLocalAllowlist")
// W31.30:navigation3 1.1.0 NavBackStack 必须有 type 参数 <T : NavKey>。
val LocalBackStack = compositionLocalOf<NavBackStack<Screen>> {
  error("LocalBackStack not initialized!")
}
