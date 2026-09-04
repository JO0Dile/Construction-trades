package il.co.tradesmanager.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Bathtub
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.Carpenter
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Construction
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Foundation
import androidx.compose.material.icons.filled.FormatPaint
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Handyman
import androidx.compose.material.icons.filled.Hardware
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LinearScale
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.Outlet
import androidx.compose.material.icons.filled.Plumbing
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SquareFoot
import androidx.compose.material.icons.filled.Stairs
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

/**
 * Category -> icon name, supplied from the shared catalogue at the top of the
 * app. Empty by default so a preview or a test can render without it.
 */
val LocalCategoryIcons: ProvidableCompositionLocal<Map<String, String>> =
    compositionLocalOf { emptyMap() }

@Composable
fun ProvideCategoryIcons(icons: Map<String, String>, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalCategoryIcons provides icons, content = content)
}

/**
 * The picture for a stock row.
 *
 * A photograph the user took of their own item always wins — it is their
 * actual stock, on their actual van, which is more use than any stock image.
 * Failing that, the item's category picks an icon, tinted by what kind of
 * thing it is, so a list is scannable at arm's length without reading a word.
 */
@Composable
fun ItemThumbnail(
    category: String,
    kind: String,
    photoUri: String? = null,
    size: Int = 44,
    modifier: Modifier = Modifier,
) {
    val tint = kindColour(kind)
    Box(
        modifier = modifier
            .size(size.dp)
            .background(tint.copy(alpha = 0.15f), RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (photoUri != null) {
            AsyncImage(
                model = photoUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size.dp).padding(0.dp),
            )
        } else {
            androidx.compose.material3.Icon(
                imageVector = categoryIcon(LocalCategoryIcons.current[category]),
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size((size * 0.55).dp),
            )
        }
    }
}

/**
 * Colour by what the thing *is*, not by trade: on a mixed list a tool, a
 * material and a piece of safety kit want telling apart at a glance, and a
 * plumber's list is all one trade anyway.
 */
fun kindColour(kind: String): Color = when (kind.uppercase()) {
    "TOOL" -> Color(0xFF2E86C1)
    "SAFETY" -> Color(0xFFC0392B)
    "CONSUMABLE" -> Color(0xFF7D3C98)
    "FITTING" -> Color(0xFF117864)
    else -> Color(0xFFB9770E)
}

/**
 * Icon name -> drawing. The names come from the shared catalogue; this
 * mapping is the one part that is properly platform code, because it names
 * Android's own asset set. An unknown name degrades to a generic box rather
 * than crashing, so a catalogue that adds a category before the apps do still
 * renders.
 */
fun categoryIcon(name: String?): ImageVector = when (name) {
    "cable", "wire" -> Icons.Filled.Cable
    "conduit" -> Icons.Filled.LinearScale
    "breaker" -> Icons.Filled.Power
    "board" -> Icons.Filled.Dashboard
    "outlet" -> Icons.Filled.Outlet
    "earth" -> Icons.Filled.Bolt

    "pipe" -> Icons.Filled.Plumbing
    "drain" -> Icons.Filled.WaterDrop
    "fitting" -> Icons.Filled.Build
    "valve" -> Icons.Filled.Tune
    "fixture" -> Icons.Filled.Bathtub
    "water-heater" -> Icons.Filled.LocalFireDepartment
    "meter" -> Icons.Filled.Speed

    "ac-unit", "refrigerant-pipe" -> Icons.Filled.AcUnit
    "insulation" -> Icons.Filled.Layers
    "gas-cylinder" -> Icons.Filled.LocalGasStation
    "thermostat" -> Icons.Filled.Thermostat
    "duct" -> Icons.Filled.Air
    "service-tool" -> Icons.Filled.Build

    "paint" -> Icons.Filled.FormatPaint
    "primer" -> Icons.Filled.Opacity
    "filler" -> Icons.Filled.Layers

    "panel" -> Icons.Filled.Layers
    "timber" -> Icons.Filled.Carpenter
    "edge-banding" -> Icons.Filled.Straighten
    "screw", "hinge" -> Icons.Filled.Hardware

    "cement", "aggregate" -> Icons.Filled.Grain
    "concrete" -> Icons.Filled.Foundation
    "block", "tile" -> Icons.Filled.GridOn
    "rebar" -> Icons.Filled.Straighten
    "waterproofing" -> Icons.Filled.Opacity
    "site-equipment" -> Icons.Filled.Construction
    "ladder" -> Icons.Filled.Stairs

    "hand-tool" -> Icons.Filled.Handyman
    "power-tool" -> Icons.Filled.Build
    "measure" -> Icons.Filled.SquareFoot
    "ppe" -> Icons.Filled.HealthAndSafety
    "consumable" -> Icons.Filled.Inventory2

    else -> Icons.Filled.Category
}
