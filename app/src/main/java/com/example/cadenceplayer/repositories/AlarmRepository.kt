package com.example.cadenceplayer.repositories

import com.example.cadenceplayer.room.Alarm
import com.example.cadenceplayer.room.AlarmDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

class AlarmRepository(private val alarmDao: AlarmDao) {

    fun getAllAlarms(): List<Alarm> {
        return runBlocking {
            withContext(Dispatchers.Default) {
                alarmDao.getAllRows()
            }
        }
    }

    suspend fun insert(alarm: Alarm) = alarmDao.insert(alarm)

    suspend fun insertAll(alarms: List<Alarm>) = alarmDao.insertAll(alarms)

    suspend fun deleteAll() = alarmDao.deleteAll()

}