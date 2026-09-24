package il.co.tradesmanager.core.safety

import il.co.tradesmanager.core.security.Signature
import java.time.Instant
import java.time.ZoneId

/**
 * Protective equipment handed to a worker, and signed for.
 *
 * The record a firm is asked for after an accident is not "we had helmets in
 * the container". It is that this man was given this helmet on this day and
 * put his name to it. So the signature is the one thing an issue cannot go
 * without: an issue nobody signed for is somebody's say-so, and that is
 * exactly what the register exists to replace.
 *
 * What is refused is only what makes the record useless — no person, no item,
 * no signature, a count that is not a count. A stock shelf that says nought
 * does not stop a helmet going on a head: the helmet in the man's hand is the
 * fact, and a stock figure that disagrees with it is the thing that is wrong.
 */
object Ppe {

    /** The most of one thing handed to one person at once. Past it, it is a typo. */
    const val MOST_AT_ONCE = 50

    /** How long before a replace-by date the row starts saying so. */
    const val DUE_SOON_DAYS = 30

    private const val DAY_MS = 24L * 60 * 60 * 1000

    enum class Refusal {
        NO_WORKER,
        NO_ITEM,
        BAD_QUANTITY,
        NOT_SIGNED,

        /** A replace-by date on or before the day it was handed over. */
        REPLACE_BY_TOO_EARLY,
    }

    /**
     * Where one issued thing stands.
     *
     * [HANDED_BACK] covers every way it stopped being theirs — returned when
     * they left, swapped for a new one, taken off them because it was damaged.
     * The register only needs to know that it is no longer what they are
     * wearing.
     */
    enum class State { OVERDUE, DUE_SOON, IN_USE, HANDED_BACK }

    /**
     * Replacement intervals a person picks from, in months.
     *
     * They are there so nobody has to count forward on their fingers, not as
     * advice: how long a harness or a helmet lasts is the manufacturer's to
     * say, and it is printed on the thing. The app has no opinion on it.
     */
    val REPLACE_AFTER_MONTHS: List<Int> = listOf(6, 12, 24, 36, 60)

    fun refusal(
        workerName: String,
        itemName: String,
        quantity: Int,
        signature: String?,
        issuedAt: Long,
        replaceBy: Long?,
    ): Refusal? = when {
        workerName.isBlank() -> Refusal.NO_WORKER
        itemName.isBlank() -> Refusal.NO_ITEM
        quantity < 1 || quantity > MOST_AT_ONCE -> Refusal.BAD_QUANTITY
        !Signature.isSigned(signature) -> Refusal.NOT_SIGNED
        replaceBy != null && replaceBy <= issuedAt -> Refusal.REPLACE_BY_TOO_EARLY
        else -> null
    }

    fun state(replaceBy: Long?, handedBackAt: Long?, now: Long): State = when {
        handedBackAt != null -> State.HANDED_BACK
        replaceBy == null -> State.IN_USE
        replaceBy <= now -> State.OVERDUE
        replaceBy - now <= DUE_SOON_DAYS * DAY_MS -> State.DUE_SOON
        else -> State.IN_USE
    }

    /**
     * How far short the stock count was, or null if it covered the issue.
     *
     * A shortfall is reported, not refused. It means somebody took stock off
     * the shelf without recording it, and the person issuing is the one who
     * has just found that out.
     */
    fun shortfall(inStock: Double, quantity: Int): Double? =
        (quantity - inStock).takeIf { it > 0.0 }

    /** One issued thing, as the rules need to see it. */
    data class Issue(
        val id: String,
        /** Who it went to. An account id, or the typed name for somebody with none. */
        val holderKey: String,
        val holderName: String,
        val itemName: String,
        val quantity: Int,
        val issuedAt: Long,
        val replaceBy: Long?,
        val handedBackAt: Long?,
    )

    /** One person and what they are holding, the worst of it first. */
    data class Holder(
        val key: String,
        val name: String,
        val items: List<Pair<Issue, State>>,
    ) {
        val overdue: Int get() = items.count { it.second == State.OVERDUE }
        val dueSoon: Int get() = items.count { it.second == State.DUE_SOON }
    }

    /**
     * Everybody holding something, with what they hold.
     *
     * Only what is still in their hands: a register of everything anybody was
     * ever given answers the wrong question on a site, which is "what is this
     * man wearing and is it still good". Handed-back rows stay in the history.
     *
     * People with something overdue come first, then due soon, then by name,
     * because the first screenful is what gets read.
     */
    fun holders(issues: List<Issue>, now: Long): List<Holder> = issues
        .map { it to state(it.replaceBy, it.handedBackAt, now) }
        .filter { it.second != State.HANDED_BACK }
        .groupBy { it.first.holderKey }
        .map { (key, rows) ->
            Holder(
                key = key,
                // The name as it was written most recently: a person whose name
                // was corrected shows under the corrected one.
                name = rows.maxBy { it.first.issuedAt }.first.holderName,
                items = rows.sortedWith(
                    compareBy<Pair<Issue, State>> { it.second.ordinal }
                        .thenByDescending { it.first.issuedAt },
                ),
            )
        }
        .sortedWith(
            compareByDescending<Holder> { it.overdue }
                .thenByDescending { it.dueSoon }
                .thenBy { it.name.lowercase() },
        )

    /** The key a person without an account is grouped under. */
    fun typedHolderKey(name: String): String = "name:" + name.trim().lowercase()

    /**
     * A replace-by date some months after the issue, on the same day of the
     * month where there is one: a helmet handed out on the 31st of August is
     * due on the 28th of February, not in March.
     */
    fun monthsAfter(issuedAt: Long, months: Int, zone: ZoneId): Long =
        Instant.ofEpochMilli(issuedAt).atZone(zone).plusMonths(months.toLong())
            .toInstant().toEpochMilli()
}
