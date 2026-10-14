package com.vansh.quicknotes.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.vansh.quicknotes.ui.editor.EditorScreen
import com.vansh.quicknotes.ui.home.HomeScreen

object Routes {
    const val HOME = "home"
    const val EDITOR = "editor/{noteId}"

    fun editor(noteId: String) = "editor/$noteId"
    const val NEW_NOTE_ID = "new"
}

@Composable
fun QuickNotesNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onOpenNote = { id -> navController.navigate(Routes.editor(id)) },
                onNewNote = { navController.navigate(Routes.editor(Routes.NEW_NOTE_ID)) },
            )
        }
        composable(
            route = Routes.EDITOR,
            arguments = listOf(navArgument("noteId") { type = NavType.StringType }),
        ) {
            EditorScreen(onBack = { navController.popBackStack() })
        }
    }
}
