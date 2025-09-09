package com.example.gamified.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ContactDao {
    @Query("SELECT * FROM contacts ORDER BY firstName COLLATE NOCASE ASC")
    fun getAll(): Flow<List<Contact>>

    @Query("SELECT * FROM contacts WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): Contact?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(contact: Contact): Long

    @Update
    suspend fun update(contact: Contact)

    @Delete
    suspend fun delete(contact: Contact)

    @Query("SELECT * FROM contacts WHERE firstName LIKE :q OR lastName LIKE :q OR phone LIKE :q ORDER BY firstName COLLATE NOCASE")
    fun search(q: String): Flow<List<Contact>>
}
