package il.co.tradesmanager.ui.export

import android.content.Context
import il.co.tradesmanager.R
import il.co.tradesmanager.core.evidence.HandoverPack
import il.co.tradesmanager.core.i18n.Formats
import il.co.tradesmanager.core.i18n.resolve
import il.co.tradesmanager.core.security.AuditChain
import il.co.tradesmanager.data.local.entity.AuditLogEntity
import il.co.tradesmanager.data.local.entity.ChecklistRunEntity
import il.co.tradesmanager.data.local.entity.ChecklistTemplateEntity
import il.co.tradesmanager.data.local.entity.ChecklistTemplateItemEntity
import il.co.tradesmanager.data.local.entity.InventoryItemEntity
import il.co.tradesmanager.data.local.entity.ProjectEntity
import il.co.tradesmanager.data.local.entity.ProjectMaterialEntity
import il.co.tradesmanager.data.local.entity.ProjectTaskEntity
import il.co.tradesmanager.data.repository.SafetyRepository
import il.co.tradesmanager.ui.components.unitLabel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/**
 * What can be exported, and the table it becomes.
 *
 * One type rather than one exporter per format, so the CSV and the PDF of the
 * same thing cannot drift apart in what they contain — an audit trail is only
 * worth something if the spreadsheet and the printout agree. The iOS
 * `ExportDocument` is the same shape for the same reason.
 */
sealed interface ExportDocument {

    data class Inventory(val items: List<InventoryItemEntity>) : ExportDocument

    /**
     * A job sheet: what has to happen, and what has to be there for it.
     *
     * [taskStages] maps a task id to the stage it belongs to, already
     * resolved into the reader's language. The stage names live in the
     * catalogue rather than the database, and an exporter that reached into
     * the catalogue would be an exporter that needs a container — so the
     * screen that already knows the language resolves them and hands them
     * over. A task with no stage is simply absent from the map.
     */
    data class ProjectSheet(
        val project: ProjectEntity,
        val tasks: List<ProjectTaskEntity>,
        val materials: List<ProjectMaterialEntity>,
        val taskStages: Map<String, String> = emptyMap(),
    ) : ExportDocument

    data class Checklist(
        val template: ChecklistTemplateEntity,
        val run: ChecklistRunEntity,
        val checks: List<ChecklistTemplateItemEntity>,
        val answers: Map<String, String>,
    ) : ExportDocument

    /**
     * The state of a job at handover, as a document somebody files.
     *
     * One table with a section column rather than a new multi-section exporter,
     * following [ProjectSheet]. The CSV and the PDF come from the same table
     * either way, which is the property that matters: an audit trail is worth
     * nothing if the spreadsheet and the printout disagree.
     */
    data class Handover(
        val project: ProjectEntity,
        val readiness: HandoverPack.Readiness,
        val producedByName: String,
        val producedOn: LocalDate,
    ) : ExportDocument

    /**
     * The audit trail, as a document somebody outside the app can check.
     *
     * The signature travels with each row. That is the whole point: a
     * tamper-evident log an inspector cannot take away is only evidence while
     * they are standing next to the phone. With the hashes in the file, a
     * third party can recompute the chain themselves and does not have to take
     * the app's word for the verdict.
     *
     * [verdict] is what the app made of it at the moment of export, and lands
     * in the first row — following the handover pack, because a document that
     * is skimmed rather than read should still say whether it holds up.
     */
    data class AuditTrail(
        val entries: List<AuditLogEntity>,
        val verdict: AuditChain.Verdict,
        val exportedOn: LocalDate,
    ) : ExportDocument

    /**
     * A table, plus columns only the machine-readable copy carries.
     *
     * [extraHeaders] and [extraCells] are appended to each CSV row and left
     * out of the PDF. The audit trail is why they exist: verifying a hash
     * needs every field the hash was taken over, including ones nobody reads
     * — the previous entry's hash, the raw millisecond timestamp, the payload.
     * Eleven columns on A4 is an unreadable printout, and a printout is what
     * an inspector actually reads. So the PDF keeps the six a person wants and
     * the CSV carries what a checker needs.
     *
     * Both still come from one Table, which is the property that mattered: the
     * two files cannot disagree about a value, only about how much they show.
     *
     * A row with no matching entry in [extraCells] is padded rather than
     * dropped, so a mismatch cannot silently shorten the export.
     */
    data class Table(
        val title: String,
        val headers: List<String>,
        val rows: List<List<String>>,
        val extraHeaders: List<String> = emptyList(),
        val extraCells: List<List<String>> = emptyList(),
    )

