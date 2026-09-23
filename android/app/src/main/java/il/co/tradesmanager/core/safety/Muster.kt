package il.co.tradesmanager.core.safety

/**
 * The roll call after the site is evacuated.
 *
 * When the alarm goes and everybody walks to the gate, one question has to be
 * answered before anybody is sent back in: is there still a man in the
 * building. On most sites that question is answered from a paper register kept
 * in the site hut -- which is inside the building that is on fire.
 *
 * The app already knows the answer. Every open check-in is a person who,
 * according to the phone in their pocket, has not left the site. Nothing used
 * that for anything until now.
 *
 * Three rules shape everything below, and all three exist because the
 * alternative gets somebody killed.
 *
 * **The list is taken once, at the start.** A roll call counts the people who
 * were on site when the alarm went, not the people who are on site now. If the
 * list kept refreshing, a man who walked out of the gate and ended his shift
 * during the evacuation would quietly disappear off it, and nobody would ever
 * know whether he was accounted for or simply gone from the query.
 *
 * **A name is never dropped for being doubtful.** Somebody who forgot to check
 * out two days ago is almost certainly not on site, and putting them on the
 * roll call is annoying. Leaving them off is worse: the one time the stale
 * check-in is real, the name is missing from the only list anybody is reading.
 * So a doubtful name goes on the list and is marked doubtful -- see
 * [Person.staleCheckIn] -- and a human decides.
 *
 * **Accounted for is not the same as seen.** Standing at the muster point in
 * front of you is [State.PRESENT]. Confirmed safe some other way -- rang them,
 * they left at four, the foreman saw them drive off -- is
 * [State.ACCOUNTED_ELSEWHERE], and it cannot be recorded without saying how.
 * Without that rule the fast way to finish a roll call is to tick everybody
 * elsewhere, and the roll call stops meaning anything at all.
 *
 * A roll call is **ended**, never closed, completed or passed. Ending one with
 * people still unaccounted for is allowed and always was -- at some point the
 * list has to be handed to the fire brigade -- but the record keeps their
 * names, and [Ended.unaccounted] is what gets read out.
 *
 * None of this is offered as a statement of what any regulation requires. It
 * is how a roll call is run on a site that runs one.
 */
object Muster {

    /**
     * Past this, a check-in that is still open says more about the phone than
     * about where the person is.
     *
     * Sixteen hours is longer than any single shift anybody works and shorter
     * than a night plus a morning, so a man who checked in at six yesterday
     * and never checked out is flagged by the time the next alarm goes. The
     * flag changes nothing about whether the name appears -- it appears either
     * way -- only how much the person reading the list should trust it.
     */
    const val STALE_AFTER_MS: Long = 16L * 60L * 60L * 1000L

    /**
     * After this long a roll call is taking too long, and the screen says so.
     *
     * A display hint and nothing more. It is not a deadline anybody is held to
     * and it is not drawn from any regulation; it exists so that a roll call
     * left half-finished on a phone in somebody's pocket looks wrong on the
     * screen instead of looking normal.
     */
    const val LONG_RUNNING_MINUTES: Long = 10L

    /** Why the site was cleared. */
    enum class Reason {
        /** A practice. Nobody is in danger and the record is training evidence. */
        DRILL,

        /** The real thing. */
        EMERGENCY,
        ;

        /** True when the record belongs with the incidents rather than the training. */
        val isReal: Boolean get() = this == EMERGENCY
    }

    /** Where a person stands in the count. */
    enum class State {
        /**
         * Nobody has said anything about this person yet. The default, and the
         * only state that means what it says without a human having acted.
         */
        UNACCOUNTED,

        /** At the muster point. Somebody is looking at them. */
        PRESENT,

        /**
         * Safe, but not here: rang them, they went home at four, seen driving
         * off. Requires an account of how they were reached -- see
         * [Refusal.NO_ACCOUNT_GIVEN].
         */
        ACCOUNTED_ELSEWHERE,
        ;

        /** True for everything except [UNACCOUNTED]. */
        val isAccountedFor: Boolean get() = this != UNACCOUNTED
    }

