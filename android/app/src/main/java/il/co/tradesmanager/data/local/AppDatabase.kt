package il.co.tradesmanager.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import il.co.tradesmanager.data.local.dao.AuditDao
import il.co.tradesmanager.data.local.dao.CatalogDao
import il.co.tradesmanager.data.local.dao.InventoryDao
import il.co.tradesmanager.data.local.dao.ProjectDao
import il.co.tradesmanager.data.local.dao.SafetyDao
import il.co.tradesmanager.data.local.dao.ScheduleDao
import il.co.tradesmanager.data.local.entity.AuditLogEntity
import il.co.tradesmanager.data.local.entity.CatalogItemEntity
import il.co.tradesmanager.data.local.entity.ChecklistRunEntity
import il.co.tradesmanager.data.local.entity.ChecklistRunItemEntity
import il.co.tradesmanager.data.local.entity.ChecklistTemplateEntity
import il.co.tradesmanager.data.local.entity.ChecklistTemplateItemEntity
import il.co.tradesmanager.data.local.entity.IncidentEntity
import il.co.tradesmanager.data.local.entity.InventoryItemEntity
import il.co.tradesmanager.data.local.entity.MilestoneEntity
import il.co.tradesmanager.data.local.entity.PhotoEntity
import il.co.tradesmanager.data.local.entity.ProjectEntity
import il.co.tradesmanager.data.local.entity.ProjectMaterialEntity
import il.co.tradesmanager.data.local.entity.ProjectTaskEntity
import il.co.tradesmanager.data.local.entity.StockMovementEntity
import il.co.tradesmanager.data.local.entity.SupplierEntity
import il.co.tradesmanager.data.local.entity.TaskBlockEntity
import il.co.tradesmanager.data.local.entity.TeamMemberEntity
import il.co.tradesmanager.data.local.entity.TimeEntryEntity
import il.co.tradesmanager.data.local.entity.TradeEntity

@Database(
    entities = [
        TradeEntity::class,
        CatalogItemEntity::class,
        ChecklistTemplateEntity::class,
        ChecklistTemplateItemEntity::class,
        InventoryItemEntity::class,
        StockMovementEntity::class,
        SupplierEntity::class,
        PhotoEntity::class,
        ProjectEntity::class,
        ProjectMaterialEntity::class,
        ProjectTaskEntity::class,
        MilestoneEntity::class,
        TaskBlockEntity::class,
        TimeEntryEntity::class,
        ChecklistRunEntity::class,
        ChecklistRunItemEntity::class,
        IncidentEntity::class,
        TeamMemberEntity::class,
        AuditLogEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun catalogDao(): CatalogDao
    abstract fun inventoryDao(): InventoryDao
    abstract fun projectDao(): ProjectDao
    abstract fun scheduleDao(): ScheduleDao
    abstract fun safetyDao(): SafetyDao
    abstract fun auditDao(): AuditDao

    companion object {
        const val NAME = "tradesmanager.db"
    }
}
