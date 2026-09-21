package com.example.assignmentapp.data

import androidx.room.Entity
import androidx.room.PrimaryKey

sealed interface MeasurementState {
    data object Idle : MeasurementState
    data object Collecting : MeasurementState
    data object Processing : MeasurementState
    data class Success(val value: Double) : MeasurementState
    data class Error(val message: String) : MeasurementState
}

enum class Symptom(val displayName: String) {
    NAUSEA("Nausea"),
    HEADACHE("Headache"),
    DIARRHEA("Diarrhea"),
    SORE_THROAT("Sore throat"),
    FEVER("Fever"),
    MUSCLE_ACHE("Muscle ache"),
    LOSS_OF_SMELL_OR_TASTE("Loss of smell or taste"),
    COUGH("Cough"),
    SHORTNESS_OF_BREATH("Shortness of breath"),
    FEELING_TIRED("Feeling tired")
}

data class HealthSession(
    val heartRate: Double? = null,
    val respiratoryRate: Double? = null,
    val symptomRatings: Map<Symptom, Int> = Symptom.entries.associateWith { 0 }
) {
    init {
        require(symptomRatings.values.all { it in 0..5 }) {
            "Symptom ratings must be between 0 and 5."
        }
    }
}

@Entity(tableName = "health_records")
data class HealthRecordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val heartRate: Double,
    val respiratoryRate: Double,
    val nausea: Int,
    val headache: Int,
    val diarrhea: Int,
    val soreThroat: Int,
    val fever: Int,
    val muscleAche: Int,
    val lossOfSmellOrTaste: Int,
    val cough: Int,
    val shortnessOfBreath: Int,
    val feelingTired: Int
)