    fun table(context: Context, languageTag: String, locale: Locale): Table = when (this) {
        is Inventory -> Table(
            title = context.getString(R.string.inv_title),
            headers = listOf(
                context.getString(R.string.inv_name),
                context.getString(R.string.inv_spec),
                context.getString(R.string.inv_quantity),
                context.getString(R.string.inv_unit),
                context.getString(R.string.inv_min_stock),
                context.getString(R.string.inv_barcode),
            ),
            rows = items.map { item ->
                listOf(
                    item.names.resolve(languageTag),
                    item.spec.resolve(languageTag),
                    Formats.quantity(item.quantity, locale),
                    context.getString(unitLabel(item.unit)),
                    Formats.quantity(item.minStock, locale),
                    item.barcode.orEmpty(),
                )
            },
        )

        is ProjectSheet -> Table(
            title = project.name,
            headers = listOf(
                context.getString(R.string.action_filter),
                context.getString(R.string.inv_name),
                context.getString(R.string.proj_required_qty),
                context.getString(R.string.inv_unit),
                context.getString(R.string.task_stage),
            ),
            rows = tasks.map { task ->
                listOf(
                    context.getString(R.string.proj_tasks),
                    task.title,
                    context.getString(if (task.isDone) R.string.saf_pass else R.string.saf_fail),
                    "",
                    taskStages[task.id].orEmpty(),
                )
            } + materials.map { material ->
                listOf(
                    context.getString(R.string.proj_materials),
                    material.label,
                    Formats.quantity(material.requiredQuantity, locale),
                    context.getString(unitLabel(material.unit)),
                    "",
                )
            },
        )

        is AuditTrail -> Table(
            title = context.getString(R.string.audit_title),
            headers = listOf(
                context.getString(R.string.audit_col_seq),
                context.getString(R.string.audit_col_when),
                context.getString(R.string.audit_col_who),
                context.getString(R.string.audit_col_action),
                context.getString(R.string.audit_col_what),
                context.getString(R.string.audit_col_signature),
            ),
            rows = listOf(
                listOf(
                    "",
                    Formats.date(exportedOn, locale),
                    context.getString(R.string.audit_export_checked),
                    verdictWord(context),
                    verdictDetail(context),
                    "",
                ),
            ) + entries.map { entry ->
                val at = Instant.ofEpochMilli(entry.occurredAt).atZone(ZoneId.systemDefault())
                listOf(
                    entry.sequence.toString(),
                    Formats.dateTime(at.toLocalDate(), at.toLocalTime(), locale),
                    entry.actorName,
                    entry.action,
                    entry.summary,
                    // Blank rather than a placeholder on an unsigned row: an
                    // entry written before the chain existed has no signature,
                    // and inventing a dash for it would read like one.
                    entry.hash,
                )
            },
            // Everything else the signature was taken over, so that a third
            // party can recompute it without the app. The visible "When"
            // column is formatted for reading; occurredAtMillis is the value
            // that was actually hashed, and a checker must use that one.
            extraHeaders = listOf(
                "previousHash",
                "entityType",
                "entityId",
                "actorId",
                "payloadJson",
                "occurredAtMillis",
            ),
            extraCells = listOf(List(6) { "" }) + entries.map { entry ->
                listOf(
                    entry.previousHash,
                    entry.entityType,
                    entry.entityId,
                    entry.actorId.orEmpty(),
                    entry.payloadJson.orEmpty(),
                    entry.occurredAt.toString(),
                )
            },
        )

        is Handover -> Table(
            title = project.name,
            headers = listOf(
                context.getString(R.string.action_filter),
                context.getString(R.string.hv_outstanding),
                context.getString(R.string.hv_produced_on),
            ),
            // The first row is the verdict, so a pack that is skimmed rather
            // than read still says whether the job was finished when it was
            // printed. A pack that buries that under a list is a pack somebody
            // files believing it says the opposite.
            rows = listOf(
                listOf(
                    context.getString(R.string.hv_title),
                    context.getString(
                        if (readiness.isComplete) R.string.hv_complete else R.string.hv_interim,
                    ),
                    Formats.date(producedOn, locale) + " · " + producedByName,
                ),
            ) + readiness.outstanding.map { outstanding ->
                listOf(
                    context.getString(R.string.hv_outstanding),
                    context.getString(handoverItemLabel(outstanding.item)),
                    outstanding.count.toString(),
                )
            },
        )

        is Checklist -> Table(
            title = template.titles.resolve(languageTag),
            headers = listOf(
                context.getString(R.string.saf_title),
                context.getString(R.string.saf_critical),
                context.getString(R.string.saf_pass),
            ),
            rows = checks.map { check ->
                listOf(
                    check.texts.resolve(languageTag),
                    if (check.critical) context.getString(R.string.saf_critical) else "",
                    answerLabel(context, answers[check.id]),
                )
            } + listOf(
                listOf(
                    context.getString(R.string.saf_signed_by),
                    "",
                    run.signedByName.orEmpty(),
                ),
            ),
        )
    }