    /**
     * One name on the roll.
     *
     * @param personId the account that checked in, or null for somebody added
     *   by hand at the gate. Not unique on its own for that reason -- [id] is.
     * @param staleCheckIn their check-in had been open longer than
     *   [STALE_AFTER_MS] when the roll call started, so the name may be a
     *   ghost. Never a reason to leave them off.
     * @param addedDuringRollCall they were not in the check-in list at all --
     *   a visitor, a delivery driver, a subcontractor who never uses the app.
     * @param account how they were accounted for, when that was not by being
     *   looked at. Null while [state] is [State.UNACCOUNTED].
     */
    data class Person(
        val id: String,
        val personId: String?,
        val name: String,
        val state: State = State.UNACCOUNTED,
        val staleCheckIn: Boolean = false,
        val addedDuringRollCall: Boolean = false,
        val account: String? = null,
        val settledAt: Long? = null,
    )

    /**
     * A roll call, live or ended.
     *
     * @param startedAt when the alarm went, and the moment the list was taken.
     * @param endedAt null while it is running. Once set, nothing about the
     *   roll call changes again.
     */
    data class Roll(
        val startedAt: Long,
        val people: List<Person> = emptyList(),
        val endedAt: Long? = null,
    ) {
        /** Still running. */
        val isLive: Boolean get() = endedAt == null

        /** Nobody left to find. Does not mean the roll call has been ended. */
        val everyoneAccountedFor: Boolean get() = people.all { it.state.isAccountedFor }

        /** How many are still missing. The number the screen shows biggest. */
        val unaccountedCount: Int get() = people.count { !it.state.isAccountedFor }

        /** The names still missing, in reading order. */
        fun unaccounted(): List<Person> = people.filter { !it.state.isAccountedFor }.sortedBy { it.name }

        /**
         * The whole list in the order it should be read: missing first, then
         * everybody else, each group by name.
         *
         * Missing first because that is the part somebody is shouting across a
         * car park, and it must not be somewhere down a scrolled list.
         */
        fun ordered(): List<Person> =
            people.sortedWith(compareBy({ it.state.isAccountedFor }, { it.name }))

        /** Minutes since the alarm, or until it was ended. Never negative. */
        fun minutesRunning(now: Long): Long {
            val until = endedAt ?: now
            return ((until - startedAt).coerceAtLeast(0L)) / 60_000L
        }

        /** Running long enough that the screen should say so. */
        fun isLongRunning(now: Long): Boolean =
            isLive && minutesRunning(now) >= LONG_RUNNING_MINUTES
    }

    /** Why a change to a roll call was refused. */
    enum class Refusal {
        /** The roll call has been ended. A record does not get edited. */
        ALREADY_ENDED,

        /** No such person on this roll call. */
        NOT_ON_THE_LIST,

        /**
         * [State.ACCOUNTED_ELSEWHERE] with nothing said about how. Refused so
         * that the quick way through a roll call is not to tick everybody off
         * without checking.
         */
        NO_ACCOUNT_GIVEN,

        /** A person was added with no name, or a name of nothing but spaces. */
        BLANK_NAME,
    }

    /** The result of asking to change a roll call. */
    sealed interface Outcome {
        /** It went through. [roll] is the roll call afterwards. */
        data class Changed(val roll: Roll) : Outcome

        /** It did not. Nothing changed. */
        data class Refused(val reason: Refusal) : Outcome
    }

    /** An open check-in, as the roll call needs it. */
    data class OpenCheckIn(
        val personId: String?,
        val name: String,
        val checkInAt: Long,
        /** The job they clocked on to, when the entry carries one. */
        val projectId: String? = null,
    )

    /**
     * Which site is being evacuated, taken from the people on it.
     *
     * Not asked of whoever starts the roll call. The alarm is going and the
     * one thing that must not happen is a dropdown between the person and the
     * list of names, so the job is worked out instead of chosen: if every open
     * check-in names the same job, that is the site, and if they disagree --
     * or if any of them names no job at all -- the answer is null.
     *
     * Null is honest rather than a failure. A roll call belonging to no
     * recorded job is a real thing: a yard, a firm running one site that
     * nobody bothered to create, a check-in made before the app knew about
     * jobs. Guessing the most common job would put an evacuation on the wrong
     * site's record, and a record that is wrong is worse than one that is
     * blank.
     */
    fun siteOf(openCheckIns: List<OpenCheckIn>): String? {
        if (openCheckIns.isEmpty()) return null
        val first = openCheckIns.first().projectId ?: return null
        return first.takeIf { openCheckIns.all { checkIn -> checkIn.projectId == first } }
    }

