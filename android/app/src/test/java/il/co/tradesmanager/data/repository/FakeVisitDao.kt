package il.co.tradesmanager.data.repository

import il.co.tradesmanager.data.local.dao.VisitDao
import il.co.tradesmanager.data.local.entity.SiteVisitEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** The visitor log, in memory. */
class FakeVisitDao : VisitDao {
    val rows = MutableStateFlow<List<SiteVisitEntity>>(emptyList())

    override suspend fun upsert(visit: SiteVisitEntity) {
        rows.value = rows.value.filterNot { it.id == visit.id } + visit
    }

    override suspend fun visit(id: String): SiteVisitEntity? = rows.value.firstOrNull { it.id == id }

    override fun observeForProject(projectId: String, limit: Int): Flow<List<SiteVisitEntity>> =
        rows.map { all ->
            all.filter { it.projectId == projectId }.sortedByDescending { it.arrivedAt }.take(limit)
        }

    override suspend fun stillHere(): List<SiteVisitEntity> =
        rows.value.filter { it.leftAt == null }.sortedBy { it.arrivedAt }

    override fun observeStillHereCount(): Flow<Int> = rows.map { all -> all.count { it.leftAt == null } }

    override suspend fun all(): List<SiteVisitEntity> = rows.value

}
