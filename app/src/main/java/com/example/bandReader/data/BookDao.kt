package com.example.bandReader.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import com.example.bandReader.data.Book

@Dao
interface BookDao {
    //crud
    @Query("SELECT * FROM book")
    suspend fun getAll(): List<Book>
    @Query("SELECT * FROM book order by id DESC")
    fun getAllFlow(): Flow<List<Book>>
    //insert
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(book: Book): Long

    @Update
    suspend fun update(book: Book)

    //delete
    @Delete
    suspend fun delete(book: Book)

    //get book by name
    @Query("SELECT * FROM book WHERE name = :name  order by `id` desc")
    fun getBookByName(name: String): Book?

    @Query("SELECT * FROM book WHERE id = :id")
    suspend fun getBookById(id: Int): Book?
}