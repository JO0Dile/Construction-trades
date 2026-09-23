package il.co.tradesmanager.core.safety

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * How hot it is for the body, not for the thermometer.
 *
 * Heat is one of the things that most often hurts people on an Israeli site
 * in summer, and it does not look like a hazard: nobody falls, nothing breaks,
 * a man slows down and then stops making sense. What decides it is not the
 * temperature alone but the temperature and the humidity together, because
 * the body cools by sweating and sweat does not evaporate into wet air. A
 * humid 32 degrees on the coast is more dangerous than a dry 40 in the Arava,
 * and the thermometer on the site hut says the opposite.
 *
 * The number that joins the two is the heat index, computed here with the
 * formula the United States National Weather Service publishes -- the
 * Rothfusz regression, with its two published adjustments, and the simpler
 * Steadman estimate below the range where the regression holds. It is used
 * because it is public, checkable and widely relied on, not because any
 * Israeli regulation names it. The bands are the NWS's own, converted to
 * Celsius, and **none of this is offered as a statement of what Israeli law
 * requires**; see the disclaimer the screen carries.
 *
 * Two things the heat index is not:
 *
 * - **It is a shade figure.** In full sun the NWS says it can read up to
 *   8 degrees Celsius higher. A roofer is not in the shade, so a check can be
 *   marked as in the sun, and the band is then taken from the worst case
 *   rather than the shade reading. See [SUN_ALLOWANCE_C].
 * - **It knows nothing about the work.** Carrying block up a stair in 34
 *   degrees is not the same as checking drawings in it. The measures for each
 *   band are what a site does in that band, and the person recording the
 *   check says which they did.
 */
object Heat {

    /**
     * How much hotter full sun can make it feel, as the NWS states it: up to
     * fifteen degrees Fahrenheit, which is 8.3 Celsius.
     *
     * An upper bound, not a correction. It is applied whole because a record
     * of a heat check exists to protect the man on the roof, and the day the
     * bound is reached is the day the record is looked at.
     */
    const val SUN_ALLOWANCE_C: Double = 15.0 * 5.0 / 9.0

    /** Readings outside these are a typo, not weather. */
    const val COLDEST_PLAUSIBLE_C: Double = -30.0
    const val HOTTEST_PLAUSIBLE_C: Double = 60.0

    /**
     * The NWS heat index bands, least to most dangerous. The order is
     * load-bearing: [atLeast] compares against it.
     *
     * The lower edge of each, in Celsius, is the NWS's Fahrenheit threshold
     * converted exactly -- 80, 90, 103 and 125 degrees -- rather than rounded
     * to something that looks tidier and moves the line.
     */
    enum class Band(val fromCelsius: Double) {
        /** Below the first band. Nothing about the heat itself to act on. */
        NONE(Double.NEGATIVE_INFINITY),

        /** Fatigue is possible with long exposure and activity. */
        CAUTION((80.0 - 32.0) * 5.0 / 9.0),

        /** Heat cramps and heat exhaustion are possible. */
        EXTREME_CAUTION((90.0 - 32.0) * 5.0 / 9.0),

        /** Cramps and exhaustion likely, heat stroke possible. */
        DANGER((103.0 - 32.0) * 5.0 / 9.0),

        /** Heat stroke is highly likely. */
        EXTREME_DANGER((125.0 - 32.0) * 5.0 / 9.0),
        ;

        fun atLeast(other: Band): Boolean = ordinal >= other.ordinal
    }

    /**
     * What a site does about the heat. Recorded against a check as what was
     * actually done, not as what the band recommends.
     */
    enum class Measure {
        /** Cool drinking water within reach of the work, not in the hut. */
        WATER,

        /** Somewhere shaded to rest. */
        SHADE,

        /** Breaks taken on a schedule rather than when somebody feels ill. */
        SCHEDULED_BREAKS,

        /** Nobody working alone; each person has somebody watching them. */
        BUDDY_SYSTEM,

        /**
         * Somebody new to the heat, or back after a week away, is on lighter
         * work. Most heat illness happens in the first days on a hot site.
         */
        NEW_WORKERS_EASED_IN,

        /** Heavy work moved to the cooler hours of the day. */
        HEAVY_WORK_MOVED,

        /** Heavy work outdoors stopped. */
        HEAVY_WORK_STOPPED,
    }

    /**
     * The measures a band calls for, cumulatively: each band keeps the ones
     * below it and adds its own.
     *
     * Common practice for each NWS band, of the kind occupational safety
     * guidance publishes. A starting list for the person on site, not a rule
     * the app enforces -- see [record]'s refusal list, which does not include
     * "did not do enough".
     */
    fun recommended(band: Band): List<Measure> = when (band) {
        Band.NONE -> emptyList()
        Band.CAUTION -> listOf(Measure.WATER, Measure.SHADE)
        Band.EXTREME_CAUTION -> recommended(Band.CAUTION) +
            listOf(Measure.SCHEDULED_BREAKS, Measure.BUDDY_SYSTEM, Measure.NEW_WORKERS_EASED_IN)
        Band.DANGER -> recommended(Band.EXTREME_CAUTION) + Measure.HEAVY_WORK_MOVED
        Band.EXTREME_DANGER -> recommended(Band.DANGER) + Measure.HEAVY_WORK_STOPPED
    }

