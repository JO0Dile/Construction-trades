package il.co.tradesmanager.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import il.co.tradesmanager.data.local.entity.ConcretePourEntity
import il.co.tradesmanager.data.local.entity.ConcreteTicketEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ConcreteDao {

    @Upsert
    suspend fun upsertPour(pour: ConcretePourEntity)

    @Delete
    suspend fun deletePour(pour: ConcretePourEntity)

    @Query("SELECT * FROM concrete_pours WHERE id = :id")
    fun observePour(id: String): Flow<ConcretePourEntity?>

    @Query("SELECT * FROM concrete_pours WHERE id = :id")
    suspend fun pour(id: String): ConcretePourEntity?

    @Query(
        """
        SELECT * FROM concrete_pours
        WHERE projectId = :projectId
        ORDER BY completedAt IS NOT NULL, startedAt DESC
        LIMIT 200
        """,
    )
    fun observePours(projectId: String): Flow<List<ConcretePourEntity>>

    @Query("SELECT COUNT(*) FROM concrete_pours")
    suspend fun pourCount(): Int

    @Upsert
    suspend fun upsertTicket(ticket: ConcreteTicketEntity)

    @Delete
    suspend fun deleteTicket(ticket: ConcreteTicketEntity)

    /**
     * Oldest batch first, which is the order they run out in.
     *
     * SQL cannot tell which load is closest to the end of its working life —
     * that depends on the clock and on how hot it is — but batching order is
     * the same order, so the list is already roughly right before Kotlin has
     * looked at it.
     */
    @Query("SELECT * FROM concrete_tickets WHERE pourId = :pourId ORDER BY dispatchedAt")
    fun observeTickets(pourId: String): Flow<List<ConcreteTicketEntity>>

    /**
     * What actually went in: delivered volume, rejected loads left out.
     *
     * A rejected truck is kept as a row because sending one away is one of the
     * more important things that happened that day — but it never counts
     * towards what is in the structure.
     */
    @Query(
        """
        SELECT COALESCE(SUM(volume), 0) FROM concrete_tickets
        WHERE pourId = :pourId AND rejected = 0 AND dischargedAt IS NOT NULL
        """,
    )
    fun observePlacedVolume(pourId: String): Flow<Double>
}
