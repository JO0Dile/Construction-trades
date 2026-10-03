package il.co.tradesmanager.core.safety

/**
 * What everybody on a job should be able to read off the wall of the site
 * office when something has gone wrong: which hospital, how to get there,
 * where to gather, who knows first aid, who to ring on site, and where the
 * electricity, the water and the gas are turned off.
 *
 * One sheet per job, kept up to date rather than added to: an old hospital
 * or a first aider who left in March is worse than none, because somebody
 * will believe it. The national numbers come from [Emergency] and are not
 * typed in.
 *
 * Four things are asked for before the sheet reads as complete -- the
 * hospital, the assembly point, the first aiders and a number on site --
 * because those are what the first minutes need. The rest is there when it
 * is known.
 */
object EmergencySheet {

    enum class Essential { HOSPITAL, ASSEMBLY_POINT, FIRST_AIDERS, SITE_CONTACT }

    enum class Refusal { NOT_A_PHONE_NUMBER }

    /**
     * What a person types. Blank is "not known yet", never an error: the
     * sheet is worth printing with the assembly point on it even before
     * anybody has looked up the hospital.
     */
    data class Sheet(
        val hospitalName: String = "",
        val hospitalAddress: String = "",
        val hospitalPhone: String = "",
        val assemblyPoint: String = "",
        val firstAiders: String = "",
        val siteContactName: String = "",
        val siteContactPhone: String = "",
        val electricityShutOff: String = "",
        val waterShutOff: String = "",
        val gasShutOff: String = "",
        val notes: String = "",
    )

    /** The essentials still blank, in the order they are shown. */
    fun missing(sheet: Sheet?): List<Essential> {
        if (sheet == null) return Essential.entries.toList()
        return buildList {
            if (sheet.hospitalName.isBlank()) add(Essential.HOSPITAL)
            if (sheet.assemblyPoint.isBlank()) add(Essential.ASSEMBLY_POINT)
            if (sheet.firstAiders.isBlank()) add(Essential.FIRST_AIDERS)
            if (sheet.siteContactPhone.isBlank()) add(Essential.SITE_CONTACT)
        }
    }

    /** A phone field holds a number somebody can dial, or nothing. */
    fun refusal(sheet: Sheet): Refusal? =
        if (listOf(sheet.hospitalPhone, sheet.siteContactPhone).any { it.isNotBlank() && !isDialable(it) }) {
            Refusal.NOT_A_PHONE_NUMBER
        } else {
            null
        }

    /** Digits, with the spaces, dashes, brackets and leading plus people write them with; at least three digits. */
    fun isDialable(text: String): Boolean {
        val trimmed = text.trim()
        if (!trimmed.all { it.isDigit() || it in " -()+" }) return false
        if (trimmed.drop(1).contains('+')) return false
        return trimmed.count { it.isDigit() } >= MIN_DIGITS
    }

    /** What goes after "tel:": the digits, and the plus if it led. */
    fun dialString(text: String): String {
        val trimmed = text.trim()
        val digits = trimmed.filter { it.isDigit() }
        return if (trimmed.startsWith("+")) "+$digits" else digits
    }

    /** 100, 101 and 102 are three digits; nothing shorter is a number anybody rings. */
    private const val MIN_DIGITS = 3
}