    fun celsiusToFahrenheit(celsius: Double): Double = celsius * 9.0 / 5.0 + 32.0

    fun fahrenheitToCelsius(fahrenheit: Double): Double = (fahrenheit - 32.0) * 5.0 / 9.0

    /**
     * The heat index, in Celsius, for a shade temperature and a relative
     * humidity.
     *
     * Worked in Fahrenheit because that is the unit the regression was fitted
     * in; its coefficients are not portable to Celsius. The simple estimate is
     * computed first, exactly as the NWS describes, and the full regression is
     * used only once that estimate reaches 80F -- below it, the regression is
     * outside the conditions it was fitted to and gives nonsense.
     */
    fun heatIndexCelsius(temperatureC: Double, humidityPercent: Double): Double {
        val t = celsiusToFahrenheit(temperatureC)
        val rh = humidityPercent
        val simple = 0.5 * (t + 61.0 + (t - 68.0) * 1.2 + rh * 0.094)
        if (simple < 80.0) return fahrenheitToCelsius(simple)

        var hi = -42.379 + 2.04901523 * t + 10.14333127 * rh -
            0.22475541 * t * rh - 0.00683783 * t * t - 0.05481717 * rh * rh +
            0.00122874 * t * t * rh + 0.00085282 * t * rh * rh -
            0.00000199 * t * t * rh * rh

        // The NWS's two published adjustments, for very dry heat and for
        // humid heat at the lower end of the range.
        if (rh < 13.0 && t in 80.0..112.0) {
            hi -= ((13.0 - rh) / 4.0) * sqrt((17.0 - abs(t - 95.0)) / 17.0)
        } else if (rh > 85.0 && t in 80.0..87.0) {
            hi += ((rh - 85.0) / 10.0) * ((87.0 - t) / 5.0)
        }
        return fahrenheitToCelsius(hi)
    }

    /**
     * Which band a heat index falls in.
     *
     * Never reached with a non-finite value: [record] refuses those first, and
     * a NaN arriving here would rather fail loudly than read as no hazard.
     */
    fun bandOf(heatIndexC: Double): Band =
        Band.entries.last { heatIndexC >= it.fromCelsius }

    /** Why a reading was not accepted. */
    enum class Refusal {
        /** Colder than [COLDEST_PLAUSIBLE_C] or hotter than [HOTTEST_PLAUSIBLE_C]. */
        IMPLAUSIBLE_TEMPERATURE,

        /** Below nought or above a hundred per cent. */
        IMPLAUSIBLE_HUMIDITY,
    }

    /** One check, as the rules see it. */
    data class Reading(
        val temperatureC: Double,
        val humidityPercent: Double,
        val inSun: Boolean,
        val heatIndexC: Double,
        /** The index the band was taken from: the shade figure, or the worst case in sun. */
        val judgedOnC: Double,
        val band: Band,
    ) {
        /** What this band calls for and the check did not record doing. */
        fun missing(taken: Collection<Measure>): List<Measure> =
            recommended(band).filterNot { it in taken }
    }

    sealed interface Outcome {
        data class Accepted(val reading: Reading) : Outcome
        data class Refused(val reason: Refusal) : Outcome
    }

    /**
     * Turn what somebody typed into a reading, or say why not.
     *
     * Refuses only on numbers that cannot be weather. A reading with nothing
     * done about it is **not** refused: "39 degrees, danger, nothing done" is
     * a true record, and a register that will not write it down is one that
     * hides the days that matter. The screen shows what is missing instead.
     */
    fun record(temperatureC: Double, humidityPercent: Double, inSun: Boolean): Outcome {
        if (!temperatureC.isFinite() ||
            temperatureC < COLDEST_PLAUSIBLE_C ||
            temperatureC > HOTTEST_PLAUSIBLE_C
        ) {
            return Outcome.Refused(Refusal.IMPLAUSIBLE_TEMPERATURE)
        }
        if (!humidityPercent.isFinite() || humidityPercent < 0.0 || humidityPercent > 100.0) {
            return Outcome.Refused(Refusal.IMPLAUSIBLE_HUMIDITY)
        }
        val index = heatIndexCelsius(temperatureC, humidityPercent)
        val judged = if (inSun) index + SUN_ALLOWANCE_C else index
        return Outcome.Accepted(
            Reading(
                temperatureC = temperatureC,
                humidityPercent = humidityPercent,
                inSun = inSun,
                heatIndexC = index,
                judgedOnC = judged,
                band = bandOf(judged),
            ),
        )
    }
}
