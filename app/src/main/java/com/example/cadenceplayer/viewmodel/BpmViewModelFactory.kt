package com.example.cadenceplayer.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class BpmViewModelFactory(
    private val application: Application
) : ViewModelProvider.AndroidViewModelFactory(application) {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(BpmViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return BpmViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}