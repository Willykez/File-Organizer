package com.willykez.files

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.willykez.files.permissions.PermissionsHelper
import com.willykez.files.ui.MainScreen
import com.willykez.files.ui.MainViewModel
import com.willykez.files.ui.theme.FileOrganizerTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            FileOrganizerTheme {
                val viewModel: MainViewModel = viewModel()
                val lifecycleOwner = LocalLifecycleOwner.current

                val manageStorageLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.StartActivityForResult()
                ) {
                    viewModel.setStoragePermission(PermissionsHelper.hasStorageAccess())
                }

                val legacyPermissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions()
                ) { results ->
                    viewModel.setStoragePermission(results.values.all { it })
                }

                // No result handling needed beyond the system prompt itself — NotificationHelper
                // already no-ops safely if the permission ends up denied.
                val notificationPermissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { /* no-op */ }

                // Re-check permission state whenever the user returns to the app (e.g. after
                // granting "All files access" in system Settings).
                DisposableEffectPermissionRefresh(lifecycleOwner) {
                    viewModel.setStoragePermission(PermissionsHelper.hasStorageAccess())
                }

                LaunchedEffect(Unit) {
                    viewModel.setStoragePermission(PermissionsHelper.hasStorageAccess())
                }

                MainScreen(
                    viewModel = viewModel,
                    onRequestStoragePermission = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            manageStorageLauncher.launch(PermissionsHelper.manageStorageIntent(this))
                        } else {
                            legacyPermissionLauncher.launch(PermissionsHelper.legacyStoragePermissions)
                        }
                    },
                    onRequestNotificationPermission = {
                        if (PermissionsHelper.needsNotificationPermission(this)) {
                            notificationPermissionLauncher.launch(PermissionsHelper.notificationPermission)
                        }
                    }
                )
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun DisposableEffectPermissionRefresh(
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    onResume: () -> Unit
) {
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) onResume()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}
