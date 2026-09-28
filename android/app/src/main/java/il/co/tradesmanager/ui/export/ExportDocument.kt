package il.co.tradesmanager.ui.export

import android.content.Context
import il.co.tradesmanager.R
import il.co.tradesmanager.ui.audit.auditActionLabel
import il.co.tradesmanager.ui.audit.summaryText
import il.co.tradesmanager.core.evidence.Complaints
import il.co.tradesmanager.core.evidence.HandoverPack
import il.co.tradesmanager.core.evidence.Inspections
import il.co.tradesmanager.core.i18n.Formats
import il.co.tradesmanager.core.i18n.resolve
import il.co.tradesmanager.core.safety.Ppe
import il.co.tradesmanager.core.safety.Risks
import il.co.tradesmanager.core.safety.Substances
import il.co.tradesmanager.core.security.AuditChain
import il.co.tradesmanager.core.work.Delays
import il.co.tradesmanager.core.work.Submittals
import il.co.tradesmanager.data.local.entity.AuditLogEntity
import il.co.tradesmanager.data.local.entity.ChecklistRunEntity
import il.co.tradesmanager.data.local.entity.ChecklistTemplateEntity
import il.co.tradesmanager.data.local.entity.ChecklistTemplateItemEntity
import il.co.tradesmanager.data.local.entity.ComplaintEntity
import il.co.tradesmanager.data.local.entity.DelayEventEntity
import il.co.tradesmanager.data.local.entity.DesignQueryEntity
import il.co.tradesmanager.data.local.entity.InspectionEntity
import il.co.tradesmanager.data.local.entity.InventoryItemEntity
import il.co.tradesmanager.data.local.entity.PpeIssueEntity
import il.co.tradesmanager.data.local.entity.ProjectEntity
import il.co.tradesmanager.data.local.entity.ProjectMaterialEntity
import il.co.tradesmanager.data.local.entity.ProjectTaskEntity
import il.co.tradesmanager.data.local.entity.RiskAssessmentEntity
import il.co.tradesmanager.data.local.entity.SiteVisitEntity
import il.co.tradesmanager.data.local.entity.SubmittalEntity
import il.co.tradesmanager.data.local.entity.SubstanceEntity
import il.co.tradesmanager.data.repository.SafetyRepository
import il.co.tradesmanager.ui.complaints.complaintSubjectLabel
import il.co.tradesmanager.ui.components.unitLabel
import il.co.tradesmanager.ui.delays.delayCauseLabel
import il.co.tradesmanager.ui.inspections.inspectionKindLabel
import il.co.tradesmanager.ui.inspections.inspectionResultLabel
import il.co.tradesmanager.ui.risks.bandLabel
import il.co.tradesmanager.ui.submittals.submittalDecisionLabel
import il.co.tradesmanager.ui.substances.hazardLabel
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
        /**
         * What was wrong, per check.
         *
         * The document this exists to produce is the one handed to a
         * regulator, and a column of the word FAIL with nothing beside it is
         * the version of it that helps nobody. Keyed by check, empty for the
         * ones that passed.
         */
        val notes: Map<String, String> = emptyMap(),
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
     * The protective equipment register: every issue, held or handed back.
     *
     * [now] is when it was exported, so the state column says what was
     * overdue on the day the printout was made rather than on the day it is
     * read.
     */
    data class PpeRegister(
        val issues: List<PpeIssueEntity>,
        val now: Long,
    ) : ExportDocument

    /**
     * One job's visitor log.
     *
     * Phone numbers are left out. The log is printed for an inspector who
     * wants to know who was on the site and when; a list of strangers'
     * numbers is not something to hand over because it happened to be kept.
     */
    data class VisitorLog(
        val jobName: String,
        val visits: List<SiteVisitEntity>,
    ) : ExportDocument

    /**
     * One job's inspection requests: what was asked to be seen, of whom, and
     * what was found. The printout a supervisor files, or a client asks for
     * before accepting a floor.
     */
    data class InspectionRegister(
        val jobName: String,
        val inspections: List<InspectionEntity>,
    ) : ExportDocument

    /** One job's material submittals, every revision, with what was said to each. */
    data class SubmittalRegister(
        val jobName: String,
        val submittals: List<SubmittalEntity>,
    ) : ExportDocument

    /**
     * One job's delay events, as a claim is prepared from them: the cause, the
     * days, the work held, and whether notice was given. Days still running
     * are counted to [today], the day the printout was made.
     */
    data class DelayRegister(
        val jobName: String,
        val events: List<DelayEventEntity>,
        val today: LocalDate,
    ) : ExportDocument

    /**
     * One job's complaints and what was done about each. The complainants'
     * contact details are left out: the register is printed for the
     * municipality or the client, not to hand strangers' numbers around.
     */
    data class ComplaintRegister(
        val jobName: String,
        val complaints: List<ComplaintEntity>,
    ) : ExportDocument

    /**
     * One job's hazardous substances: what each is, what its label warns of,
     * where the rest of it is kept, what to wear and what to do if somebody is
     * splashed, and the date of the data sheet on file. The sheet for the
     * store's door and for whoever arrives when something has gone wrong.
     */
    data class SubstanceRegister(
        val jobName: String,
        val substances: List<SubstanceEntity>,
        val today: LocalDate,
    ) : ExportDocument

    /** One job's questions to its designers, with the answers and when they came. */
    data class QueryRegister(
        val jobName: String,
        val queries: List<DesignQueryEntity>,
    ) : ExportDocument

    /**
     * One job's risk assessment: every hazard, its score before and after the
     * controls, the controls themselves, who owns them and when it is looked at
     * again. The document a safety officer is asked for first.
     */
    data class RiskRegister(
        val jobName: String,
        val risks: List<RiskAssessmentEntity>,
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

        is PpeRegister -> Table(
            title = context.getString(R.string.ppe_title),
            headers = listOf(
                context.getString(R.string.ppe_col_holder),
                context.getString(R.string.ppe_col_item),
                context.getString(R.string.ppe_quantity),
                context.getString(R.string.ppe_col_size),
                context.getString(R.string.ppe_col_issued),
                context.getString(R.string.ptw_issued_by),
                context.getString(R.string.ppe_replace_by),
                context.getString(R.string.ppe_col_state),
            ),
            rows = issues.sortedWith(compareBy({ it.holderName.lowercase() }, { it.issuedAt })).map { issue ->
                val state = Ppe.state(issue.replaceBy, issue.handedBackAt, now)
                listOf(
                    issue.holderName,
                    issue.itemName,
                    issue.quantity.toString(),
                    issue.size.orEmpty(),
                    day(issue.issuedAt, locale),
                    issue.issuedByName,
                    issue.replaceBy?.let { day(it, locale) }.orEmpty(),
                    when (state) {
                        Ppe.State.OVERDUE -> context.getString(R.string.ppe_state_overdue)
                        Ppe.State.DUE_SOON -> context.getString(R.string.ppe_state_due_soon)
                        Ppe.State.IN_USE -> context.getString(R.string.ppe_state_in_use)
                        Ppe.State.HANDED_BACK -> context.getString(R.string.ppe_history) +
                            issue.handedBackAt?.let { " " + day(it, locale) }.orEmpty()
                    },
                )
            },
        )

        is VisitorLog -> Table(
            title = context.getString(R.string.visit_title) + " — " + jobName,
            headers = listOf(
                context.getString(R.string.visit_name),
                context.getString(R.string.visit_organisation),
                context.getString(R.string.visit_host),
                context.getString(R.string.visit_col_arrived),
                context.getString(R.string.visit_col_left),
                context.getString(R.string.visit_col_briefed),
                context.getString(R.string.audit_signed),
            ),
            rows = visits.sortedBy { it.arrivedAt }.map { visit ->
                listOf(
                    visit.name,
                    visit.organisation.orEmpty(),
                    visit.hostName.orEmpty(),
                    moment(visit.arrivedAt, locale),
                    visit.leftAt?.let { moment(it, locale) } ?: context.getString(R.string.visit_col_still_here),
                    context.getString(if (visit.briefed) R.string.visit_col_yes else R.string.visit_col_no),
                    context.getString(if (!visit.signature.isNullOrBlank()) R.string.visit_col_yes else R.string.visit_col_no),
                )
            },
        )

        is InspectionRegister -> Table(
            title = context.getString(R.string.ir_title) + " — " + jobName,
            headers = listOf(
                context.getString(R.string.ex_col_number),
                context.getString(R.string.ir_kind),
                context.getString(R.string.ir_element),
                context.getString(R.string.ir_requested_of),
                context.getString(R.string.ex_col_asked),
                context.getString(R.string.ir_result),
                context.getString(R.string.ir_inspector),
                context.getString(R.string.ex_col_on),
                context.getString(R.string.ir_comments),
            ),
            rows = inspections.sortedBy { it.requestedAt }.map { row ->
                listOf(
                    row.reference,
                    context.getString(inspectionKindLabel(Inspections.kindOf(row.kind))),
                    row.element,
                    row.requestedOf,
                    day(row.requestedAt, locale),
                    Inspections.resultOf(row.result)?.let { context.getString(inspectionResultLabel(it)) }
                        ?: context.getString(R.string.ex_waiting),
                    row.inspectorName.orEmpty(),
                    row.decidedAt?.let { day(it, locale) }.orEmpty(),
                    row.comments.orEmpty(),
                )
            },
        )

        is SubmittalRegister -> Table(
            title = context.getString(R.string.ms_title) + " — " + jobName,
            headers = listOf(
                context.getString(R.string.ex_col_number),
                context.getString(R.string.ms_item),
                context.getString(R.string.ms_supplier),
                context.getString(R.string.ms_submitted_to),
                context.getString(R.string.ex_col_sent),
                context.getString(R.string.ms_decision),
                context.getString(R.string.ms_reviewer),
                context.getString(R.string.ex_col_on),
                context.getString(R.string.ms_notes),
            ),
            rows = submittals.sortedWith(compareBy({ it.reference }, { it.revision })).map { row ->
                listOf(
                    if (row.revision == 0) row.reference else context.getString(R.string.ms_revision_of, row.reference, row.revision),
                    row.item,
                    row.supplier.orEmpty(),
                    row.submittedTo,
                    day(row.submittedAt, locale),
                    Submittals.decisionOf(row.decision)?.let { context.getString(submittalDecisionLabel(it)) }
                        ?: context.getString(R.string.ex_waiting),
                    row.reviewerName.orEmpty(),
                    row.decidedAt?.let { day(it, locale) }.orEmpty(),
                    row.notes.orEmpty(),
                )
            },
        )

        is DelayRegister -> Table(
            title = context.getString(R.string.de_title) + " — " + jobName,
            headers = listOf(
                context.getString(R.string.ex_col_number),
                context.getString(R.string.de_cause),
                context.getString(R.string.de_description),
                context.getString(R.string.de_affected),
                context.getString(R.string.ex_col_from),
                context.getString(R.string.ex_col_to),
                context.getString(R.string.ex_col_days),
                context.getString(R.string.de_notice),
                context.getString(R.string.de_related),
            ),
            rows = events.sortedBy { it.startedOnDay }.map { row ->
                val started = LocalDate.ofEpochDay(row.startedOnDay)
                val ended = row.endedOnDay?.let(LocalDate::ofEpochDay)
                listOf(
                    row.reference,
                    context.getString(delayCauseLabel(Delays.causeOf(row.cause))),
                    row.description,
                    row.affectedWork.orEmpty(),
                    Formats.date(started, locale),
                    ended?.let { Formats.date(it, locale) } ?: context.getString(R.string.ex_still_going),
                    Delays.days(started, ended, today).toString(),
                    row.notifiedOnDay?.let { notified ->
                        row.notifiedTo.orEmpty() + " · " + Formats.date(LocalDate.ofEpochDay(notified), locale)
                    } ?: context.getString(R.string.de_no_notice),
                    row.relatedReference.orEmpty(),
                )
            },
        )

        is ComplaintRegister -> Table(
            title = context.getString(R.string.cp_title) + " — " + jobName,
            headers = listOf(
                context.getString(R.string.ex_col_number),
                context.getString(R.string.cp_subject),
                context.getString(R.string.cp_from),
                context.getString(R.string.cp_description),
                context.getString(R.string.ex_col_received),
                context.getString(R.string.cp_response),
                context.getString(R.string.ex_col_on),
            ),
            rows = complaints.sortedBy { it.receivedAt }.map { row ->
                listOf(
                    row.reference,
                    context.getString(complaintSubjectLabel(Complaints.subjectOf(row.subject))),
                    row.fromWhom,
                    row.description,
                    day(row.receivedAt, locale),
                    row.response ?: context.getString(R.string.ex_waiting),
                    row.answeredAt?.let { day(it, locale) }.orEmpty(),
                )
            },
        )

        is SubstanceRegister -> Table(
            title = context.getString(R.string.hs_title) + " — " + jobName,
            headers = listOf(
                context.getString(R.string.ex_col_number),
                context.getString(R.string.hs_name),
                context.getString(R.string.hs_hazards),
                context.getString(R.string.hs_kept_where),
                context.getString(R.string.hs_quantity),
                context.getString(R.string.hs_precautions),
                context.getString(R.string.hs_first_aid),
                context.getString(R.string.hs_sheet),
                context.getString(R.string.ex_col_off_site),
            ),
            rows = substances.sortedBy { it.addedAt }.map { row ->
                val sheetOn = row.sheetOnDay?.let(LocalDate::ofEpochDay)
                val state = Substances.state(sheetOn, row.removedAt != null, today)
                listOf(
                    row.reference,
                    row.name,
                    Substances.decode(row.hazards).sortedBy { it.ordinal }.joinToString(", ") { context.getString(hazardLabel(it)) },
                    row.keptWhere,
                    row.quantity.orEmpty(),
                    row.precautions.orEmpty(),
                    row.firstAid.orEmpty(),
                    when {
                        sheetOn == null -> context.getString(R.string.hs_no_sheet)
                        state == Substances.State.SHEET_OLD -> context.getString(R.string.hs_sheet_old, Formats.date(sheetOn, locale))
                        else -> Formats.date(sheetOn, locale)
                    },
                    row.removedAt?.let { day(it, locale) }.orEmpty(),
                )
            },
        )

        is QueryRegister -> Table(
            title = context.getString(R.string.qry_title) + " — " + jobName,
            headers = listOf(
                context.getString(R.string.ex_col_number),
                context.getString(R.string.qry_question),
                context.getString(R.string.qry_asked_of),
                context.getString(R.string.qry_drawing),
                context.getString(R.string.ex_col_asked),
                context.getString(R.string.qry_needed_by_label),
                context.getString(R.string.qry_answer),
                context.getString(R.string.ex_col_on),
            ),
            rows = queries.sortedBy { it.askedAt }.map { row ->
                listOf(
                    row.reference,
                    row.question,
                    row.askedOf,
                    row.drawingNumber.orEmpty(),
                    day(row.askedAt, locale),
                    row.neededBy?.let { day(it, locale) }.orEmpty(),
                    row.answer ?: context.getString(R.string.ex_waiting),
                    row.answeredAt?.let { day(it, locale) }.orEmpty(),
                )
            },
        )

        is RiskRegister -> Table(
            title = context.getString(R.string.ra_title) + " — " + jobName,
            headers = listOf(
                context.getString(R.string.ex_col_number),
                context.getString(R.string.ra_activity),
                context.getString(R.string.ra_hazard),
                context.getString(R.string.ra_who),
                context.getString(R.string.ra_before),
                context.getString(R.string.ra_controls),
                context.getString(R.string.ra_after),
                context.getString(R.string.ra_owner),
                context.getString(R.string.ex_col_review),
            ),
            rows = risks.sortedBy { it.reference }.map { row ->
                val before = Risks.score(row.likelihoodBefore, row.severityBefore)
                val after = Risks.score(row.likelihoodAfter, row.severityAfter)
                listOf(
                    row.reference,
                    row.activity,
                    row.hazard,
                    row.whoAtRisk.orEmpty(),
                    "$before " + context.getString(bandLabel(Risks.band(before))),
                    row.controls.orEmpty(),
                    "$after " + context.getString(bandLabel(Risks.band(after))),
                    row.ownerName.orEmpty(),
                    if (row.closed) {
                        context.getString(R.string.ra_closed)
                    } else {
                        row.reviewOnDay?.let { Formats.date(LocalDate.ofEpochDay(it), locale) }.orEmpty()
                    },
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
                    // The same word the screen shows, falling back to the
                    // stored value for an action this version has no sentence
                    // for. A document handed to a regulator should not be the
                    // one place the app still speaks in constants.
                    auditActionLabel(entry.action)
                        ?.let { context.getString(it) }
                        ?: entry.action,
                    summaryText(context, entry.summary),
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
                context.getString(R.string.saf_what_is_wrong),
            ),
            rows = checks.map { check ->
                listOf(
                    check.texts.resolve(languageTag),
                    if (check.critical) context.getString(R.string.saf_critical) else "",
                    answerLabel(context, answers[check.id]),
                    notes[check.id].orEmpty(),
                )
            } + listOf(
                listOf(
                    context.getString(R.string.saf_signed_by),
                    "",
                    run.signedByName.orEmpty(),
                    "",
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
            is PpeRegister -> "protective-equipment"
            is VisitorLog -> "visitors-" + jobName
            is InspectionRegister -> "inspections-" + jobName
            is SubmittalRegister -> "material-approvals-" + jobName
            is DelayRegister -> "delays-" + jobName
            is RiskRegister -> "risk-assessment-" + jobName
            is QueryRegister -> "questions-to-designers-" + jobName
            is ComplaintRegister -> "complaints-" + jobName
            is SubstanceRegister -> "substances-" + jobName
        },
    )

    private fun day(at: Long, locale: Locale): String =
        Formats.date(Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).toLocalDate(), locale)

    private fun moment(at: Long, locale: Locale): String =
        Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).let {
            Formats.dateTime(it.toLocalDate(), it.toLocalTime(), locale)
        }

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
    HandoverPack.Item.CUBES_FOR_ENGINEER -> R.string.hv_cubes_engineer
    HandoverPack.Item.POURS_WITHOUT_28_DAY_RESULT -> R.string.hv_cubes_missing
    HandoverPack.Item.UNSIGNED_DAILY_LOGS -> R.string.hv_daily_logs
    HandoverPack.Item.QUERIES_UNANSWERED -> R.string.hv_queries
    HandoverPack.Item.INSPECTIONS_OUTSTANDING -> R.string.hv_inspections
    HandoverPack.Item.POURS_WITHOUT_INSPECTION -> R.string.hv_pours_uninspected
    HandoverPack.Item.SUBMITTALS_OUTSTANDING -> R.string.hv_submittals
    HandoverPack.Item.WASTE_WITHOUT_TICKET -> R.string.hv_waste_unproven
}
