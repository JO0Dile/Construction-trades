package il.co.tradesmanager.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import il.co.tradesmanager.data.local.entity.AccountEntity
import il.co.tradesmanager.data.local.entity.CompanyEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {

    @Upsert
    suspend fun upsertCompany(company: CompanyEntity)

    @Query("SELECT * FROM companies WHERE id = :id")
    suspend fun company(id: String): CompanyEntity?

    @Query("SELECT * FROM companies LIMIT 1")
    fun observeCompany(): Flow<CompanyEntity?>

    @Upsert
    suspend fun upsert(account: AccountEntity)

    /** The sign-in list: everyone still on the books, longest-idle last. */
    @Query(
        """
        SELECT * FROM accounts
        WHERE deletedAt IS NULL
        ORDER BY lastSignInAt IS NULL, lastSignInAt DESC, displayName
        """,
    )
    fun observeAccounts(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE id = :id AND deletedAt IS NULL")
    fun observeAccount(id: String): Flow<AccountEntity?>

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun account(id: String): AccountEntity?

    @Query("SELECT COUNT(*) FROM accounts WHERE deletedAt IS NULL")
    suspend fun accountCount(): Int

    /**
     * How many people can still administer the company. Used to refuse the
     * removal or demotion that would lock everyone out of their own data.
     */
    @Query("SELECT COUNT(*) FROM accounts WHERE deletedAt IS NULL AND role = :role")
    suspend fun countWithRole(role: String): Int

    @Query("UPDATE accounts SET lastSignInAt = :at WHERE id = :id")
    suspend fun recordSignIn(id: String, at: Long)

    @Query("UPDATE accounts SET role = :role WHERE id = :id")
    suspend fun setRole(id: String, role: String)

    @Query("UPDATE accounts SET passcodeHash = :hash, passcodeSalt = :salt WHERE id = :id")
    suspend fun setPasscode(id: String, hash: String?, salt: String?)

    /** Soft delete: the audit trail names people, so their rows have to stay. */
    @Query("UPDATE accounts SET deletedAt = :at WHERE id = :id")
    suspend fun softDelete(id: String, at: Long)
}
