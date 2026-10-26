package com.paperpractice.ui

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

private object Routes {
    const val HOME = "home"
    const val CONFIG = "configure/{paperId}"
    const val PRACTICE = "practice"
    const val EXPORT = "export"

    fun configure(paperId: String): String = "configure/${Uri.encode(paperId)}"
}

@Composable
fun PaperPracticeApp(viewModel: MainViewModel) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Routes.HOME
    ) {
        composable(Routes.HOME) {
            HomeScreen(
                viewModel = viewModel,
                onOpenPaper = { paperId ->
                    navController.navigate(Routes.configure(paperId)) {
                        launchSingleTop = true
                    }
                }
            )
        }

        composable(
            route = Routes.CONFIG,
            arguments = listOf(
                navArgument("paperId") { type = NavType.StringType }
            )
        ) { entry ->
            val paperId = entry.arguments?.getString("paperId").orEmpty()
            TestConfigScreen(
                viewModel = viewModel,
                paperId = paperId,
                onBack = { navController.popBackStack() },
                onStartTest = {
                    navController.navigate(Routes.PRACTICE) {
                        launchSingleTop = true
                    }
                },
                onExport = {
                    navController.navigate(Routes.EXPORT) {
                        launchSingleTop = true
                    }
                }
            )
        }

        composable(Routes.PRACTICE) {
            PracticeScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onExport = {
                    navController.navigate(Routes.EXPORT) {
                        launchSingleTop = true
                    }
                }
            )
        }

        composable(Routes.EXPORT) {
            ExportScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
