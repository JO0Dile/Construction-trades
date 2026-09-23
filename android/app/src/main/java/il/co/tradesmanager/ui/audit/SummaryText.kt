package il.co.tradesmanager.ui.audit

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import il.co.tradesmanager.R
import il.co.tradesmanager.core.access.Role
import il.co.tradesmanager.core.audit.Summaries
import il.co.tradesmanager.core.audit.Summary
import il.co.tradesmanager.ui.account.roleLabel

/**
 * An audit summary, in the language of whoever is reading it.
 *
 * The register stores a key and its arguments rather than a sentence, because
 * the person who writes an entry and the person who reads it are routinely
 * not the same person and routinely do not share a language. A labourer signs
 * an induction on an Arabic phone, the site manager reads the trail in
 * Hebrew, and the export goes to an inspector in English. One stored sentence
 * cannot serve all three, and for a long time this app stored the English one
 * — which meant the register it exists to produce was the least readable
 * screen in it for two of the three languages it ships in.
 *
 * **Anything not recognised is printed exactly as it is stored.** That covers
 * every row written before this existed, since English prose is not a key. It
 * covers the summaries that are data rather than words — an item's own name,
 * a reference. And it is why an unknown key falls back to the stored text and
 * never to the key: an item genuinely called "cable" is a single lower-case
 * word, and it does parse as one.
 *
 * Two entry points, because the audit trail is read in two places that do not
 * share a way of reaching resources: the screens, and the PDF handed to a
 * regulator. A document given to an inspector should not be the one place the
 * app still speaks in constants.
 */
fun summaryText(context: Context, stored: String): String {
    val parsed = Summary.parse(stored) ?: return stored
    val phrase = summaryPhrase(parsed.key) ?: return stored
    val arguments: Array<Any> = Array(parsed.arguments.size) { index ->
        parsed.arguments[index].split(LIST)
            .joinToString(LIST) { piece ->
                Summary.nested(piece)?.let { nestedText(context, it) } ?: piece
            }
    }
    // A stored row with fewer arguments than its phrase expects would throw
    // out of getString and take the whole audit screen with it. That row
    // should never exist, and the register is the last screen in this app
    // that may go blank because one line in it is malformed — so a summary
    // that will not format is shown as it was stored, which is the same
    // answer every other unrecognised row gets.
    return runCatching { context.getString(phrase, *arguments) }.getOrDefault(stored)
}

@Composable
fun summaryText(stored: String): String = summaryText(LocalContext.current, stored)

/**
 * An argument that is a key in its own right: a role, a reason, anything from
 * a fixed set. Marked with [Summary.nest] at the call site so that
 * "SITE_MANAGER" never lands untranslated in the middle of a Hebrew sentence.
 *
 * A key nothing recognises is printed as it stands rather than swallowed. It
 * is the name of something, and a half-empty line is worse than an untidy one
 * in a register somebody is trying to reconstruct a day from.
 */
private fun nestedText(context: Context, key: String): String {
    Role.entries.firstOrNull { it.name.lowercase() == key }
        ?.let { return context.getString(roleLabel(it)) }
    val phrase = summaryPhrase(key) ?: borrowedPhrase(key) ?: return key
    return context.getString(phrase)
}

