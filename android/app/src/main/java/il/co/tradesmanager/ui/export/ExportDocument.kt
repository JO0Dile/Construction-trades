package il.co.tradesmanager.ui.export

import android.content.Context
import il.co.tradesmanager.R
import il.co.tradesmanager.core.evidence.HandoverPack
import il.co.tradesmanager.core.i18n.Formats
import il.co.tradesmanager.core.i18n.resolve
import il.co.tradesmanager.data.local.entity.ChecklistRunEntity
import il.co.tradesmanager.data.local.entity.ChecklistTemplateEntity
import il.co.tradesmanager.data.local.entity.ChecklistTemplateItemEntity
import il.co.tradesmanager.data.local.entity.InventoryItemEntity
import il.co.tradesmanager.data.local.entity.ProjectEntity
import il.co.tradesmanager.data.local.entity.ProjectMaterialEntity
import il.co.tradesmanager.data.local.entity.ProjectTaskEntity
import il.co.tradesmanager.data.repository.SafetyRepository
import il.co.tradesmanager.ui.components.unitLabel
import java.time.LocalDate
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

    data class ProjectSheet(
        val project: ProjectEntity,
        val tasks: List<ProjectTaskEntity>,
        val materials: List<ProjectMaterialEntity>,
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

    data class Table(val title: String, val headers: List<String>, val rows: List<List<String>>)

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
            ),
            rows = tasks.map { task ->
                listOf(
                    context.getString(R.string.proj_tasks),
                    task.title,
                    context.getString(if (task.isDone) R.string.saf_pass else R.string.saf_fail),
                    "",
                )
            } + materials.map { material ->
                listOf(
                    context.getString(R.string.proj_materials),
                    material.label,
                    Formats.quantity(material.requiredQuantity, locale),
                    context.getString(unitLabel(material.unit)),
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
        },
    )

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
