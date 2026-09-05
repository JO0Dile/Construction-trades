package il.co.tradesmanager.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import il.co.tradesmanager.data.local.entity.ScaffoldEntity
import il.co.tradesmanager.data.local.entity.ScaffoldInspectionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ScaffoldDao {

    @Upsert
    suspend fun upsert(scaffold: ScaffoldEntity)

    @Delete
    suspend fun delete(scaffold: ScaffoldEntity)

    @Query("SELECT * FROM scaffolds WHERE id = :id")
    fun observe(id: String): Flow<ScaffoldEntity?>

    /**
     * Standing scaffolds first, then the ones that have been struck down.
     *
     * SQL cannot say which is overdue — that needs the clock and the site's
     * own time zone — so the ordering here is only the part that does not
     * depend on when somebody looks. Kotlin sorts the rest.
     */
    @Query(
        """
        SELECT * FROM scaffolds
        WHERE projectId = :projectId
        ORDER BY dismantledAt IS NOT NULL, lastInspectedAt IS NULL DESC, lastInspectedAt
        LIMIT 300
        """,
    )
    fun observeForProject(projectId: String): Flow<List<ScaffoldEntity>>

    @Query("SELECT COUNT(*) FROM scaffolds")
    suspend fun count(): Int

    /**
     * Inserted, never updated.
     *
     * An inspection is a statement about what somebody saw at a moment. The
     * register is worth keeping only because that statement cannot be revised
     * afterwards, so there is no update here to reach for by accident.
     */
    @Insert
    suspend fun insertInspection(inspection: ScaffoldInspectionEntity)

    @Query(
        """
        SELECT * FROM scaffold_inspections
        WHERE scaffoldId = :scaffoldId
        ORDER BY inspectedAt DESC
        LIMIT 100
        """,
    )
    fun observeInspections(scaffoldId: String): Flow<List<ScaffoldInspectionEntity>>
}
