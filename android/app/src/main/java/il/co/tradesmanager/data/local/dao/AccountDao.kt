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

    /**
     * Everyone who could be the person typing that name or ID number.
     *
     * Three ways in: the username the site office issued, the ID number, or —
     * for accounts made before usernames existed — the display name. More than
     * one match is not an error: two Hammams on one job is an ordinary
     * Tuesday, so every match is returned and the password decides which was
     * meant.
     *
     * Matching is case-insensitive and ignores surrounding spaces, because a
     * name typed with gloves on at seven in the morning is not typed
     * carefully.
     *
     * NOCASE is ASCII-only in SQLite, so it does nothing for Hebrew or Arabic
     * names — which have no case for it to fold, so nothing is lost.
     */
    @Query(
        """
        SELECT * FROM accounts
        WHERE deletedAt IS NULL
          AND (TRIM(username) = TRIM(:identifier) COLLATE NOCASE
               OR TRIM(idNumber) = TRIM(:identifier) COLLATE NOCASE
               OR TRIM(displayName) = TRIM(:identifier) COLLATE NOCASE)
        ORDER BY lastSignInAt IS NULL, lastSignInAt DESC
        """,
    )
    suspend fun accountsMatching(identifier: String): List<AccountEntity>

    @Query("SELECT COUNT(*) FROM accounts WHERE deletedAt IS NULL")
    suspend fun accountCount(): Int

    /**
     * How many people can still administer the company. Used to refuse the
     * removal or demotion that would lock everyone out of their own data.
     */
    @Query("SELECT COUNT(*) FROM accounts WHERE deletedAt IS NULL AND role = :role")
    suspend fun countWithRole(role: String): Int

    /** Whether an ID number is already spoken for, so sign-up can say so. */
    @Query(
        """
        SELECT COUNT(*) FROM accounts
        WHERE deletedAt IS NULL AND TRIM(idNumber) = TRIM(:idNumber) COLLATE NOCASE
        """,
    )
    suspend fun countWithIdNumber(idNumber: String): Int

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
