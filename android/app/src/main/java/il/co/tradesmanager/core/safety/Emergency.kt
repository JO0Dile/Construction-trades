package il.co.tradesmanager.core.safety

/**
 * The numbers somebody rings when the roll call comes up short.
 *
 * Israel's national emergency numbers, which are public and fixed. Offered on
 * the roll call screen as a tap that opens the dialer with the number already
 * in it -- the person still presses call themselves, so the app needs no
 * permission to place calls and cannot place one by mistake from a pocket.
 *
 * Kept to the three a construction site needs in an evacuation. The electric
 * company's 103 and Home Front Command's 104 are real numbers too, and not the
 * ones somebody standing at a muster point with a man missing should have to
 * choose between.
 */
object Emergency {

    /** Magen David Adom: ambulance. */
    const val AMBULANCE = "101"

    /** Fire and rescue services, including collapse and entrapment. */
    const val FIRE_AND_RESCUE = "102"

    /** Police. */
    const val POLICE = "100"

    /** In the order they are offered: the one most often needed first. */
    val ORDER: List<String> = listOf(AMBULANCE, FIRE_AND_RESCUE, POLICE)
}
