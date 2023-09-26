package com.example.bandReader.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

@Entity(tableName = "chapter")
@Serializable
data class Chapter(
    @PrimaryKey(autoGenerate = true)
    var id: Int = 0,
    @ColumnInfo(name = "index")
    var index: Int,
    @ColumnInfo(name = "bookId")
    var bookId: Int,
    @ColumnInfo(name = "name")
    var name: String,
    @ColumnInfo(name = "content")
    var content: String,
    @ColumnInfo(name = "paging")
    var paging: Int,
    @ColumnInfo(name = "sync")
    var sync: Boolean = false,
)

fun Chapter.toJsonString():String{
    return Json.encodeToString(this)
}