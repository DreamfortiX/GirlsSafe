package com.example.gamified.contacts

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.gamified.data.AppDatabase
import com.example.gamified.data.Contact
import com.example.gamified.data.ContactRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class EditContactViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = ContactRepository(AppDatabase.get(app).contactDao())

    suspend fun load(id: Long): Contact? = withContext(Dispatchers.IO) { repo.getById(id) }

    suspend fun insert(contact: Contact): Long = withContext(Dispatchers.IO) { repo.add(contact) }

    suspend fun update(contact: Contact) = withContext(Dispatchers.IO) { repo.update(contact) }

    suspend fun delete(contact: Contact) = withContext(Dispatchers.IO) { repo.delete(contact) }
}
