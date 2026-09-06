package il.co.tradesmanager.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import il.co.tradesmanager.data.local.entity.PaymentApplicationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PaymentsDao {

    @Upsert
    suspend fun upsert(application: PaymentApplicationEntity)

    @Delete
    suspend fun delete(application: PaymentApplicationEntity)

    @Query("SELECT * FROM payment_applications WHERE id = :id")
    fun observe(id: String): Flow<PaymentApplicationEntity?>

    /**
     * Unpaid first, then by application number descending.
     *
     * Newest application at the top of the unpaid group, because the one
     * somebody has just raised is the one they are looking for.
     */
    @Query(
        """
        SELECT * FROM payment_applications
        WHERE projectId = :projectId
        ORDER BY paidAt IS NOT NULL, applicationNumber DESC
        LIMIT 200
        """,
    )
    fun observeForProject(projectId: String): Flow<List<PaymentApplicationEntity>>

    /**
     * The highest number used on this job in this direction, or null.
     *
     * Per direction, because our applications to the client and a
     * subcontractor's to us are two separate sequences that both start at one.
     */
    @Query(
        """
        SELECT MAX(applicationNumber) FROM payment_applications
        WHERE projectId = :projectId AND direction = :direction
        """,
    )
    suspend fun lastNumber(projectId: String, direction: String): Int?

    /**
     * The furthest-advanced application that has actually been paid.
     *
     * Returns the row rather than a computed net figure. Working the net out
     * in SQL would mean a second implementation of the retention rule — one
     * that could not see the limit of retention, and would quietly disagree
     * with `core.money.Payments` on any job that had run over. There is one
     * place that knows how retention works, and it is not this file.
     *
     * Ordered by application number rather than by when it was paid: the
     * applications are cumulative, so the highest number is the one that
     * carries the running total, whatever order the cheques arrived in.
     */
    @Query(
        """
        SELECT * FROM payment_applications
        WHERE projectId = :projectId AND direction = :direction
          AND paidAt IS NOT NULL AND certifiedGrossToDate IS NOT NULL
        ORDER BY applicationNumber DESC
        LIMIT 1
        """,
    )
    suspend fun lastPaid(projectId: String, direction: String): PaymentApplicationEntity?

    @Query("SELECT COUNT(*) FROM payment_applications")
    suspend fun count(): Int
}
