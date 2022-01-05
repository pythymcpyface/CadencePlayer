package com.example.cadenceplayer.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import com.example.cadenceplayer.R
import com.example.cadenceplayer.repositories.BpmRepository
import com.example.cadenceplayer.room.BpmDatabase
import com.example.cadenceplayer.room.BpmEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class BpmViewModel(application: Application): AndroidViewModel(application) {

    private val bpmRepository: BpmRepository

    val allBpmEntries: LiveData<List<BpmEntry>>

    init {
        val bpmEntryDao = BpmDatabase.getDatabase(application, GlobalScope).bpmEntryDao()
        val sampleRate = application.resources.getInteger(R.integer.sample_rate)
        bpmRepository = BpmRepository(bpmEntryDao, sampleRate)
        allBpmEntries = bpmRepository.allBpmEntries
    }

    fun insert(bpmEntry: BpmEntry) = viewModelScope.launch(Dispatchers.IO) {bpmRepository.insert(bpmEntry)}

}