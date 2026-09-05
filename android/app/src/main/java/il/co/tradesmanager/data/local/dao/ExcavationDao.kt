package il.co.tradesmanager.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import il.co.tradesmanager.data.local.entity.ExcavationEntity
import il.co.tradesmanager.data.local.entity.ExcavationInspectionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ExcavationDao {

    @Upsert
    suspend fun upsert(excavation: ExcavationEntity)

    @Delete
    suspend fun delete(excavation: ExcavationEntity)

    @Query("SELECT * FROM excavations WHERE id = :id")
    fun observe(id: String): Flow<ExcavationEntity?>

    /**
     * Open trenches first, filled ones last.
     *
     * SQL cannot say which are inspected for today — that needs the clock and
     * the site's zone — so this orders only what does not depend on when
     * somebody looks. Kotlin sorts the rest.
     */
    @Query(
        """
        SELECT * FROM excavations
        WHERE projectId = :projectId
        ORDER BY backfilledAt IS NOT NULL, createdAt
        LIMIT 300
        """,
    )
    fun observeForProject(projectId: String): Flow<List<ExcavationEntity>>

    @Query("SELECT COUNT(*) FROM excavations")
    suspend fun count(): Int

    /** Inserted, never updated: an inspection is a statement about a moment. */
    @Insert
    suspend fun insertInspection(inspection: ExcavationInspectionEntity)

    @Query(
        """
        SELECT * FROM excavation_inspections
        WHERE excavationId = :excavationId
        ORDER BY inspectedAt DESC
        LIMIT 100
        """,
    )
    fun observeInspections(excavationId: String): Flow<List<ExcavationInspectionEntity>>
}
