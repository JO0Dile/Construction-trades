package il.co.tradesmanager.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import il.co.tradesmanager.data.local.entity.CertificationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CertificationDao {

    @Upsert
    suspend fun upsert(certification: CertificationEntity)

    @Delete
    suspend fun delete(certification: CertificationEntity)

    @Query("SELECT * FROM certifications WHERE accountId = :accountId ORDER BY expiresOn IS NULL, expiresOn")
    fun observeForAccount(accountId: String): Flow<List<CertificationEntity>>

    /**
     * Every ticket in the company, soonest to expire first.
     *
     * One query for the whole list rather than one per person: the People
     * screen shows a warning badge on every row, and a query per row is a
     * round trip per row on every recomposition.
     */
    @Query("SELECT * FROM certifications ORDER BY expiresOn IS NULL, expiresOn")
    fun observeAll(): Flow<List<CertificationEntity>>

    /** Tickets already lapsed or lapsing inside the window, for the dashboard. */
    @Query(
        """
        SELECT * FROM certifications
        WHERE expiresOn IS NOT NULL AND expiresOn <= :before
        ORDER BY expiresOn
        """,
    )
    fun observeExpiringBefore(before: Long): Flow<List<CertificationEntity>>
}
