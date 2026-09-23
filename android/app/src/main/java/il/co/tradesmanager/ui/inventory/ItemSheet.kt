package il.co.tradesmanager.ui.inventory

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import il.co.tradesmanager.R
import il.co.tradesmanager.core.i18n.Formats
import il.co.tradesmanager.core.i18n.resolve
import il.co.tradesmanager.data.local.entity.InventoryItemEntity
import il.co.tradesmanager.data.local.entity.StockMovementEntity
import il.co.tradesmanager.ui.audit.summaryText
import il.co.tradesmanager.ui.components.ItemThumbnail
import il.co.tradesmanager.ui.components.currentLanguageTag
import il.co.tradesmanager.ui.components.currentLocale
import il.co.tradesmanager.ui.components.unitLabel
import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs

/**
 * One item, opened from the stock list.
 *
 * The list row is a row: a thumbnail the size of a thumbnail, a name, and two
 * lines of spec cut off mid-sentence. That is right for scanning a van and
 * wrong for the other thing people do constantly, which is check they are
 * holding the right part before they fit it. This is that second view — the
 * picture at a size you can actually recognise a fitting from, the whole
 * spec, and the count with its plus and minus close enough together to use
 * one-handed.
 *
 * It also shows what has happened to the stock, which until now nothing did.
 * The movements have been recorded since the register was built — every
 * adjustment writes one, naming who and why — and no screen ever read them
 * back. A count nobody can question is a count nobody trusts: "it says four
 * and there are two" needs an answer, and the answer is in this list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemSheet(
    item: InventoryItemEntity,
    movements: List<StockMovementEntity>,
    photoUri: String?,
    onAdjust: (delta: Double, reason: String) -> Unit,
    onEdit: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val languageTag = currentLanguageTag()
    val locale = currentLocale()
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding(),
        ) {
            // The picture first and large. Most of this catalogue is fittings
            // and cable that read as the same grey object at 44dp, which is
            // the size the list can afford and this screen does not have to.
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                ItemThumbnail(
                    category = item.category,
                    kind = item.kind,
                    photoUri = photoUri,
                    catalogItemId = item.catalogItemId,
                    size = 148,
                )
            }

            Spacer(Modifier.height(16.dp))
            Text(item.names.resolve(languageTag), style = MaterialTheme.typography.headlineSmall)
            Text(
                text = stringResource(kindLabel(item.kind)) + SEPARATOR +
                    stringResource(if (item.isCustom) R.string.inv_custom else R.string.inv_from_catalog),
                style = MaterialTheme.typography.bodySmall,
                color = muted,
                modifier = Modifier.padding(top = 4.dp),
            )

            if (item.isLowStock) {
                Text(
                    text = stringResource(R.string.inv_low_stock),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            // The count, and the two buttons that change it. Large, centred
            // and far apart, because this is used standing up holding
            // something in the other hand.
            Spacer(Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilledTonalIconButton(
                    onClick = { onAdjust(-1.0, InventoryViewModel.USED_ON_SITE) },
                    // The repository clamps at zero, so pressing this on an
                    // empty shelf was already harmless — but silently
                    // harmless, which reads as broken. Now it is visibly off.
                    enabled = item.quantity > 0.0,
                ) {
                    Icon(
                        Icons.Filled.Remove,
                        contentDescription = stringResource(R.string.inv_stock_remove),
                    )
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 28.dp),
                ) {
                    Text(
                        text = Formats.quantity(item.quantity, locale),
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    Text(
                        text = stringResource(unitLabel(item.unit)),
                        style = MaterialTheme.typography.bodySmall,
                        color = muted,
                    )
                }
                FilledTonalIconButton(
                    onClick = { onAdjust(1.0, InventoryViewModel.RESTOCKED) },
                ) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = stringResource(R.string.inv_stock_add),
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            val spec = item.spec.resolve(languageTag)
            if (spec.isNotBlank()) {
                DetailLine(label = stringResource(R.string.inv_spec), value = spec)
            }
            if (item.minStock > 0.0) {
                DetailLine(
                    label = stringResource(R.string.inv_min_stock),
                    value = Formats.quantity(item.minStock, locale) + " " +
                        stringResource(unitLabel(item.unit)),
                )
            }
            item.barcode?.takeIf { it.isNotBlank() }?.let { barcode ->
                DetailLine(label = stringResource(R.string.inv_barcode), value = barcode)
            }
            if (item.tags.isNotEmpty()) {
                DetailLine(
                    label = stringResource(R.string.inv_tags),
                    value = item.tags.joinToString(SEPARATOR),
                )
            }

            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))

            Text(
                text = stringResource(R.string.inv_history),
                style = MaterialTheme.typography.labelLarge,
                color = muted,
            )
            Spacer(Modifier.height(4.dp))
            if (movements.isEmpty()) {
                Text(
                    text = stringResource(R.string.inv_no_movements),
                    style = MaterialTheme.typography.bodySmall,
                    color = muted,
                )
            } else {
                movements.take(MOVEMENTS_SHOWN).forEach { movement ->
                    Movement(movement = movement)
                }
            }

            Spacer(Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.action_close))
                }
                Spacer(Modifier.width(8.dp))
                FilledTonalButton(onClick = onEdit) {
                    Text(stringResource(R.string.inv_edit_item))
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

/** A labelled line. Label above in the muted style the sheets already use. */
@Composable
private fun DetailLine(label: String, value: String) {
    Column(modifier = Modifier.padding(bottom = 12.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * One stock movement: what changed, when, why and by whom.
 *
 * The sign is written out rather than left to the number formatter. In Hebrew
 * and Arabic a minus in front of a figure can end up on the far side of it,
 * and "1 -" read quickly is a one, not a minus one, on a line whose entire
 * job is to say which way the stock went.
 */
@Composable
private fun Movement(movement: StockMovementEntity) {
    val locale = currentLocale()
    val sign = if (movement.delta > 0) "+" else "-"
    val day = Formats.date(
        Instant.ofEpochMilli(movement.occurredAt).atZone(ZoneId.systemDefault()).toLocalDate(),
        locale,
    )
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = sign + Formats.quantity(abs(movement.delta), locale),
            style = MaterialTheme.typography.titleMedium,
            color = if (movement.delta > 0) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
        Column(modifier = Modifier.padding(start = 12.dp)) {
            Text(text = summaryText(movement.reason), style = MaterialTheme.typography.bodyMedium)
            Text(
                text = day + SEPARATOR + movement.actorName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun kindLabel(kind: String): Int = when (kind.uppercase()) {
    "TOOL" -> R.string.kind_tool
    "MATERIAL" -> R.string.kind_material
    "SAFETY" -> R.string.kind_safety
    "FITTING" -> R.string.kind_fitting
    "CONSUMABLE" -> R.string.kind_consumable
    else -> R.string.kind_other
}

/**
 * Between two things on one line.
 *
 * A middle dot rather than a comma or a slash: it carries no direction, so it
 * sits correctly between Hebrew or Arabic words without the reordering a
 * bracket or a slash brings with it.
 */
private const val SEPARATOR = " · "

/** Enough to answer "where did it go", short enough to stay a sheet. */
private const val MOVEMENTS_SHOWN = 8
