package com.example

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.i18n.LocalStrings
import com.example.i18n.stringsFor
import com.example.model.StudioSettings
import com.example.ui.navigation.GuideRoute
import com.example.ui.navigation.HomeRoute
import com.example.ui.navigation.OutputRoute
import com.example.ui.navigation.RigEditorRoute
import com.example.ui.navigation.SettingsRoute
import com.example.ui.navigation.StudioRoute
import com.example.ui.screens.GuideScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.OutputScreen
import com.example.ui.screens.RigEditorScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.StudioScreen
import com.example.ui.theme.TalkingPhotoTheme
import com.example.viewmodel.HomeViewModel
import com.example.viewmodel.RigEditorViewModel
import com.example.viewmodel.SettingsViewModel
import com.example.viewmodel.StudioViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        setContent {
            TalkingPhotoProApp()
        }
    }
}

@Composable
fun TalkingPhotoProApp() {
    val context = LocalContext.current
    val container = remember { AppContainer(context) }
    val settings by container.settingsDataStore.settingsFlow
        .collectAsStateWithLifecycle(initialValue = StudioSettings())
    val strings = stringsFor(settings.language)
    val scope = rememberCoroutineScope()
    val navController = rememberNavController()

    TalkingPhotoTheme {
        CompositionLocalProvider(LocalStrings provides strings) {
            NavHost(
                navController = navController,
                startDestination = HomeRoute
            ) {
                composable<HomeRoute> {
                    val vm: HomeViewModel = viewModel(
                        factory = HomeViewModel.Factory(
                            container.projectRepository,
                            container.settingsDataStore
                        )
                    )
                    HomeScreen(
                        viewModel = vm,
                        language = settings.language,
                        onProjectCreated = { projectId ->
                            navController.navigate(RigEditorRoute(projectId))
                        },
                        onOpenStudio = { projectId ->
                            navController.navigate(StudioRoute(projectId))
                        },
                        onEditRig = { projectId ->
                            navController.navigate(RigEditorRoute(projectId))
                        },
                        onOpenGuide = { navController.navigate(GuideRoute) },
                        onOpenSettings = { navController.navigate(SettingsRoute) }
                    )
                }

                composable<RigEditorRoute> { backStackEntry ->
                    val route = backStackEntry.toRoute<RigEditorRoute>()
                    val vm: RigEditorViewModel = viewModel(
                        factory = RigEditorViewModel.Factory(
                            route.projectId,
                            container.projectRepository
                        )
                    )
                    RigEditorScreen(
                        viewModel = vm,
                        language = settings.language,
                        onToggleLanguage = { scope.launch { container.settingsDataStore.toggleLanguage() } },
                        onBack = { navController.popBackStack() },
                        onSavedToStudio = { projectId ->
                            navController.navigate(StudioRoute(projectId)) {
                                popUpTo<RigEditorRoute> { inclusive = true }
                            }
                        }
                    )
                }

                composable<StudioRoute> { backStackEntry ->
                    val route = backStackEntry.toRoute<StudioRoute>()
                    val vm: StudioViewModel = viewModel(
                        factory = StudioViewModel.Factory(
                            context.applicationContext,
                            route.projectId,
                            container.projectRepository,
                            container.settingsDataStore
                        )
                    )
                    StudioScreen(
                        viewModel = vm,
                        language = settings.language,
                        onToggleLanguage = { scope.launch { container.settingsDataStore.toggleLanguage() } },
                        onBack = { navController.popBackStack() },
                        onEditRig = { projectId ->
                            navController.navigate(RigEditorRoute(projectId))
                        },
                        onOpenSettings = { navController.navigate(SettingsRoute) },
                        onVideoExported = { projectId, videoPath ->
                            navController.navigate(OutputRoute(projectId, videoPath))
                        }
                    )
                }

                composable<OutputRoute> { backStackEntry ->
                    val route = backStackEntry.toRoute<OutputRoute>()
                    OutputScreen(
                        videoPath = route.videoPath,
                        language = settings.language,
                        onToggleLanguage = { scope.launch { container.settingsDataStore.toggleLanguage() } },
                        onBackToStudio = { navController.popBackStack() },
                        onBackToHome = {
                            navController.popBackStack<HomeRoute>(inclusive = false)
                        }
                    )
                }

                composable<SettingsRoute> {
                    val vm: SettingsViewModel = viewModel(
                        factory = SettingsViewModel.Factory(container.settingsDataStore)
                    )
                    SettingsScreen(
                        viewModel = vm,
                        onBack = { navController.popBackStack() }
                    )
                }

                composable<GuideRoute> {
                    GuideScreen(
                        language = settings.language,
                        onToggleLanguage = { scope.launch { container.settingsDataStore.toggleLanguage() } },
                        onBack = { navController.popBackStack() }
                    )
                }
            }
        }
    }
}
