package dev.sifrekasa.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.sifrekasa.AppContainer
import dev.sifrekasa.data.VaultState

private sealed interface Screen {
    data object List : Screen
    data class Edit(val id: String?) : Screen
    data object Settings : Screen
}

@Composable
fun AppRoot(container: AppContainer, activity: FragmentActivity) {
    val state by container.vault.state.collectAsStateWithLifecycle()
    var screen by remember { mutableStateOf<Screen>(Screen.List) }
    val unlocked = state is VaultState.Unlocked

    LaunchedEffect(unlocked) {
        if (unlocked) container.lockManager.onUserInteraction() else screen = Screen.List
    }

    when (val current = state) {
        VaultState.NoVault -> SetupScreen(container.vault)
        VaultState.Locked -> UnlockScreen(container, activity)
        is VaultState.Unlocked -> when (val s = screen) {
            Screen.List -> VaultListScreen(
                entries = current.data.entries,
                onAdd = { screen = Screen.Edit(null) },
                onOpen = { screen = Screen.Edit(it) },
                onSettings = { screen = Screen.Settings },
                onLock = { container.vault.lock() },
            )
            is Screen.Edit -> EntryEditScreen(
                existing = current.data.entries.firstOrNull { it.id == s.id },
                vault = container.vault,
                clipboard = container.clipboard,
                onClose = { screen = Screen.List },
            )
            Screen.Settings -> SettingsScreen(container, activity, onBack = { screen = Screen.List })
        }
    }
}
