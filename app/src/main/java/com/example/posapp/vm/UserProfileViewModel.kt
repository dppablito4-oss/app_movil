package com.example.posapp.vm

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.posapp.data.UserPreferencesRepository
import com.example.posapp.data.UserProfile
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class UserProfileViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = UserPreferencesRepository(application.applicationContext)

    val profile: StateFlow<UserProfile?> = repo.profileFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun saveProfile(profile: UserProfile, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            repo.saveProfile(profile)
            onComplete()
        }
    }
}
