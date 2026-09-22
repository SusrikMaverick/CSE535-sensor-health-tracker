package com.example.assignmentapp

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.assignmentapp.data.HealthDatabase
import com.example.assignmentapp.data.HealthRecordDao
import com.example.assignmentapp.data.HealthSession
import com.example.assignmentapp.data.Symptom
import com.example.assignmentapp.data.toEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HealthDatabaseTest {
    private lateinit var database: HealthDatabase
    private lateinit var dao: HealthRecordDao

    @Before
    fun createDatabase() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, HealthDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.healthRecordDao()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun insertCountAndDeleteRecord() = runBlocking {
        val record = HealthSession(
            heartRate = 72.0,
            respiratoryRate = 16.0,
            symptomRatings = Symptom.entries.associateWith { it.ordinal % 6 }
        ).toEntity(timestamp = 123L)

        assertEquals(0, dao.count())
        val insertedId = dao.insert(record)
        assertTrue(insertedId > 0)
        assertEquals(1, dao.count())
        assertEquals(record.copy(id = insertedId), dao.getAll().single())
        assertEquals(1, dao.deleteAll())
        assertEquals(0, dao.count())
    }
}