@StringRes
private fun summaryPhrase(key: String): Int? = when (key) {
    Summaries.ADMITTED_AT_GATE -> R.string.summary_admitted_at_gate
    Summaries.AMENDMENT_STATUS -> R.string.summary_amendment_status
    Summaries.APPLICATION_CERTIFIED -> R.string.summary_application_certified
    Summaries.APPLICATION_PAID -> R.string.summary_application_paid
    Summaries.APPLICATION_REJECTED -> R.string.summary_application_rejected
    Summaries.APPLICATION_SUBMITTED -> R.string.summary_application_submitted
    Summaries.ASSIGNMENT_INVOICED -> R.string.summary_assignment_invoiced
    Summaries.ASSIGNMENT_STATUS -> R.string.summary_assignment_status
    Summaries.ASSIGNMENT_STATUS_REASON -> R.string.summary_assignment_status_reason
    Summaries.ATTENDEE_ADDED -> R.string.summary_attendee_added
    Summaries.ATTENDEE_REMOVED -> R.string.summary_attendee_removed
    Summaries.BACKUP_MADE -> R.string.summary_backup_made
    Summaries.BLOCKS_COPIED -> R.string.summary_blocks_copied
    Summaries.CHECKED_IN -> R.string.summary_checked_in
    Summaries.CHECKED_OUT -> R.string.summary_checked_out
    Summaries.CHECKLIST_SIGNED -> R.string.summary_checklist_signed
    Summaries.CHECKLIST_STARTED -> R.string.summary_checklist_started
    Summaries.CONCRETE_PLACED -> R.string.summary_concrete_placed
    Summaries.CONCRETE_REJECTED -> R.string.summary_concrete_rejected
    Summaries.CONCRETE_TRUCK -> R.string.summary_concrete_truck
    Summaries.CONTRACT_SIGNED -> R.string.summary_contract_signed
    Summaries.CONTRACT_VALUE_SET -> R.string.summary_contract_value_set
    Summaries.CREATED_FROM_TEMPLATE -> R.string.summary_created_from_template
    Summaries.CUSTOM_TRADE_REMOVED -> R.string.summary_custom_trade_removed
    Summaries.DAILY_LOG_SIGNED -> R.string.summary_daily_log_signed
    Summaries.DELIVERED_ON -> R.string.summary_delivered_on
    Summaries.DETAILS_CORRECTED -> R.string.summary_details_corrected
    Summaries.EQUIPMENT_SERVICED -> R.string.summary_equipment_serviced
    Summaries.EQUIPMENT_STATUS -> R.string.summary_equipment_status
    Summaries.EXCAVATION_BACKFILLED -> R.string.summary_excavation_backfilled
    Summaries.EXCAVATION_DISTURBED -> R.string.summary_excavation_disturbed
    Summaries.FIELD_ADDRESS -> R.string.summary_field_address
    Summaries.FIELD_EMAIL -> R.string.summary_field_email
    Summaries.FIELD_ID_NUMBER -> R.string.summary_field_id_number
    Summaries.FIELD_LICENCE -> R.string.summary_field_licence
    Summaries.FIELD_NAME -> R.string.summary_field_name
    Summaries.FIELD_NOTHING -> R.string.summary_field_nothing
    Summaries.FIELD_PHONE -> R.string.summary_field_phone
    Summaries.FIELD_REGISTRATION -> R.string.summary_field_registration
    Summaries.FIELD_WEBSITE -> R.string.summary_field_website
    Summaries.GOODS_RECEIVED -> R.string.summary_goods_received
    Summaries.ID_NUMBER_SET -> R.string.summary_id_number_set
    Summaries.INDUCTION_SIGNED -> R.string.summary_induction_signed
    Summaries.INSPECTION_FAILED -> R.string.summary_inspection_failed
    Summaries.INSPECTION_PASSED -> R.string.summary_inspection_passed
    Summaries.INVOICE_PAID -> R.string.summary_invoice_paid
    Summaries.ITEM_REMOVED -> R.string.summary_item_removed
    Summaries.JOINED_AS -> R.string.summary_joined_as
    Summaries.LEFT_COMPANY -> R.string.summary_left_company
    Summaries.LIFT_APPROVED -> R.string.summary_lift_approved
    Summaries.LIFT_DONE -> R.string.summary_lift_done
    Summaries.LIFT_ROLE_CLEARED -> R.string.summary_lift_role_cleared
    Summaries.LIFT_ROLE_SET -> R.string.summary_lift_role_set
    Summaries.MARKED_SITE_PLAN -> R.string.summary_marked_site_plan
    Summaries.MEMBER_ADDED -> R.string.summary_member_added
    Summaries.ORDER_CANCELLED -> R.string.summary_order_cancelled
    Summaries.ORDER_DUE -> R.string.summary_order_due
    Summaries.ORDER_NO_DATE -> R.string.summary_order_no_date
    Summaries.ORDER_PLACED -> R.string.summary_order_placed
    Summaries.ORDER_STATUS -> R.string.summary_order_status
    Summaries.PARTY_ADDED -> R.string.summary_party_added
    Summaries.PARTY_FIRST_TIER -> R.string.summary_party_first_tier
    Summaries.PARTY_GENERAL_CONTRACTOR -> R.string.summary_party_general_contractor
    Summaries.PARTY_SECOND_TIER -> R.string.summary_party_second_tier
    Summaries.PASSCODE_CHANGED -> R.string.summary_passcode_changed
    Summaries.PASSCODE_REMOVED -> R.string.summary_passcode_removed
    Summaries.PERMIT_CANCELLED -> R.string.summary_permit_cancelled
    Summaries.PERMIT_ISSUED -> R.string.summary_permit_issued
    Summaries.PERMIT_SIGNED_BACK -> R.string.summary_permit_signed_back
    Summaries.PERMIT_STOPPED -> R.string.summary_permit_stopped
    Summaries.PHOTO_REMOVED -> R.string.summary_photo_removed
    Summaries.PLANT_TO_SITE -> R.string.summary_plant_to_site
    Summaries.PLANT_TO_YARD -> R.string.summary_plant_to_yard
    Summaries.POUR_FINISHED -> R.string.summary_pour_finished
    Summaries.PROFILE_PUBLISHED -> R.string.summary_profile_published
    Summaries.REPORTS_TO -> R.string.summary_reports_to
    Summaries.REPORTS_TO_NOBODY -> R.string.summary_reports_to_nobody
    Summaries.RESTOCKED -> R.string.summary_restocked
    Summaries.ROLE_SET -> R.string.summary_role_set
    Summaries.SCAFFOLD_ALTERED -> R.string.summary_scaffold_altered
    Summaries.SCAFFOLD_DISMANTLED -> R.string.summary_scaffold_dismantled
    Summaries.SERVICES_LOCATED -> R.string.summary_services_located
    Summaries.SNAG_CLAIMED_FIXED -> R.string.summary_snag_claimed_fixed
    Summaries.SNAG_STATUS -> R.string.summary_snag_status
    Summaries.STATUS_FIXED -> R.string.summary_status_fixed
    Summaries.STATUS_IN_PROGRESS -> R.string.summary_status_in_progress
    Summaries.STATUS_PROPOSED -> R.string.summary_status_proposed
    Summaries.STATUS_WITHDRAWN -> R.string.summary_status_withdrawn
    Summaries.STOCK_MOVED -> R.string.summary_stock_moved
    Summaries.TASK_DONE -> R.string.summary_task_done
    Summaries.TASK_REOPENED -> R.string.summary_task_reopened
    Summaries.TASK_STAGE_CLEARED -> R.string.summary_task_stage_cleared
    Summaries.TASK_STAGE_SET -> R.string.summary_task_stage_set
    Summaries.TIME_BLOCK_REMOVED -> R.string.summary_time_block_removed
    Summaries.TRADE_CLEARED -> R.string.summary_trade_cleared
    Summaries.TRADE_SET -> R.string.summary_trade_set
    Summaries.TW_CHECKED_BY -> R.string.summary_tw_checked_by
    Summaries.TW_ERECTED -> R.string.summary_tw_erected
    Summaries.TW_INSPECTED -> R.string.summary_tw_inspected
    Summaries.TW_LOADED -> R.string.summary_tw_loaded
    Summaries.TW_RELEASED -> R.string.summary_tw_released
    Summaries.TW_STRUCK -> R.string.summary_tw_struck
    Summaries.USED_ON_SITE -> R.string.summary_used_on_site
    Summaries.VARIATION_STATUS -> R.string.summary_variation_status
    Summaries.VIOLATION_CONFIRMED -> R.string.summary_violation_confirmed
    else -> null
}

