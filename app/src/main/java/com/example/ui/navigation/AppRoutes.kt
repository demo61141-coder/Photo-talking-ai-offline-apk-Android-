package com.example.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
data object HomeRoute

@Serializable
data class RigEditorRoute(val projectId: String)

@Serializable
data class StudioRoute(val projectId: String)

@Serializable
data class OutputRoute(val projectId: String, val videoPath: String)

@Serializable
data object SettingsRoute

@Serializable
data object GuideRoute