    /**
     * Take the list.
     *
     * One row per person, oldest check-in first so that the man who has been on
     * site longest is not buried. Two open check-ins for the same account are
     * one person -- it happens when a phone records a check-in twice -- and the
     * earlier one wins, because it is the one that says how long they have been
     * in there.
     *
     * A check-in with no account behind it cannot be deduplicated and is kept
     * as its own row. Two rows for one man is a nuisance; one row for two men
     * is how somebody gets left inside.
     */
    fun start(openCheckIns: List<OpenCheckIn>, startedAt: Long, id: (Int) -> String): Roll {
        val seen = mutableSetOf<String>()
        val people = mutableListOf<Person>()
        openCheckIns.sortedBy { it.checkInAt }.forEach { checkIn ->
            if (checkIn.personId != null && !seen.add(checkIn.personId)) return@forEach
            people += Person(
                id = id(people.size),
                personId = checkIn.personId,
                name = checkIn.name.trim().ifBlank { checkIn.personId.orEmpty() },
                staleCheckIn = startedAt - checkIn.checkInAt > STALE_AFTER_MS,
            )
        }
        return Roll(startedAt = startedAt, people = people)
    }

    /**
     * Say where somebody is.
     *
     * Also the way back: settling to [State.UNACCOUNTED] clears the account and
     * puts them back on the missing list, because a row gets ticked by mistake
     * and there has to be an undo that is not "start the roll call again".
     */
    fun settle(
        roll: Roll,
        personRowId: String,
        to: State,
        account: String? = null,
        at: Long,
    ): Outcome {
        if (!roll.isLive) return Outcome.Refused(Refusal.ALREADY_ENDED)
        val index = roll.people.indexOfFirst { it.id == personRowId }
        if (index < 0) return Outcome.Refused(Refusal.NOT_ON_THE_LIST)
        val given = account?.trim().orEmpty()
        if (to == State.ACCOUNTED_ELSEWHERE && given.isEmpty()) {
            return Outcome.Refused(Refusal.NO_ACCOUNT_GIVEN)
        }
        val was = roll.people[index]
        val now = was.copy(
            state = to,
            account = if (to == State.UNACCOUNTED) null else given.ifEmpty { null },
            settledAt = if (to == State.UNACCOUNTED) null else at,
        )
        return Outcome.Changed(roll.copy(people = roll.people.toMutableList().also { it[index] = now }))
    }

    /**
     * Put somebody on the list who was never in the check-in data: a visitor, a
     * delivery driver, a subcontractor whose men do not use the app.
     *
     * They start [State.UNACCOUNTED] like everybody else. Adding a person to a
     * roll call is not a claim that they are safe, it is a claim that they were
     * on site.
     */
    fun add(roll: Roll, name: String, rowId: String): Outcome {
        if (!roll.isLive) return Outcome.Refused(Refusal.ALREADY_ENDED)
        val cleaned = name.trim()
        if (cleaned.isEmpty()) return Outcome.Refused(Refusal.BLANK_NAME)
        val person = Person(
            id = rowId,
            personId = null,
            name = cleaned,
            addedDuringRollCall = true,
        )
        return Outcome.Changed(roll.copy(people = roll.people + person))
    }

    /**
     * What ending a roll call produced.
     *
     * [unaccounted] is the part that matters and it is deliberately not a count:
     * a number is no use to somebody about to go back into a building, and the
     * names are what gets handed over.
     */
    data class Ended(val roll: Roll, val unaccounted: List<Person>) {
        val everybodyOut: Boolean get() = unaccounted.isEmpty()
    }

    /**
     * End it.
     *
     * Always allowed, including with people still missing, because the roll call
     * has to be endable at the moment the fire brigade takes over. What is not
     * allowed is ending it quietly: the names of anybody unaccounted for come
     * back out of this function and go into the record.
     *
     * Ending an already-ended roll call is refused rather than silently ignored
     * -- two end times on one record would make the duration a guess.
     */
    fun end(roll: Roll, at: Long): Outcome {
        if (!roll.isLive) return Outcome.Refused(Refusal.ALREADY_ENDED)
        return Outcome.Changed(roll.copy(endedAt = at.coerceAtLeast(roll.startedAt)))
    }

    /** The roll call as it reads once it is over. */
    fun ended(roll: Roll): Ended? =
        if (roll.isLive) null else Ended(roll = roll, unaccounted = roll.unaccounted())
}
