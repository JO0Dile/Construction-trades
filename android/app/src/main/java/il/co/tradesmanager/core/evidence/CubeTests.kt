package il.co.tradesmanager.core.evidence

/**
 * Concrete cube results, set against the strength the engineer specified.
 *
 * Cubes are cast from the trucks on the day of the pour and crushed by a lab
 * at seven days and at twenty-eight. The twenty-eight-day result is the one a
 * structure is judged on; the seven-day one is an early look, and a low seven
 * is a reason to watch the twenty-eight rather than a failure in itself.
 *
 * This does not decide whether a pour conforms. That is a statistical test in
 * the standard, applied by the engineer, and it can call for more cubes,
 * cores or a load test before anybody says a slab is good or bad. What it
 * does is refuse to let a low result sit unnoticed in a lab report in a
 * drawer: a twenty-eight-day mean under the specified strength, or any one
 * cube under it, is shown as needing the engineer.
 */
object CubeTests {

    /** The age the structure is judged at. */
    const val JUDGED_AT_DAYS = 28

    /** Past this a number is a typo, not a cube: the strongest site concrete is well under it. */
    const val MOST_MPA = 150.0

    /** How many cubes one result can carry. Three is normal; six is a doubled set. */
    const val MOST_CUBES = 6

    enum class Refusal { NO_CUBES, IMPLAUSIBLE_STRENGTH, BAD_AGE }

    enum class Verdict {
        /** Before twenty-eight days: an indication, not a judgement. */
        EARLY,

        /** The pour does not say what strength was specified, so there is nothing to compare with. */
        NO_SPECIFICATION,

        /** The mean of the set is under the specified strength. */
        MEAN_BELOW,

        /** The mean is there, and at least one cube is under it. */
        CUBE_BELOW,

        /** Mean and every cube at or above the specified strength. */
        MEETS,
    }

    data class Result(
        val meanMpa: Double,
        val lowestMpa: Double,
        val specifiedMpa: Double?,
        val verdict: Verdict,
    ) {
        /** Worth an engineer's attention. */
        val needsEngineer: Boolean get() = verdict == Verdict.MEAN_BELOW || verdict == Verdict.CUBE_BELOW
    }

    fun refusal(ageDays: Int, strengthsMpa: List<Double>): Refusal? = when {
        ageDays < 1 || ageDays > 365 -> Refusal.BAD_AGE
        strengthsMpa.isEmpty() -> Refusal.NO_CUBES
        strengthsMpa.size > MOST_CUBES -> Refusal.IMPLAUSIBLE_STRENGTH
        strengthsMpa.any { !it.isFinite() || it <= 0.0 || it > MOST_MPA } -> Refusal.IMPLAUSIBLE_STRENGTH
        else -> null
    }

    fun judge(ageDays: Int, strengthsMpa: List<Double>, specifiedMpa: Double?): Result {
        val mean = strengthsMpa.average()
        val lowest = strengthsMpa.min()
        val verdict = when {
            ageDays < JUDGED_AT_DAYS -> Verdict.EARLY
            specifiedMpa == null -> Verdict.NO_SPECIFICATION
            mean < specifiedMpa -> Verdict.MEAN_BELOW
            lowest < specifiedMpa -> Verdict.CUBE_BELOW
            else -> Verdict.MEETS
        }
        return Result(meanMpa = mean, lowestMpa = lowest, specifiedMpa = specifiedMpa, verdict = verdict)
    }

    /**
     * A stored set, as the rules see it: figures kept as text, judged against
     * the strength the pour's own mix asks for. Null when no figure reads as
     * a number, which a set recorded through the app never is.
     */
    fun judgeStored(ageDays: Int, strengths: List<String>, mixDesign: String?): Result? {
        val parsed = strengths.mapNotNull { it.toDoubleOrNull() }.filter { it.isFinite() }
        if (parsed.isEmpty()) return null
        return judge(ageDays, parsed, specifiedMpa(mixDesign))
    }

    /**
     * The cube strength a mix design asks for, in MPa, or null if it does not
     * say in a form this can read.
     *
     * "B-30", "B30", "ב-30" and "ב30" are the Israeli grade and mean 30 MPa
     * on a cube. "C30/37" is the European pair, cylinder then cube, and the
     * cube figure is what cubes are compared with. Anything else is left
     * alone rather than guessed at: a wrong specified strength is worse than
     * none, because it makes a bad result look good.
     */
    fun specifiedMpa(mixDesign: String?): Double? {
        val text = mixDesign?.trim().orEmpty()
        if (text.isEmpty()) return null
        EUROPEAN.find(text)?.let { return it.groupValues[2].toDouble() }
        ISRAELI.find(text)?.let { return it.groupValues[1].toDouble() }
        return null
    }

    private val EUROPEAN = Regex("""(?i)\bC\s*(\d{2,3})\s*/\s*(\d{2,3})\b""")

    // B or the Hebrew bet, an optional dash or space, then the number -- and
    // not in the middle of a longer word.
    private val ISRAELI = Regex("""(?:^|[^\p{L}])[Bbב]\s*[-‐-―־]?\s*(\d{2,3})\b""")
}
