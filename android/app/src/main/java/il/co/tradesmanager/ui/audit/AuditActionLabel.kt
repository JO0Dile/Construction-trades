package il.co.tradesmanager.ui.audit

import androidx.annotation.StringRes
import il.co.tradesmanager.R
import il.co.tradesmanager.data.repository.AuditTrail

/**
 * The word for what happened, in the reader's language.
 *
 * The audit screen and the exported document both printed `entry.action`
 * straight out of the row: `SIGN_OFF`, `STOCK_CHANGE`, in an app whose whole
 * point is that a Hebrew- or Arabic-speaking site manager can read it. The
 * words existed and were translated into all three languages from the day the
 * trail was built; nothing referenced them.
 *
 * Null rather than a fallback word when this version has no sentence for an
 * action. A trail restored from a backup taken by a newer version, or written
 * by one, can carry an action this build has never heard of — and showing it
 * as "Updated" would be this screen quietly saying something the record does
 * not. The caller prints the stored value instead, which is ugly and true.
 */
@StringRes
fun auditActionLabel(action: String): Int? = when (action) {
    AuditTrail.Action.CREATE -> R.string.audit_created
    AuditTrail.Action.UPDATE -> R.string.audit_updated
    AuditTrail.Action.DELETE -> R.string.audit_deleted
    AuditTrail.Action.STOCK_CHANGE -> R.string.audit_stock_change
    AuditTrail.Action.SIGN_OFF -> R.string.audit_signed
    AuditTrail.Action.EXPORT -> R.string.audit_exported
    AuditTrail.Action.PURGE -> R.string.audit_purged
    else -> null
}
