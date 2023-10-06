package com.example.bandReader.data

import kotlinx.serialization.Serializable

@Serializable
sealed class BandMessage(
    var type: String,
) {
    @Serializable
    data class AddBook(var content: Book) : BandMessage("add_book")

    @Serializable
    data class AddChapter(var content: ChapterByChunk) : BandMessage("add_chapter")

    @Serializable
    data class BookInfo(var content: String = "") : BandMessage("book_info")
    @Serializable
    data class UpdateBook(var content: Book) : BandMessage("update_book")
    @Serializable
    data class UpdateCover(var content: Cover) : BandMessage("update_cover")
}
