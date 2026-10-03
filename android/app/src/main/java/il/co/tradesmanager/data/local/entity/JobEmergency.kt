package il.co.tradesmanager.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One job's emergency sheet: the hospital, the assembly point, the first
 * aiders, the number to ring on site and the shut-offs. One row per job,
 * replaced when it changes; the audit trail keeps that it did. See
 * core.safety.EmergencySheet.
 */
@Entity(tableName = "job_emergency")
data class JobEmergencyEntity(
    @PrimaryKey val projectId: String,
    val hospitalName: String? = null,
    val hospitalAddress: String? = null,
    val hospitalPhone: String? = null,
    val assemblyPoint: String? = null,
    /** Names, as they would be shouted: "Yossi (crane), Samir (formwork)". */
    val firstAiders: String? = null,
    val siteContactName: String? = null,
    val siteContactPhone: String? = null,
    val electricityShutOff: String? = null,
    val waterShutOff: String? = null,
    val gasShutOff: String? = null,
    val notes: String? = null,
    val updatedAt: Long,
    val updatedByName: String,
)
