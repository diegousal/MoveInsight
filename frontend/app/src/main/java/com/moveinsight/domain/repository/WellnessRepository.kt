package com.moveinsight.domain.repository

import com.moveinsight.core.utils.Resource
import com.moveinsight.domain.model.Incident
import com.moveinsight.domain.model.IncidentFactors
import com.moveinsight.domain.model.ValidationResult
import com.moveinsight.domain.model.WellnessTimeline

interface WellnessRepository {
    suspend fun listIncidents()                         : Resource<List<Incident>>
    suspend fun getIncident(id: Int)                    : Resource<Incident>
    suspend fun createIncident(
        bodyZone: String, severity: Int, incidentType: String,
        startDate: String, endDate: String? = null, notes: String
    ): Resource<Incident>
    suspend fun updateIncident(
        id: Int, bodyZone: String?, severity: Int?, incidentType: String?,
        endDate: String?, notes: String?
    ): Resource<Incident>
    suspend fun recoverIncident(id: Int, endDate: String?) : Resource<Incident>
    suspend fun deleteIncident(id: Int)                 : Resource<Unit>
    suspend fun getTimeline(days: Int = 90)             : Resource<WellnessTimeline>
    suspend fun getIncidentFactors(
        incidentId: Int, windowDays: Int = 14
    ): Resource<IncidentFactors>
    suspend fun getValidation(): Resource<ValidationResult>
}
