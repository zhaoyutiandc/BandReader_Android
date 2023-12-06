package com.example.bandReader.data

import android.net.Uri
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.net.URI
import java.util.Base64

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
    @ColumnInfo(name = "list")
    var list: String = "",
)

@Serializable
data class ChapterByChunk(
    var id: Int = 0,
    var index: Int = 0,
    var bookId: Int = 0,
    var name: String = "",
    var content: String,
    var paging: Int = 0,
    var sync: Boolean = false,
    var first: Boolean = true,
    var last: Boolean = true,
    @Transient
    var raw:Chapter? = null
)
fun Chapter.toJsonString(): String {
    return Json.encodeToString(this)
}


fun Chapter.toChunk(chunSize:Int=2000): List<ChapterByChunk> {
    val chunks = content.chunked(chunSize)
    return chunks.mapIndexed { idx, chunk ->
        ChapterByChunk(
            id = id,
            index = index,
            bookId = bookId,
            name = name,
            content = chunk,
            paging = paging,
            sync = sync,
            first = idx == 0,
            last = idx == chunks.size - 1,
            raw = this.copy()
        )
    }
}