    fun fileStem(): String = ExportFormat.safeFileStem(
        when (this) {
            is Inventory -> "inventory"
            is ProjectSheet -> project.name
            is Checklist -> template.id
            is Handover -> "handover-" + project.name
            is AuditTrail -> "audit-trail-" + exportedOn
        },
    )

    /** The verdict in one word, for the first cell somebody reads. */
    private fun AuditTrail.verdictWord(context: Context): String = when (verdict) {
        is AuditChain.Verdict.Intact -> context.getString(R.string.audit_intact)
        is AuditChain.Verdict.Failed -> context.getString(R.string.audit_failed)
        AuditChain.Verdict.Empty -> context.getString(R.string.audit_empty)
    }

    /** And the sentence under it, including what could not be checked. */
    private fun AuditTrail.verdictDetail(context: Context): String = when (val v = verdict) {
        is AuditChain.Verdict.Intact -> {
            val checked = context.getString(R.string.audit_intact_detail, v.checked)
            if (v.unchained > 0) {
                checked + " " + context.getString(R.string.audit_unchained, v.unchained)
            } else {
                checked
            }
        }

        is AuditChain.Verdict.Failed -> context.getString(
            when (v.fault) {
                AuditChain.Fault.ALTERED -> R.string.audit_fault_altered
                AuditChain.Fault.BROKEN_LINK -> R.string.audit_fault_broken
                AuditChain.Fault.MISSING -> R.string.audit_fault_missing
            },
            v.sequence,
        )

        AuditChain.Verdict.Empty -> context.getString(R.string.audit_empty)
    }

    private fun answerLabel(context: Context, state: String?): String = when (state) {
        SafetyRepository.State.PASS -> context.getString(R.string.saf_pass)
        SafetyRepository.State.FAIL -> context.getString(R.string.saf_fail)
        SafetyRepository.State.NOT_APPLICABLE -> context.getString(R.string.saf_na)
        else -> "—"
    }

}

/**
 * The words for each kind of outstanding item.
 *
 * Outside the interface so it can be shared with the screen: the pack and the
 * screen must call the same thing by the same name, or somebody reads "3
 * scaffolds standing" on one and something else on the other.
 */
internal fun handoverItemLabel(item: HandoverPack.Item): Int = when (item) {
    HandoverPack.Item.BLOCKING_SNAGS -> R.string.hv_blocking_snags
    HandoverPack.Item.OPEN_PERMITS -> R.string.hv_open_permits
    HandoverPack.Item.SCAFFOLDS_STANDING -> R.string.hv_scaffolds
    HandoverPack.Item.TEMPORARY_WORKS_STANDING -> R.string.hv_temporary_works
    HandoverPack.Item.EXCAVATIONS_OPEN -> R.string.hv_excavations
    HandoverPack.Item.LIFTS_INCOMPLETE -> R.string.hv_lifts
    HandoverPack.Item.POURS_UNFINISHED -> R.string.hv_pours
    HandoverPack.Item.UNSIGNED_DAILY_LOGS -> R.string.hv_daily_logs
}