/**
 * Between several keys in one argument.
 *
 * "Details corrected for Yossi: name, phone" is one argument holding two
 * field names, not two arguments — how many changed is not known until
 * somebody changes them. Each piece is looked up on its own, and a piece that
 * is not marked as a key is left exactly as it is, so an argument that merely
 * contains a comma is untouched.
 */
private const val LIST = ", "

/**
 * A status or a position on a job, borrowed from the word the screens use.
 *
 * These are not written again here. "In the yard" already exists, in three
 * languages, because the plant register shows it on a chip — and a register
 * that said something slightly different from the screen it came from would
 * be worse than one that repeated it, because somebody would eventually have
 * to work out which of the two was right.
 *
 * Keyed by the name of the string rather than by the status constant, so the
 * mapping is a line anybody can read against the catalogue. The repositories
 * write these keys through a summaryKey function beside each status
 * vocabulary.
 */
@StringRes
private fun borrowedPhrase(key: String): Int? = when (key) {
    "plant_available" -> R.string.plant_available
    "plant_on_site" -> R.string.plant_on_site
    "plant_maintenance" -> R.string.plant_maintenance
    "plant_off_hire" -> R.string.plant_off_hire
    "po_status_draft" -> R.string.po_status_draft
    "po_status_ordered" -> R.string.po_status_ordered
    "po_status_part" -> R.string.po_status_part
    "po_status_received" -> R.string.po_status_received
    "po_status_cancelled" -> R.string.po_status_cancelled
    "snag_state_open" -> R.string.snag_state_open
    "snag_state_closed" -> R.string.snag_state_closed
    "snag_state_rejected" -> R.string.snag_state_rejected
    "wp_status_draft" -> R.string.wp_status_draft
    "wp_status_offered" -> R.string.wp_status_offered
    "wp_status_accepted" -> R.string.wp_status_accepted
    "wp_status_declined" -> R.string.wp_status_declined
    "wp_status_progress" -> R.string.wp_status_progress
    "wp_status_submitted" -> R.string.wp_status_submitted
    "wp_status_rejected" -> R.string.wp_status_rejected
    "wp_status_approved" -> R.string.wp_status_approved
    "wp_status_cancelled" -> R.string.wp_status_cancelled
    "pay_status_draft" -> R.string.pay_status_draft
    "pay_status_submitted" -> R.string.pay_status_submitted
    "pay_status_certified" -> R.string.pay_status_certified
    "pay_status_paid" -> R.string.pay_status_paid
    "pay_status_rejected" -> R.string.pay_status_rejected
    "party_client" -> R.string.party_client
    "party_consultant" -> R.string.party_consultant
    "party_gc" -> R.string.party_gc
    "party_first" -> R.string.party_first
    "party_second" -> R.string.party_second
    "party_supplier" -> R.string.party_supplier
    else -> null
}
