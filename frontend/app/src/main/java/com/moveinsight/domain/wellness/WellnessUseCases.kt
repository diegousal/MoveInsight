package com.moveinsight.domain.wellness

import com.moveinsight.core.utils.Resource
import com.moveinsight.domain.model.Incident
import com.moveinsight.domain.model.IncidentFactors
import com.moveinsight.domain.model.ValidationResult
import com.moveinsight.domain.model.WellnessTimeline
import com.moveinsight.domain.repository.WellnessRepository
import javax.inject.Inject

class GetTimelineUseCase @Inject constructor(
    private val repo: WellnessRepository
) {
    suspend operator fun invoke(days: Int = 90): Resource<WellnessTimeline> =
        repo.getTimeline(days)
}

class ListIncidentsUseCase @Inject constructor(
    private val repo: WellnessRepository
) {
    suspend operator fun invoke(): Resource<List<Incident>> = repo.listIncidents()
}

class GetIncidentUseCase @Inject constructor(
    private val repo: WellnessRepository
) {
    suspend operator fun invoke(id: Int): Resource<Incident> = repo.getIncident(id)
}

class CreateIncidentUseCase @Inject constructor(
    private val repo: WellnessRepository
) {
    suspend operator fun invoke(
        bodyZone     : String,
        severity     : Int,
        incidentType : String,
        startDate    : String,
        endDate      : String? = null,
        notes        : String  = ""
    ): Resource<Incident> {
        if (bodyZone.isBlank()) return Resource.Error("Selecciona una zona corporal.")
        if (severity !in 0..10) return Resource.Error("La severidad debe estar entre 0 y 10.")
        return repo.createIncident(bodyZone, severity, incidentType, startDate, endDate, notes)
    }
}

class UpdateIncidentUseCase @Inject constructor(
    private val repo: WellnessRepository
) {
    suspend operator fun invoke(
        id: Int, bodyZone: String? = null, severity: Int? = null,
        incidentType: String? = null, endDate: String? = null, notes: String? = null
    ): Resource<Incident> = repo.updateIncident(id, bodyZone, severity, incidentType, endDate, notes)
}

class RecoverIncidentUseCase @Inject constructor(
    private val repo: WellnessRepository
) {
    suspend operator fun invoke(id: Int, endDate: String? = null): Resource<Incident> =
        repo.recoverIncident(id, endDate)
}

class DeleteIncidentUseCase @Inject constructor(
    private val repo: WellnessRepository
) {
    suspend operator fun invoke(id: Int): Resource<Unit> = repo.deleteIncident(id)
}

class GetIncidentFactorsUseCase @Inject constructor(
    private val repo: WellnessRepository
) {
    suspend operator fun invoke(
        incidentId: Int, windowDays: Int = 14
    ): Resource<IncidentFactors> = repo.getIncidentFactors(incidentId, windowDays)
}

class GetValidationUseCase @Inject constructor(
    private val repo: WellnessRepository
) {
    suspend operator fun invoke(): Resource<ValidationResult> = repo.getValidation()
}
