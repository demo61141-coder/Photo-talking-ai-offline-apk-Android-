package com.example

import android.content.Context
import com.example.data.AppDatabase
import com.example.data.ProjectRepository
import com.example.data.SettingsDataStore

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val database = AppDatabase.getInstance(appContext)
    val projectRepository = ProjectRepository(appContext, database.projectDao())
    val settingsDataStore = SettingsDataStore(appContext)
}
