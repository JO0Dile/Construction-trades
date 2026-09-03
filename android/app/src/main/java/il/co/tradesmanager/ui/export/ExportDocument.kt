package il.co.tradesmanager.ui.export

import android.content.Context
import il.co.tradesmanager.R
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
        },
    )

    private fun answerLabel(context: Context, state: String?): String = when (state) {
        SafetyRepository.State.PASS -> context.getString(R.string.saf_pass)
        SafetyRepository.State.FAIL -> context.getString(R.string.saf_fail)
        SafetyRepository.State.NOT_APPLICABLE -> context.getString(R.string.saf_na)
        else -> "—"
    }

}
