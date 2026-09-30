package com.dpad.messaging.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "drafts")
data class Draft(
    @PrimaryKey
    @ColumnInfo(name = "thread_id")
    val threadId: Long,

    @ColumnInfo(name = "body")
    val body: String,

    @ColumnInfo(name = "attachment_uris_json")
    val attachmentUrisJson: String = "[]",

    @ColumnInfo(name = "subscription_id")
    val subscriptionId: Int = -1,

    @ColumnInfo(name = "date")
    val date: Long = System.currentTimeMillis()
)
