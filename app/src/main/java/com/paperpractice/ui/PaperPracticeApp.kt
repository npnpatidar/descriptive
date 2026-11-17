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
    const val SEARCH = "search"
    const val CONFIG = "configure/{paperId}"
    const val QUESTIONS = "questions/{paperId}"
    const val PRACTICE = "practice"
    const val EXPORT = "export"
    const val TAGS = "tags"

    fun configure(paperId: String): String = "configure/${Uri.encode(paperId)}"
    fun questions(paperId: String): String = "questions/${Uri.encode(paperId)}"
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
                },
                onOpenQuestions = { paperId ->
                    navController.navigate(Routes.questions(paperId)) {
                        launchSingleTop = true
                    }
                },
                onSearch = {
                    navController.navigate(Routes.SEARCH) {
                        launchSingleTop = true
                    }
                },
                onManageTags = {
                    navController.navigate(Routes.TAGS) {
                        launchSingleTop = true
                    }
                }
            )
        }

        composable(Routes.SEARCH) {
            SearchScreen(
                viewModel = viewModel,
                onBack = {
                    viewModel.clearSearch()
                    navController.popBackStack()
                },
                onOpenPaper = { paperId ->
                    navController.navigate(Routes.configure(paperId)) {
                        launchSingleTop = true
                    }
                },
                onOpenQuestions = { paperId ->
                    navController.navigate(Routes.questions(paperId)) {
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
                },
                onEditQuestions = {
                    navController.navigate(Routes.questions(paperId)) {
                        launchSingleTop = true
                    }
                }
            )
        }

        composable(
            route = Routes.QUESTIONS,
            arguments = listOf(
                navArgument("paperId") { type = NavType.StringType }
            )
        ) { entry ->
            val paperId = entry.arguments?.getString("paperId").orEmpty()
            QuestionsScreen(
                viewModel = viewModel,
                paperId = paperId,
                onBack = { navController.popBackStack() }
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

        composable(Routes.TAGS) {
            TagsScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
