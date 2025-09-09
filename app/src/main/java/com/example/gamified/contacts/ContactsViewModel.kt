package com.example.gamified.contacts

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.example.gamified.data.AppDatabase
import com.example.gamified.data.Contact
import com.example.gamified.data.ContactRepository
import kotlinx.coroutines.launch

class ContactsViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = ContactRepository(AppDatabase.get(app).contactDao())
    val contacts: LiveData<List<Contact>> = repo.getAll().asLiveData()

    fun delete(contact: Contact) = viewModelScope.launch { repo.delete(contact) }

    fun add(
        firstName: String,
        lastName: String?,
        phone: String,
        email: String?,
        address: String?,
        onResult: (success: Boolean, error: String?) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val contact = Contact(
                    firstName = firstName,
                    lastName = lastName?.takeIf { it.isNotBlank() },
                    phone = phone,
                    email = email?.takeIf { it.isNotBlank() },
                    address = address?.takeIf { it.isNotBlank() }
                )
                repo.add(contact)
                onResult(true, null)
            } catch (e: Exception) {
                val msg = e.message ?: "Failed to add contact"
                onResult(false, msg)
            }
        }
    }
}
