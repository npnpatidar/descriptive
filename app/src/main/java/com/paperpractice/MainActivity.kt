package com.paperpractice

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.paperpractice.ui.MainViewModel
import com.paperpractice.ui.PaperPracticeApp
import com.paperpractice.ui.theme.PaperPracticeTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PaperPracticeRoot()
        }
    }
}

@Composable
private fun PaperPracticeRoot() {
    val viewModel: MainViewModel = viewModel()
    val darkTheme = viewModel.themeMode.resolveDarkTheme(isSystemInDarkTheme())

    PaperPracticeTheme(darkTheme = darkTheme) {
        ApplySystemBarAppearance(darkTheme)
        PaperPracticeApp(viewModel = viewModel)
    }
}

@Composable
private fun ApplySystemBarAppearance(darkTheme: Boolean) {
    val view = LocalView.current
    LaunchedEffect(darkTheme, view) {
        val activity = view.context.findActivity() as? ComponentActivity
            ?: return@LaunchedEffect
        val style = if (darkTheme) {
            SystemBarStyle.dark(Color.TRANSPARENT)
        } else {
            SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        }
        activity.enableEdgeToEdge(
            statusBarStyle = style,
            navigationBarStyle = style
        )
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
