package il.co.tradesmanager.ui.components

import il.co.tradesmanager.R

/**
 * Units come from catalogue data, so an unknown code degrades to "pcs" rather
 * than showing a raw enum name to a user. Shared by the list rows, the project
 * sheet and the exporter so a printed report cannot label a unit differently
 * from the screen it came from.
 */
fun unitLabel(unit: String): Int = when (unit.uppercase()) {
    "M" -> R.string.unit_m
    "M2" -> R.string.unit_m2
    "M3" -> R.string.unit_m3
    "KG" -> R.string.unit_kg
    "L" -> R.string.unit_l
    "ROLL" -> R.string.unit_roll
    "BAG" -> R.string.unit_bag
    "PAIR" -> R.string.unit_pair
    "BOX" -> R.string.unit_box
    else -> R.string.unit_pcs
}
