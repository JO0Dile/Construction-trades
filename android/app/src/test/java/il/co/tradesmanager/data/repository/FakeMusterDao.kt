package il.co.tradesmanager.data.repository

import il.co.tradesmanager.data.local.dao.MusterDao
import il.co.tradesmanager.data.local.dao.ScheduleDao
import il.co.tradesmanager.data.local.entity.MusterEntity
import il.co.tradesmanager.data.local.entity.MusterPersonEntity
import il.co.tradesmanager.data.local.entity.TaskBlockEntity
import il.co.tradesmanager.data.local.entity.TimeEntryEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * The roll call tables, in memory.
 *
 * The queries are reimplemented rather than stubbed. Two of them carry rules:
 * [observeLive] and [liveNow] are what stops a second roll call starting while
 * one is running, and a fake that returned the first row it had would let a
 * test pass over a query that returned the wrong one.
 */
class FakeMusterDao : MusterDao {

    private val musters = MutableStateFlow<List<MusterEntity>>(emptyList())
    private val people = MutableStateFlow<List<MusterPersonEntity>>(emptyList())

    override suspend fun upsert(muster: MusterEntity) {
        musters.value = musters.value.filterNot { it.id == muster.id } + muster
    }

    override suspend fun upsertPerson(person: MusterPersonEntity) {
        people.value = people.value.filterNot { it.id == person.id } + person
    }

    override suspend fun upsertPeople(rows: List<MusterPersonEntity>) {
        rows.forEach { upsertPerson(it) }
    }

    override fun observe(id: String): Flow<MusterEntity?> =
        musters.map { rows -> rows.firstOrNull { it.id == id } }

    override suspend fun muster(id: String): MusterEntity? = musters.value.firstOrNull { it.id == id }

    override fun observeLive(): Flow<MusterEntity?> = musters.map { rows -> live(rows) }

    override suspend fun liveNow(): MusterEntity? = live(musters.value)

    private fun live(rows: List<MusterEntity>): MusterEntity? =
        rows.filter { it.endedAt == null }.maxByOrNull { it.startedAt }

    override fun observeForCompany(companyId: String?, limit: Int): Flow<List<MusterEntity>> =
        musters.map { rows ->
            rows.filter { it.companyId == companyId }
                .sortedByDescending { it.startedAt }
                .take(limit)
        }

    override fun observePeople(musterId: String): Flow<List<MusterPersonEntity>> =
        people.map { rows -> rows.filter { it.musterId == musterId } }

    override suspend fun people(musterId: String): List<MusterPersonEntity> =
        people.value.filter { it.musterId == musterId }

    override suspend fun person(id: String): MusterPersonEntity? =
        people.value.firstOrNull { it.id == id }
}

/**
 * The schedule tables, in memory, for the one thing a roll call reads from
 * them: who is still checked in.
 *
 * [openCheckIns] is reimplemented with the ordering the real query has, oldest
 * first, because the roll call's own ordering depends on it and a fake that
 * returned insertion order would hide a query that did not sort at all.
 */
class FakeScheduleDao : ScheduleDao {

    val timeEntries = mutableListOf<TimeEntryEntity>()

    override suspend fun openCheckIns(): List<TimeEntryEntity> =
        timeEntries.filter { it.checkOutAt == null }.sortedBy { it.checkInAt }.take(1000)

    override fun observeOnSiteCount(): Flow<Int> =
        MutableStateFlow(timeEntries.count { it.checkOutAt == null })

    override suspend fun upsertTimeEntry(entry: TimeEntryEntity) {
        timeEntries.removeAll { it.id == entry.id }
        timeEntries += entry
    }

    override fun observeOpenTimeEntry(): Flow<TimeEntryEntity?> =
        MutableStateFlow(timeEntries.filter { it.checkOutAt == null }.maxByOrNull { it.checkInAt })

    override fun observeTimeEntries(projectId: String): Flow<List<TimeEntryEntity>> =
        MutableStateFlow(timeEntries.filter { it.projectId == projectId })

    override fun observeCompletedTimeEntries(projectId: String): Flow<List<TimeEntryEntity>> =
        MutableStateFlow(
            timeEntries.filter { it.projectId == projectId && it.checkOutAt != null },
        )

    /* The task-block half of the DAO, which a roll call never touches. */

    override fun observeDay(epochDay: Long): Flow<List<TaskBlockEntity>> =
        MutableStateFlow(emptyList())

    override fun observeRange(from: Long, to: Long): Flow<List<TaskBlockEntity>> =
        MutableStateFlow(emptyList())

    override suspend fun block(id: String): TaskBlockEntity? = null

    override suspend fun upsert(block: TaskBlockEntity) = Unit

    override suspend fun upsertAll(blocks: List<TaskBlockEntity>) = Unit

    override suspend fun delete(id: String) = Unit

    override suspend fun setDone(id: String, done: Boolean, now: Long) = Unit
}
