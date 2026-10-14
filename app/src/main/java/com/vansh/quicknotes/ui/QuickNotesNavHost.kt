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
import com.vansh.quicknotes.ui.lists.ListsScreen
import com.vansh.quicknotes.ui.onboarding.OnboardingScreen
import com.vansh.quicknotes.ui.settings.SettingsScreen

object Routes {
    const val HOME = "home"
    const val LISTS = "lists"
    const val SETTINGS = "settings"
    const val ONBOARDING = "onboarding"
    const val LIST_DETAIL = "list/{listId}"
    const val EDITOR = "editor/{noteId}?mode={mode}"

    fun listDetail(listId: String) = "list/$listId"

    fun editor(noteId: String, mode: String? = null) =
        if (mode != null) "editor/$noteId?mode=$mode" else "editor/$noteId"

    const val NEW_NOTE_ID = "new"
}

@Composable
fun QuickNotesNavHost(
    shareRouter: ShareRouter,
    onboardingPending: Boolean = false,
    startAtNoteId: String? = null,
    startInCaptureMode: String? = null,
    startAtLists: Boolean = false,
    onLaunched: () -> Unit = {},
    navController: NavHostController = rememberNavController(),
) {
    LaunchedEffect(shareRouter) {
        shareRouter.openNote.collect { id ->
            navController.navigate(Routes.editor(id))
        }
    }
    LaunchedEffect(startAtNoteId, startInCaptureMode, startAtLists) {
        when {
            startAtLists -> {
                navController.navigate(Routes.LISTS)
                onLaunched()
            }
            startAtNoteId != null -> {
                navController.navigate(Routes.editor(startAtNoteId))
                onLaunched()
            }
            startInCaptureMode != null -> {
                if (startInCaptureMode == com.vansh.quicknotes.widget.CaptureModes.LIST) {
                    navController.navigate(Routes.LISTS)
                } else {
                    navController.navigate(Routes.editor(Routes.NEW_NOTE_ID, startInCaptureMode))
                }
                onLaunched()
            }
        }
    }
    NavHost(navController = navController, startDestination = if (onboardingPending) Routes.ONBOARDING else Routes.HOME) {
        composable(Routes.ONBOARDING) {
            OnboardingScreen(onDone = { navController.navigate(Routes.HOME) })
        }
        composable(Routes.HOME) {
            val recording = com.vansh.quicknotes.ui.voice.RememberRecordingController(noteId = null)
            HomeScreen(
                onOpenNote = { id -> navController.navigate(Routes.editor(id)) },
                onOpenLists = { navController.navigate(Routes.LISTS) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
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
        composable(Routes.LISTS) {
            ListsScreen(onOpenList = { id -> navController.navigate(Routes.listDetail(id)) })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
        composable(
            route = Routes.EDITOR,
            arguments = listOf(
                navArgument("noteId") { type = NavType.StringType },
                navArgument("mode") { type = NavType.StringType; defaultValue = "" },
            ),
        ) { entry ->
            EditorScreen(
                onBack = { navController.popBackStack() },
                onPickImage = {},
                autoMode = entry.arguments?.getString("mode").orEmpty(),
            )
        }
    }
}
