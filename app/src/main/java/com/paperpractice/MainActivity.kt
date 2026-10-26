package com.paperpractice

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import com.paperpractice.ui.MainViewModel
import com.paperpractice.ui.PaperPracticeApp
import com.paperpractice.ui.theme.PaperPracticeTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PaperPracticeTheme {
                PaperPracticeRoot()
            }
        }
    }
}

@Composable
private fun PaperPracticeRoot() {
    val viewModel: MainViewModel = viewModel()
    PaperPracticeApp(viewModel = viewModel)
}
