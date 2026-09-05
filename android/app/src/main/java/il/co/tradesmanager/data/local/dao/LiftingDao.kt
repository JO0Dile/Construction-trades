package il.co.tradesmanager.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import il.co.tradesmanager.data.local.entity.LiftCrewEntity
import il.co.tradesmanager.data.local.entity.LiftPlanEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LiftingDao {

    @Upsert
    suspend fun upsertPlan(plan: LiftPlanEntity)

    @Delete
    suspend fun deletePlan(plan: LiftPlanEntity)

    @Query("SELECT * FROM lift_plans WHERE id = :id")
    fun observePlan(id: String): Flow<LiftPlanEntity?>

    /**
     * Lifts still to happen first, soonest at the top; then what has been done.
     *
     * `plannedFor IS NULL` sorts last within the pending group: a plan with no
     * date is a draft somebody started, not the next thing off the ground.
     */
    @Query(
        """
        SELECT * FROM lift_plans
        WHERE projectId = :projectId
        ORDER BY completedAt IS NOT NULL, plannedFor IS NULL, plannedFor
        LIMIT 200
        """,
    )
    fun observePlans(projectId: String): Flow<List<LiftPlanEntity>>

    @Query("SELECT COUNT(*) FROM lift_plans")
    suspend fun planCount(): Int

    @Upsert
    suspend fun upsertCrew(member: LiftCrewEntity)

    @Delete
    suspend fun deleteCrew(member: LiftCrewEntity)

    @Query("SELECT * FROM lift_crew WHERE planId = :planId")
    fun observeCrew(planId: String): Flow<List<LiftCrewEntity>>

    /** One person per role: setting a role replaces whoever was in it. */
    @Query("DELETE FROM lift_crew WHERE planId = :planId AND role = :role")
    suspend fun clearRole(planId: String, role: String)
}
