package il.co.tradesmanager.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import il.co.tradesmanager.data.local.entity.WasteLoadEntity
import kotlinx.coroutines.flow.Flow

/** Write and read. There is no delete: a load that left the site left it. */
@Dao
interface WasteDao {

    @Upsert
    suspend fun upsert(load: WasteLoadEntity)

    @Query("SELECT * FROM waste_loads WHERE id = :id")
    suspend fun load(id: String): WasteLoadEntity?

    /** One job's loads, newest first. */
    @Query("SELECT * FROM waste_loads WHERE projectId = :projectId ORDER BY removedAt DESC")
    fun observeForProject(projectId: String): Flow<List<WasteLoadEntity>>
}
