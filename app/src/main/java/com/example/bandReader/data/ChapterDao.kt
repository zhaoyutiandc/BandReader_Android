package com.example.bandReader.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ChapterDao {
    @Query("SELECT * FROM chapter")
    fun getAll(): Flow<List<Chapter>>
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(chapter: Chapter)
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(chapters: List<Chapter>)
    @Delete
    suspend fun delete(chapter: Chapter): Int
    @Query("SELECT * FROM chapter WHERE bookId = :bookId order by `index`")
    fun getChaptersByBookId(bookId: Int): Flow<List<Chapter>>
    //un sync chapters
    @Query("SELECT * FROM chapter WHERE bookId = :bookId and sync = 0")
    suspend fun getUnSyncChapters(bookId:Int): List<Chapter>
    @Query("SELECT COUNT(*) FROM chapter WHERE bookId = :bookId")
    suspend fun countChapterBy(bookId: Int):Int
    @Query("SELECT COUNT(*) FROM chapter WHERE bookId = :bookId and sync = 1")
    suspend fun countSynced(bookId: Int):Int
    @Update
    suspend fun update(chapter:Chapter)
    @Query("DELETE FROM chapter WHERE bookId = :bookId")
    suspend fun deleteBy(bookId: Int)
    @Query("UPDATE chapter SET sync = 0 WHERE bookId = :bookId")
    suspend fun setAllUnSync(bookId: Int)
}