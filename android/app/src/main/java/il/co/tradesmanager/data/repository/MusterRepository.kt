package il.co.tradesmanager.data.repository

import il.co.tradesmanager.core.access.Lens
import il.co.tradesmanager.core.access.Role
import il.co.tradesmanager.core.audit.Summaries
import il.co.tradesmanager.core.audit.Summary
import il.co.tradesmanager.core.safety.Muster
import il.co.tradesmanager.data.local.dao.MusterDao
import il.co.tradesmanager.data.local.dao.ScheduleDao
import il.co.tradesmanager.data.local.entity.MusterEntity
import il.co.tradesmanager.data.local.entity.MusterPersonEntity
import java.util.UUID
import kotlinx.coroutines.flow.Flow

/**
 * The roll call, from the check-in data to the record.
 *
 * Every rule in `core.safety.Muster` is applied here as well as on the screen,
 * the same way the violation register does it. A roll call is run by somebody
 * holding a phone in a car park with an alarm going: whatever the screen is
 * doing at that moment, an account cannot be marked safe elsewhere without a
 * reason and an ended roll call cannot be edited.
 *
 * The one thing this adds on top of the rules is the record. Ending a roll
 * call writes an audit row naming anybody unaccounted for, and that row cannot
 * be removed, because the register that could be tidied up afterwards is the
 * one nobody should believe.
 */
