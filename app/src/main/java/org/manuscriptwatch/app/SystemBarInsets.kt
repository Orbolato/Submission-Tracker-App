package org.manuscriptwatch.app

import android.view.View
import androidx.activity.ComponentActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updatePadding

/**
 * Android 15+ draws edge-to-edge for apps targeting API 35.
 * Keep interactive content inside the status bar, display cutout, and navigation
 * bar safe area on every Android version, including Xiaomi HyperOS.
 *
 * Call this after setContentView(...) on the activity's top-level view.
 * The starting padding is captured once, preventing cumulative padding when
 * the system reapplies insets (e.g. rotation, gesture/3-button changes).
 */
internal fun ComponentActivity.keepContentInsideSystemBars(root: View) {
    WindowCompat.setDecorFitsSystemWindows(window, false)
    WindowInsetsControllerCompat(window, root).apply {
        isAppearanceLightStatusBars = true
        isAppearanceLightNavigationBars = true
    }

    val originalLeft = root.paddingLeft
    val originalTop = root.paddingTop
    val originalRight = root.paddingRight
    val originalBottom = root.paddingBottom

    ViewCompat.setOnApplyWindowInsetsListener(root) { view, windowInsets ->
        val safe = windowInsets.getInsets(
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
        )
        // The IME can cover form fields; don't put the root under the keyboard.
        val keyboard = windowInsets.getInsets(WindowInsetsCompat.Type.ime())
        view.updatePadding(
            left = originalLeft + safe.left,
            top = originalTop + safe.top,
            right = originalRight + safe.right,
            bottom = originalBottom + maxOf(safe.bottom, keyboard.bottom)
        )
        windowInsets
    }
    ViewCompat.requestApplyInsets(root)
}
