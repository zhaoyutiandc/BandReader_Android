package com.example.bandReader.data

import androidx.room.PrimaryKey
import androidx.room.ColumnInfo
import androidx.room.Entity
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Entity(tableName = "book")
@Serializable
data class Book(
    @PrimaryKey(autoGenerate = true)
    var id: Int = 0,
    @ColumnInfo(name = "name")
    var name: String,
    @ColumnInfo(name = "chapters")
    var chapters: Int,
    @ColumnInfo(name = "pages")
    var pages: Int,
    @ColumnInfo(name = "synced")
    var synced: Boolean = false,
)

fun Book.toJsonString():String{
    return Json.encodeToString(this)
}