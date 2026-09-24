package il.co.tradesmanager.core.people

import il.co.tradesmanager.core.find.Search

/**
 * The tickets somebody's trade usually needs that they do not hold in date.
 *
 * "Usually" is the catalogue's word, not the law's. Which tickets a job
 * actually calls for depends on the job -- an electrician rewiring a board and
 * one pulling cable in a trench are asked for different things -- so this is
 * the first question a safety officer asks, put where they can see it, not a
 * ruling. A trade the catalogue says nothing about produces no gaps rather
 * than invented ones.
 *
 * A ticket counts when its title is the kind's name in any of the app's
 * languages, the same rule the roll call uses to find first aiders: the
 * picker writes the name in whatever language the person adding it spoke. A
 * ticket typed by hand under other wording is not recognised, and shows as a
 * gap -- the safer way to be wrong, and one a person can put right by
 * adding it from the list.
 */
object TicketGaps {

    enum class State {
        /** Nothing on record under that kind. */
        MISSING,

        /** On record, and every one of them expired. */
        EXPIRED,
    }

    data class Gap(val kindId: String, val state: State)

    /** A ticket as it was written down. */
    data class Held(val title: String, val expiresOn: Long?)

    /**
     * @param usual the kinds the person's trade usually needs, by id
     * @param kindTitles every name each kind goes by, by id
     */
    fun gapsFor(
        usual: List<String>,
        kindTitles: Map<String, Collection<String>>,
        held: List<Held>,
        now: Long,
    ): List<Gap> = usual.distinct().mapNotNull { kind ->
        val titles = kindTitles[kind].orEmpty().map(Search::fold).filter { it.isNotEmpty() }.toSet()
        // A kind with no names cannot be matched by anything, and a gap
        // reported against it would be a gap nobody could close.
        if (titles.isEmpty()) return@mapNotNull null
        val matching = held.filter { Search.fold(it.title) in titles }
        when {
            matching.isEmpty() -> Gap(kind, State.MISSING)
            matching.all { Expiry.state(it.expiresOn, now) == Expiry.State.EXPIRED } -> Gap(kind, State.EXPIRED)
            else -> null
        }
    }
}
