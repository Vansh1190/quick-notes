package com.vansh.quicknotes.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.vansh.quicknotes.domain.ShareRouter
import com.vansh.quicknotes.ui.editor.EditorScreen
import com.vansh.quicknotes.ui.home.HomeScreen

object Routes {
    const val HOME = "home"
    const val EDITOR = "editor/{noteId}"

    fun editor(noteId: String) = "editor/$noteId"
    const val NEW_NOTE_ID = "new"
}

@Composable
fun QuickNotesNavHost(
    shareRouter: ShareRouter,
    navController: NavHostController = rememberNavController(),
) {
    LaunchedEffect(shareRouter) {
        shareRouter.openNote.collect { id ->
            navController.navigate(Routes.editor(id))
        }
    }
    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            val recording = com.vansh.quicknotes.ui.voice.RememberRecordingController(noteId = null)
            HomeScreen(
                onOpenNote = { id -> navController.navigate(Routes.editor(id)) },
                onNewNote = { navController.navigate(Routes.editor(Routes.NEW_NOTE_ID)) },
                onVoiceNote = { recording.start() },
            )
            if (recording.showSheetState.value) {
                androidx.compose.ui.window.Dialog(
                    onDismissRequest = { recording.showSheetState.value = false },
                    properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
                ) {
                    com.vansh.quicknotes.ui.voice.RecordingSheetHost(recording)
                }
            }
        }
        composable(
            route = Routes.EDITOR,
            arguments = listOf(navArgument("noteId") { type = NavType.StringType }),
        ) {
            EditorScreen(
                onBack = { navController.popBackStack() },
                onPickImage = {},
            )
        }
    }
}
