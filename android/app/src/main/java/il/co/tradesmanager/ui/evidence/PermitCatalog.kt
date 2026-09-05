package il.co.tradesmanager.ui.evidence

import il.co.tradesmanager.R
import il.co.tradesmanager.core.evidence.Permits

/**
 * What each kind of permit consists of.
 *
 * The precautions live here, as string resources, because they are translated
 * text — and they are copied onto the permit as resolved text at the moment it
 * is raised, in the language the issuer was reading. A permit is a record of
 * what somebody read and ticked; it must not change its meaning when a later
 * version of this list rewords a line.
 */
data class PermitKind(
    val type: String,
    val titleRes: Int,
    val precautionRes: List<Int>,
)

val PERMIT_KINDS: List<PermitKind> = listOf(
    PermitKind(
        type = Permits.Type.HOT_WORK,
        titleRes = R.string.ptw_type_hot,
        precautionRes = listOf(
            R.string.ptw_hot_1,
            R.string.ptw_hot_2,
            R.string.ptw_hot_3,
            R.string.ptw_hot_4,
        ),
    ),
    PermitKind(
        type = Permits.Type.HEIGHT,
        titleRes = R.string.ptw_type_height,
        precautionRes = listOf(
            R.string.ptw_height_1,
            R.string.ptw_height_2,
            R.string.ptw_height_3,
            R.string.ptw_height_4,
        ),
    ),
    PermitKind(
        type = Permits.Type.CONFINED_SPACE,
        titleRes = R.string.ptw_type_confined,
        precautionRes = listOf(
            R.string.ptw_confined_1,
            R.string.ptw_confined_2,
            R.string.ptw_confined_3,
            R.string.ptw_confined_4,
        ),
    ),
    PermitKind(
        type = Permits.Type.EXCAVATION,
        titleRes = R.string.ptw_type_excavation,
        precautionRes = listOf(
            R.string.ptw_exc_1,
            R.string.ptw_exc_2,
            R.string.ptw_exc_3,
            R.string.ptw_exc_4,
        ),
    ),
    PermitKind(
        type = Permits.Type.ELECTRICAL,
        titleRes = R.string.ptw_type_electrical,
        precautionRes = listOf(
            R.string.ptw_elec_1,
            R.string.ptw_elec_2,
            R.string.ptw_elec_3,
            R.string.ptw_elec_4,
        ),
    ),
)

/**
 * The title for a permit type, including one written by a version of the app
 * that knew a type this one does not. Falling back to the generic heading
 * keeps an old permit readable rather than showing an empty row.
 */
fun permitTypeLabel(type: String): Int =
    PERMIT_KINDS.firstOrNull { it.type == type }?.titleRes ?: R.string.ptw_title

fun permitStateLabel(state: Permits.State): Int = when (state) {
    Permits.State.DRAFT -> R.string.ptw_state_draft
    Permits.State.PENDING -> R.string.ptw_state_pending
    Permits.State.ACTIVE -> R.string.ptw_state_active
    Permits.State.EXPIRING -> R.string.ptw_state_expiring
    Permits.State.EXPIRED -> R.string.ptw_state_expired
    Permits.State.CLOSED -> R.string.ptw_state_closed
    Permits.State.CANCELLED -> R.string.ptw_state_cancelled
}
