package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.model.FaceRig

@Entity(tableName = "projects")
data class TalkingProjectEntity(
    @PrimaryKey val id: String,
    val title: String,
    val imagePath: String,
    val audioPath: String? = null,
    val audioDisplayName: String? = null,
    val lastExportPath: String? = null,
    val rigJson: String,
    val updatedAt: Long = System.currentTimeMillis()
)

data class TalkingProject(
    val id: String,
    val title: String,
    val imagePath: String,
    val audioPath: String? = null,
    val audioDisplayName: String? = null,
    val lastExportPath: String? = null,
    val rig: FaceRig = FaceRig(),
    val updatedAt: Long = System.currentTimeMillis()
)
