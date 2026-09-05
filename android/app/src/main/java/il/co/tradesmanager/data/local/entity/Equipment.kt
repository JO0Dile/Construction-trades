package il.co.tradesmanager.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Plant: the kit a firm owns or hires, as opposed to the stock it consumes.
 *
 * Separate from inventory on purpose. A box of wall plugs is used up and
 * reordered; a breaker or a dumper is one thing with a serial number that is
 * somewhere, belongs to somebody, and costs money every day it is on hire.
 * Modelling them as the same row would mean either "quantity 1" everywhere or
 * a stock item that cannot be serviced.
 *
 * [hireRatePerDay] is what turns hired plant into a number the Money lens can
 * use without anyone typing it twice — the same rule as priced material lines.
 */
@Entity(
    tableName = "equipment",
    indices = [Index("assignedProjectId"), Index("supplierId")],
)
data class EquipmentEntity(
    @PrimaryKey val id: String,
    val name: String,
    /** OWNED or HIRED — see EquipmentRepository. */
    val ownership: String,
    /** AVAILABLE, ON_SITE, MAINTENANCE or OFF_HIRE. */
    val status: String,
    val serialNumber: String?,
    val supplierId: String?,
    /** Which job it is on, if any. Null means it is in the yard. */
    val assignedProjectId: String?,
    /** Net of VAT, like every other figure the Money lens reads. */
    val hireRatePerDay: Double?,
    val hireStartedOn: Long?,
    val hireEndsOn: Long?,
    val lastServicedOn: Long?,
    /** Warned about on the same thirty-day window as a person's ticket. */
    val serviceDueOn: Long?,
    val notes: String?,
    val createdAt: Long,
    val updatedAt: Long,
)