class MusterRepository(
    private val dao: MusterDao,
    private val schedule: ScheduleDao,
    private val audit: AuditTrail,
) {

    /** Why a write was refused. */
    enum class Refusal {
        /** Not a supervisor or safety officer. */
        NOT_ALLOWED,

        /** One is already running. Two at once would split the list in half. */
        ALREADY_RUNNING,

        /** No such roll call, or no such name on it. */
        UNKNOWN,

        /** It has ended. A record does not get edited. */
        ALREADY_ENDED,

        /** Marked safe elsewhere with nothing said about how. */
        NO_ACCOUNT_GIVEN,

        /** A name of nothing but spaces. */
        BLANK_NAME,
    }

    class Refused(val refusal: Refusal) : Exception(refusal.name)

    /** The roll call that is running, if one is. Null nearly always. */
    fun observeLive(): Flow<MusterEntity?> = dao.observeLive()

    fun observe(id: String): Flow<MusterEntity?> = dao.observe(id)

    fun observePeople(musterId: String): Flow<List<MusterPersonEntity>> = dao.observePeople(musterId)

    /** Everybody on one roll call, read once, for handing the list over. */
    suspend fun peopleOf(musterId: String): List<MusterPersonEntity> = dao.people(musterId)

    /** One firm's past roll calls. Null is the one-man band, not everybody. */
    fun observeForCompany(companyId: String?): Flow<List<MusterEntity>> =
        dao.observeForCompany(companyId)

    /** How many are checked in right now, for the card that offers to start one. */
    fun observeOnSiteCount(): Flow<Int> = schedule.observeOnSiteCount()

    /**
     * Take the list and start counting.
     *
     * Starts with nobody accounted for, including when the list is empty: a
     * roll call with no names is still a roll call, and somebody at the gate
     * adds the visitors by hand.
     */
    suspend fun start(
        role: Role,
        companyId: String?,
        reason: Muster.Reason,
        note: String?,
        byAccountId: String,
        byName: String,
    ): Result<MusterEntity> {
        if (!mayRun(role)) return Result.failure(Refused(Refusal.NOT_ALLOWED))
        if (dao.liveNow() != null) return Result.failure(Refused(Refusal.ALREADY_RUNNING))
        val startedAt = System.currentTimeMillis()
        val open = schedule.openCheckIns().map {
            Muster.OpenCheckIn(
                personId = it.workerId,
                name = it.workerName,
                checkInAt = it.checkInAt,
                projectId = it.projectId,
            )
        }
        val roll = Muster.start(
            openCheckIns = open,
            startedAt = startedAt,
            id = { UUID.randomUUID().toString() },
        )
        val muster = MusterEntity(
            id = UUID.randomUUID().toString(),
            // Taken from the people on site, not asked for. See Muster.siteOf.
            projectId = Muster.siteOf(open),
            companyId = companyId,
            startedAt = startedAt,
            reason = reason.name,
            startedByAccountId = byAccountId,
            startedByName = byName,
            note = note?.trim()?.ifBlank { null },
        )
        dao.upsert(muster)
        dao.upsertPeople(roll.people.map { it.toEntity(muster.id) })
        audit.record(
            entityType = ENTITY,
            entityId = muster.id,
            action = AuditTrail.Action.CREATE,
            actorName = byName,
            summary = Summary.of(
                Summaries.MUSTER_STARTED,
                Summary.nest(reasonKey(reason)),
                roll.people.size.toString(),
            ),
        )
        return Result.success(muster)
    }

    /**
     * Say where somebody is, or take it back.
     *
     * Not audited. A roll call is minutes long and a row gets ticked, unticked
     * and ticked again while somebody walks down a line of men; an audit entry
     * per tap would bury the two rows that matter -- it started, it ended --
     * under forty that do not. What each person's state ended up as is on the
     * roll call itself, which is never deleted.
     */
    suspend fun settle(
        personRowId: String,
        to: Muster.State,
        account: String? = null,
    ): Result<MusterPersonEntity> {
        val person = dao.person(personRowId) ?: return Result.failure(Refused(Refusal.UNKNOWN))
        val muster = dao.muster(person.musterId) ?: return Result.failure(Refused(Refusal.UNKNOWN))
        val roll = rollOf(muster, dao.people(muster.id))
        return when (val outcome = Muster.settle(roll, personRowId, to, account, System.currentTimeMillis())) {
            is Muster.Outcome.Refused -> Result.failure(Refused(outcome.reason.asRefusal()))
            is Muster.Outcome.Changed -> {
                val updated = outcome.roll.people.first { it.id == personRowId }.toEntity(muster.id)
                dao.upsertPerson(updated)
                Result.success(updated)
            }
        }
    }

    /**
     * Put somebody on the list who never checked in.
     *
     * Audited, unlike settling a row: a name that was not in the check-in data
     * is the roll call disagreeing with what the app thought it knew, and that
     * is worth a line in the register.
     */
    suspend fun add(musterId: String, name: String, actorName: String): Result<MusterPersonEntity> {
        val muster = dao.muster(musterId) ?: return Result.failure(Refused(Refusal.UNKNOWN))
        val roll = rollOf(muster, dao.people(musterId))
        val rowId = UUID.randomUUID().toString()
        return when (val outcome = Muster.add(roll, name, rowId)) {
            is Muster.Outcome.Refused -> Result.failure(Refused(outcome.reason.asRefusal()))
            is Muster.Outcome.Changed -> {
                val added = outcome.roll.people.first { it.id == rowId }.toEntity(musterId)
                dao.upsertPerson(added)
                audit.record(
                    entityType = ENTITY,
                    entityId = musterId,
                    action = AuditTrail.Action.UPDATE,
                    actorName = actorName,
                    summary = Summary.of(Summaries.MUSTER_PERSON_ADDED, added.name),
                )
                Result.success(added)
            }
        }
    }

    /**
     * End it, and write down who was not found.
     *
     * The names go in the audit summary rather than only the count. A number
     * in a register is something somebody argues with a year later; the names
     * are what the record is for.
     */
    suspend fun end(musterId: String, actorName: String): Result<Muster.Ended> {
        val muster = dao.muster(musterId) ?: return Result.failure(Refused(Refusal.UNKNOWN))
        val roll = rollOf(muster, dao.people(musterId))
        return when (val outcome = Muster.end(roll, System.currentTimeMillis())) {
            is Muster.Outcome.Refused -> Result.failure(Refused(outcome.reason.asRefusal()))
            is Muster.Outcome.Changed -> {
                val ended = Muster.ended(outcome.roll)
                    ?: return Result.failure(Refused(Refusal.ALREADY_ENDED))
                dao.upsert(
                    muster.copy(
                        endedAt = outcome.roll.endedAt,
                        unaccountedAtEnd = ended.unaccounted.size,
                    ),
                )
                audit.record(
                    entityType = ENTITY,
                    entityId = musterId,
                    action = AuditTrail.Action.SIGN_OFF,
                    actorName = actorName,
                    summary = if (ended.everybodyOut) {
                        Summary.of(Summaries.MUSTER_ENDED_ALL_OUT)
                    } else {
                        Summary.of(
                            Summaries.MUSTER_ENDED_MISSING,
                            ended.unaccounted.size.toString(),
                            ended.unaccounted.joinToString(NAMES) { it.name },
                        )
                    },
                )
                Result.success(ended)
            }
        }
    }

    /**
     * Rebuild the rules' view of a roll call from the rows.
     *
     * Public because the screen needs the same view: the order names are read
     * in, the count of who is missing and how long it has been running are all
     * rules, and a screen recomputing them beside the repository is a screen
     * that will disagree with the record.
     *
     * The tables hold strings because that is what a database holds; a state
     * nobody recognises reads back as [Muster.State.UNACCOUNTED], which is the
     * direction that keeps a name on the missing list rather than quietly off
     * it.
     */
    fun rollOf(muster: MusterEntity, people: List<MusterPersonEntity>): Muster.Roll =
        Muster.Roll(
            startedAt = muster.startedAt,
            endedAt = muster.endedAt,
            people = people.map { row ->
                Muster.Person(
                    id = row.id,
                    personId = row.personId,
                    name = row.name,
                    state = state(row.state),
                    staleCheckIn = row.staleCheckIn,
                    addedDuringRollCall = row.addedDuringRollCall,
                    account = row.account,
                    settledAt = row.settledAt,
                )
            },
        )

    private fun state(stored: String): Muster.State =
        runCatching { Muster.State.valueOf(stored) }.getOrDefault(Muster.State.UNACCOUNTED)

    private fun Muster.Person.toEntity(musterId: String) = MusterPersonEntity(
        id = id,
        musterId = musterId,
        personId = personId,
        name = name,
        state = state.name,
        staleCheckIn = staleCheckIn,
        addedDuringRollCall = addedDuringRollCall,
        account = account,
        settledAt = settledAt,
    )

    private fun Muster.Refusal.asRefusal(): Refusal = when (this) {
        Muster.Refusal.ALREADY_ENDED -> Refusal.ALREADY_ENDED
        Muster.Refusal.NOT_ON_THE_LIST -> Refusal.UNKNOWN
        Muster.Refusal.NO_ACCOUNT_GIVEN -> Refusal.NO_ACCOUNT_GIVEN
        Muster.Refusal.BLANK_NAME -> Refusal.BLANK_NAME
    }

    /**
     * Who may start one.
     *
     * The same write permission as the rest of the safety register, which
     * means an owner, a manager, a safety officer or a worker -- anybody
     * actually on site. Deliberately not narrowed to a supervisor: the one
     * scenario this feature exists for is the one where the supervisor is the
     * person still inside, and an app that refuses the man holding the phone
     * is worse than no app.
     *
     * The two it excludes are the office roles, finance and HR, who write
     * nothing in the safety register and are not standing at the gate.
     */
    private fun mayRun(role: Role): Boolean = role.canWrite(Lens.EVIDENCE)

    companion object {
        /** Translated at display time; see core.audit.Summary. */
        fun reasonKey(reason: Muster.Reason): String = when (reason) {
            Muster.Reason.DRILL -> "muster_reason_drill"
            Muster.Reason.EMERGENCY -> "muster_reason_emergency"
        }

        private const val ENTITY = "muster"
        private const val NAMES = ", "
    }
}
