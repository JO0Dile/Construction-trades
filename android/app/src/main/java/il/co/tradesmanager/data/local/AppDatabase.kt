package il.co.tradesmanager.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import il.co.tradesmanager.data.local.dao.AccountDao
import il.co.tradesmanager.data.local.dao.AuditDao
import il.co.tradesmanager.data.local.dao.CatalogDao
import il.co.tradesmanager.data.local.dao.CertificationDao
import il.co.tradesmanager.data.local.dao.ConcreteDao
import il.co.tradesmanager.data.local.dao.DailyLogDao
import il.co.tradesmanager.data.local.dao.EquipmentDao
import il.co.tradesmanager.data.local.dao.EvidenceDao
import il.co.tradesmanager.data.local.dao.InventoryDao
import il.co.tradesmanager.data.local.dao.MembershipDao
import il.co.tradesmanager.data.local.dao.MoneyDao
import il.co.tradesmanager.data.local.dao.PhotoDao
import il.co.tradesmanager.data.local.dao.ProjectDao
import il.co.tradesmanager.data.local.dao.PurchasingDao
import il.co.tradesmanager.data.local.dao.SafetyDao
import il.co.tradesmanager.data.local.dao.ScaffoldDao
import il.co.tradesmanager.data.local.dao.ScheduleDao
import il.co.tradesmanager.data.local.entity.AccountEntity
import il.co.tradesmanager.data.local.entity.AuditLogEntity
import il.co.tradesmanager.data.local.entity.CatalogItemEntity
import il.co.tradesmanager.data.local.entity.CertificationEntity
import il.co.tradesmanager.data.local.entity.CompanyEntity
import il.co.tradesmanager.data.local.entity.ChecklistRunEntity
import il.co.tradesmanager.data.local.entity.ConcretePourEntity
import il.co.tradesmanager.data.local.entity.ConcreteTicketEntity
import il.co.tradesmanager.data.local.entity.CostEntryEntity
import il.co.tradesmanager.data.local.entity.DailyLogEntity
import il.co.tradesmanager.data.local.entity.ChecklistRunItemEntity
import il.co.tradesmanager.data.local.entity.ChecklistTemplateEntity
import il.co.tradesmanager.data.local.entity.ChecklistTemplateItemEntity
import il.co.tradesmanager.data.local.entity.EquipmentEntity
import il.co.tradesmanager.data.local.entity.IncidentEntity
import il.co.tradesmanager.data.local.entity.InventoryItemEntity
import il.co.tradesmanager.data.local.entity.InvoiceEntity
import il.co.tradesmanager.data.local.entity.JobBudgetEntity
import il.co.tradesmanager.data.local.entity.MembershipEntity
import il.co.tradesmanager.data.local.entity.MilestoneEntity
import il.co.tradesmanager.data.local.entity.PermitEntity
import il.co.tradesmanager.data.local.entity.PermitPrecautionEntity
import il.co.tradesmanager.data.local.entity.PhotoEntity
import il.co.tradesmanager.data.local.entity.ProjectEntity
import il.co.tradesmanager.data.local.entity.ProjectMaterialEntity
import il.co.tradesmanager.data.local.entity.ProjectTaskEntity
import il.co.tradesmanager.data.local.entity.PurchaseOrderEntity
import il.co.tradesmanager.data.local.entity.PurchaseOrderLineEntity
import il.co.tradesmanager.data.local.entity.ScaffoldEntity
import il.co.tradesmanager.data.local.entity.ScaffoldInspectionEntity
import il.co.tradesmanager.data.local.entity.SnagEntity
import il.co.tradesmanager.data.local.entity.StockMovementEntity
import il.co.tradesmanager.data.local.entity.SupplierEntity
import il.co.tradesmanager.data.local.entity.TaskBlockEntity
import il.co.tradesmanager.data.local.entity.TeamMemberEntity
import il.co.tradesmanager.data.local.entity.TimeEntryEntity
import il.co.tradesmanager.data.local.entity.ToolboxTalkAttendeeEntity
import il.co.tradesmanager.data.local.entity.ToolboxTalkEntity
import il.co.tradesmanager.data.local.entity.TradeEntity
import il.co.tradesmanager.data.local.entity.VariationEntity

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
        CompanyEntity::class,
        AccountEntity::class,
        JobBudgetEntity::class,
        CostEntryEntity::class,
        VariationEntity::class,
        InvoiceEntity::class,
        CertificationEntity::class,
        EquipmentEntity::class,
        PurchaseOrderEntity::class,
        PurchaseOrderLineEntity::class,
        ToolboxTalkEntity::class,
        ToolboxTalkAttendeeEntity::class,
        PermitEntity::class,
        PermitPrecautionEntity::class,
        MembershipEntity::class,
        SnagEntity::class,
        DailyLogEntity::class,
        ConcretePourEntity::class,
        ConcreteTicketEntity::class,
        ScaffoldEntity::class,
        ScaffoldInspectionEntity::class,
    ],
    version = 13,
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
    abstract fun photoDao(): PhotoDao
    abstract fun accountDao(): AccountDao
    abstract fun moneyDao(): MoneyDao
    abstract fun certificationDao(): CertificationDao
    abstract fun equipmentDao(): EquipmentDao
    abstract fun purchasingDao(): PurchasingDao
    abstract fun evidenceDao(): EvidenceDao
    abstract fun membershipDao(): MembershipDao
    abstract fun dailyLogDao(): DailyLogDao
    abstract fun concreteDao(): ConcreteDao

    abstract fun scaffoldDao(): ScaffoldDao

    companion object {
        const val NAME = "tradesmanager.db"
    }
}
