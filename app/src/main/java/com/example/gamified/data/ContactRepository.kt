package com.example.gamified.data

import kotlinx.coroutines.flow.Flow

class ContactRepository(private val dao: ContactDao) {
    fun getAll(): Flow<List<Contact>> = dao.getAll()
    fun search(query: String): Flow<List<Contact>> = dao.search("%$query%")

    suspend fun getById(id: Long) = dao.getById(id)

    suspend fun add(contact: Contact) = dao.insert(contact)
    suspend fun update(contact: Contact) = dao.update(contact)
    suspend fun delete(contact: Contact) = dao.delete(contact)
}
