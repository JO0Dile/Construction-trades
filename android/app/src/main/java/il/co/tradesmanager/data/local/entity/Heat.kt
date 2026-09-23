package il.co.tradesmanager.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One heat check: what the air was, what that means for a body working in it,
 * and what the site did about it.
 *
 * The band and the index are stored as they were worked out, not recomputed
 * on reading. A record is what somebody stood behind at eleven in the morning,
 * and a better formula next year must not quietly move last August's reading
 * from one band to another.
 *
 * Nothing deletes or edits these. See core.safety.Heat for the rules.
 */
@Entity(
    tableName = "heat_checks",
    // Every read is one firm's checks, newest first.
    indices = [Index("checkedAt")],
)
data class HeatCheckEntity(
    @PrimaryKey val id: String,
    /** The site, worked out from who is checked in, as a roll call does it. */
    val projectId: String? = null,
    /** The firm, or null for somebody working alone. */
    val companyId: String? = null,
    val checkedAt: Long,
    /** Shade temperature, as read. */
    val temperatureC: Double,
    val humidityPercent: Double,
    /** Work in full sun: the band was taken from the worst case, not the shade. */
    val inSun: Boolean,
    /** The heat index itself, the shade figure, whether or not [inSun]. */
    val heatIndexC: Double,
    /** NONE to EXTREME_DANGER — see core.safety.Heat.Band. As judged at the time. */
    val band: String,
    /**
     * What was actually done — see core.safety.Heat.Measure. Empty is a real
     * answer and is recorded as one: a register that refused "nothing done"
     * would hide exactly the days somebody later needs to see.
     */
    val measures: List<String> = emptyList(),
    val note: String? = null,
    val checkedByAccountId: String,
    /** As it read at the time. */
    val checkedByName: String,
)
