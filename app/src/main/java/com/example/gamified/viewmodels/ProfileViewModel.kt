// ProfileViewModel.kt
package com.example.gamified.viewmodels

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class ProfileViewModel : ViewModel() {
    private val auth: FirebaseAuth = Firebase.auth
    private val db = FirebaseFirestore.getInstance()

    private val _userData = MutableLiveData<Map<String, Any>?>()
    val userData: LiveData<Map<String, Any>?> = _userData

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    fun loadUserData(userId: String) = viewModelScope.launch {
        try {
            _isLoading.value = true
            _errorMessage.value = null

            val document = db.collection("users").document(userId).get().await()
            if (document.exists()) {
                _userData.value = document.data
            } else {
                _errorMessage.value = "User data not found"
            }
        } catch (e: Exception) {
            _errorMessage.value = "Failed to load user data: ${e.message}"
        } finally {
            _isLoading.value = false
        }
    }

    fun updateUserProfile(userId: String, displayName: String, username: String, bio: String) = viewModelScope.launch {
        try {
            _isLoading.value = true
            _errorMessage.value = null

            val updates = hashMapOf<String, Any>()
            if (displayName.isNotEmpty()) updates["displayName"] = displayName
            if (username.isNotEmpty()) updates["username"] = username
            if (bio.isNotEmpty()) updates["bio"] = bio

            db.collection("users").document(userId).update(updates).await()

            // Reload user data after update
            loadUserData(userId)
        } catch (e: Exception) {
            _errorMessage.value = "Failed to update profile: ${e.message}"
        } finally {
            _isLoading.value = false
        }
    }
}