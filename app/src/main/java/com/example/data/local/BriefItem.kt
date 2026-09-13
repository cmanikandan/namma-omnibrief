package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "brief_items")
data class BriefItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val type: String, // "X_POST" or "CONFERENCE_REPORT"
    val title: String,
    val content: String,
    val sourceOrSpeaker: String,
    val timestamp: Long = System.currentTimeMillis(),
    val tags: String = "",
    val imageCount: Int = 0,
    val hasAudio: Boolean = false,
    val audioDurationSeconds: Int = 0,
    val status: String = "Draft" // "Draft", "Approved", "Posted to X", "Exported"
)
