package il.co.tradesmanager.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import il.co.tradesmanager.data.local.entity.CompanyEntity
import il.co.tradesmanager.data.local.entity.MembershipEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MembershipDao {

    @Upsert
    suspend fun upsert(membership: MembershipEntity)

    @Query("SELECT * FROM memberships WHERE accountId = :accountId ORDER BY joinedAt DESC")
    fun observeFor(accountId: String): Flow<List<MembershipEntity>>

    @Query("SELECT * FROM memberships WHERE accountId = :accountId ORDER BY joinedAt DESC")
    suspend fun forAccount(accountId: String): List<MembershipEntity>

    @Query("SELECT * FROM memberships WHERE id = :id")
    suspend fun membership(id: String): MembershipEntity?

    /**
     * Everybody currently on one company's books — the People list.
     *
     * Scoped to the company on purpose. A device shared between two firms must
     * not show one firm's crew to the other, and the old query, which listed
     * every account on the device, did exactly that.
     */
    @Query(
        """
        SELECT * FROM memberships
        WHERE leftAt IS NULL
          AND ((:companyId IS NULL AND companyId IS NULL) OR companyId = :companyId)
        """,
    )
    fun observeForCompany(companyId: String?): Flow<List<MembershipEntity>>

    /** Every membership of a company, so the last-owner guard can count. */
    @Query("SELECT * FROM memberships WHERE companyId = :companyId")
    suspend fun forCompany(companyId: String): List<MembershipEntity>

    /**
     * Somebody's current membership of one company, or null.
     *
     * What the gate asks before admitting anybody, so a second tap on the
     * button does not produce a second membership. Current only: a spell that
     * ended is history and must not stop them coming back.
     *
     * The null-safe companyId comparison is the same shape as
     * [observeForCompany] and for the same reason -- `companyId = NULL` is
     * never true in SQL, so a personal membership would never be found.
     */
    @Query(
        """
        SELECT * FROM memberships
        WHERE accountId = :accountId
          AND leftAt IS NULL
          AND ((:companyId IS NULL AND companyId IS NULL) OR companyId = :companyId)
        LIMIT 1
        """,
    )
    suspend fun currentFor(accountId: String, companyId: String?): MembershipEntity?

    @Query("SELECT * FROM companies ORDER BY name")
    fun observeCompanies(): Flow<List<CompanyEntity>>

    @Query("SELECT * FROM companies WHERE id = :id")
    fun observeCompanyById(id: String): Flow<CompanyEntity?>

    @Query("UPDATE memberships SET role = :role WHERE id = :id")
    suspend fun setRole(id: String, role: String)

    /** Coming off the books, not being deleted from history. */
    @Query("UPDATE memberships SET leftAt = :at WHERE id = :id")
    suspend fun markLeft(id: String, at: Long)
}
