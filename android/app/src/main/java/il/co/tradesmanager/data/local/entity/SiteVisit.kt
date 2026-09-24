package il.co.tradesmanager.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Somebody on a job who does not work there: an inspector, the client, an
 * engineer, a driver waiting to be unloaded.
 *
 * A visit is open until [leftAt] is set, and an open visit is a name on the
 * next roll call. That is most of the reason the table exists -- a visitor is
 * the person least likely to know where the assembly point is and most likely
 * to be forgotten at it, because nobody on the site knows their face.
 *
 * See core.safety.Visits for the rules.
 */
@Entity(
    tableName = "site_visits",
    // Read by job for the log, and by "still here" for the roll call.
    indices = [Index("projectId"), Index("leftAt")],
)
data class SiteVisitEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val companyId: String?,
    val name: String,
    /** Which firm or office they are from: "Ministry of Labour", "the client". */
    val organisation: String? = null,
    val phone: String? = null,
    /** Who on site they came to see. */
    val hostName: String? = null,
    /** Whether somebody told them the site rules and where the assembly point is. */
    val briefed: Boolean,
    /** Their own signature, if they gave one. See core.security.Signature. */
    val signature: String? = null,
    val arrivedAt: Long,
    val leftAt: Long? = null,
    val signedInByAccountId: String?,
    val signedInByName: String,
)
