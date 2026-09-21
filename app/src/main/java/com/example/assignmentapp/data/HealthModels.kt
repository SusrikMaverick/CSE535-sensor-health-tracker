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

fun HealthSession.toEntity(
    timestamp: Long = System.currentTimeMillis()
): HealthRecordEntity = HealthRecordEntity(
    timestamp = timestamp,
    heartRate = requireNotNull(heartRate) { "Heart rate is required." },
    respiratoryRate = requireNotNull(respiratoryRate) { "Respiratory rate is required." },
    nausea = symptomRatings[Symptom.NAUSEA] ?: 0,
    headache = symptomRatings[Symptom.HEADACHE] ?: 0,
    diarrhea = symptomRatings[Symptom.DIARRHEA] ?: 0,
    soreThroat = symptomRatings[Symptom.SORE_THROAT] ?: 0,
    fever = symptomRatings[Symptom.FEVER] ?: 0,
    muscleAche = symptomRatings[Symptom.MUSCLE_ACHE] ?: 0,
    lossOfSmellOrTaste = symptomRatings[Symptom.LOSS_OF_SMELL_OR_TASTE] ?: 0,
    cough = symptomRatings[Symptom.COUGH] ?: 0,
    shortnessOfBreath = symptomRatings[Symptom.SHORTNESS_OF_BREATH] ?: 0,
    feelingTired = symptomRatings[Symptom.FEELING_TIRED] ?: 0
)

fun HealthRecordEntity.toSession(): HealthSession = HealthSession(
    heartRate = heartRate,
    respiratoryRate = respiratoryRate,
    symptomRatings = mapOf(
        Symptom.NAUSEA to nausea,
        Symptom.HEADACHE to headache,
        Symptom.DIARRHEA to diarrhea,
        Symptom.SORE_THROAT to soreThroat,
        Symptom.FEVER to fever,
        Symptom.MUSCLE_ACHE to muscleAche,
        Symptom.LOSS_OF_SMELL_OR_TASTE to lossOfSmellOrTaste,
        Symptom.COUGH to cough,
        Symptom.SHORTNESS_OF_BREATH to shortnessOfBreath,
        Symptom.FEELING_TIRED to feelingTired
    )
)
