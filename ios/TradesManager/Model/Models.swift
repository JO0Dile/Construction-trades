import Foundation
import SwiftData

// The SwiftData mirror of the Room schema in
// android/app/src/main/java/il/co/tradesmanager/data/local/entity.
//
// Two deliberate choices carried over from Android:
//
//  - Localised text is a [String: String] keyed by language code, not a column
//    per language, so a fourth language needs no migration on either platform.
//  - Rows reference each other by id rather than by SwiftData relationship,
//    matching the Room schema field for field. It makes the two databases
//    readable side by side, which is what a shared sync format will need.

@Model
final class Trade {
    @Attribute(.unique) var id: String
    var names: [String: String]
    var icon: String
    var colorHex: String
    var sortOrder: Int
    var isSelected: Bool

    init(id: String, names: [String: String], icon: String, colorHex: String, sortOrder: Int, isSelected: Bool = false) {
        self.id = id
        self.names = names
        self.icon = icon
        self.colorHex = colorHex
        self.sortOrder = sortOrder
        self.isSelected = isSelected
    }
}

/// Read-only reference data seeded from the shared catalogues. Never edited by
/// a user — their own stock lives in `StockItem`.
@Model
final class CatalogEntry {
    @Attribute(.unique) var id: String
    var tradeId: String
    var kind: String
    var category: String
    var unit: String
    var names: [String: String]
    var spec: [String: String]
    var attributes: [String: String]
    var tags: [String]
    var catalogVersion: Int
    var searchIndex: String

    init(
        id: String, tradeId: String, kind: String, category: String, unit: String,
        names: [String: String], spec: [String: String], attributes: [String: String],
        tags: [String], catalogVersion: Int, searchIndex: String
    ) {
        self.id = id
        self.tradeId = tradeId
        self.kind = kind
        self.category = category
        self.unit = unit
        self.names = names
        self.spec = spec
        self.attributes = attributes
        self.tags = tags
        self.catalogVersion = catalogVersion
        self.searchIndex = searchIndex
    }
}

/// A row in this user's own stock list. `catalogItemId` is the seeder's
/// duplicate guard: an id already stocked is never stocked a second time.
@Model
final class StockItem {
    @Attribute(.unique) var id: String
    var catalogItemId: String?
    var tradeId: String?
    var kind: String
    var category: String
    var unit: String
    var names: [String: String]
    var spec: [String: String]
    var attributes: [String: String]
    var tags: [String]
    var quantity: Double
    var minStock: Double
    var supplierName: String?
    var purchasePrice: Double?
    var barcode: String?
    var searchIndex: String
    var createdAt: Date
    var updatedAt: Date
    var deletedAt: Date?

    var isLowStock: Bool { minStock > 0 && quantity <= minStock }
    var isCustom: Bool { catalogItemId == nil }

    init(
        id: String = UUID().uuidString,
        catalogItemId: String? = nil, tradeId: String? = nil,
        kind: String = "MATERIAL", category: String = "", unit: String = "PCS",
        names: [String: String] = [:], spec: [String: String] = [:],
        attributes: [String: String] = [:], tags: [String] = [],
        quantity: Double = 0, minStock: Double = 0,
        supplierName: String? = nil, purchasePrice: Double? = nil, barcode: String? = nil,
        searchIndex: String = "", createdAt: Date = .now, updatedAt: Date = .now,
        deletedAt: Date? = nil
    ) {
        self.id = id
        self.catalogItemId = catalogItemId
        self.tradeId = tradeId
        self.kind = kind
        self.category = category
        self.unit = unit
        self.names = names
        self.spec = spec
        self.attributes = attributes
        self.tags = tags
        self.quantity = quantity
        self.minStock = minStock
        self.supplierName = supplierName
        self.purchasePrice = purchasePrice
        self.barcode = barcode
        self.searchIndex = searchIndex
        self.createdAt = createdAt
        self.updatedAt = updatedAt
        self.deletedAt = deletedAt
    }
}

/// Append-only record of every stock change: who, when, how much and why.
@Model
final class StockMovement {
    @Attribute(.unique) var id: String
    var itemId: String
    var delta: Double
    var resultingQuantity: Double
    var reason: String
    var projectId: String?
    var actorName: String
    var occurredAt: Date

    init(
        id: String = UUID().uuidString, itemId: String, delta: Double,
        resultingQuantity: Double, reason: String, projectId: String? = nil,
        actorName: String, occurredAt: Date = .now
    ) {
        self.id = id
        self.itemId = itemId
        self.delta = delta
        self.resultingQuantity = resultingQuantity
        self.reason = reason
        self.projectId = projectId
        self.actorName = actorName
        self.occurredAt = occurredAt
    }
}

@Model
final class Project {
    @Attribute(.unique) var id: String
    var name: String
    var kindLabel: String
    var parentProjectId: String?
    var street: String?
    var city: String?
    var clientName: String?
    var clientPhone: String?
    var status: String
    var startDate: Date?
    var dueDate: Date?
    var notes: String?
    var templateId: String?
    var createdAt: Date
    var updatedAt: Date
    var deletedAt: Date?

    init(
        id: String = UUID().uuidString, name: String, kindLabel: String,
        parentProjectId: String? = nil, street: String? = nil, city: String? = nil,
        clientName: String? = nil, clientPhone: String? = nil,
        status: String = ProjectStatus.planned.rawValue,
        startDate: Date? = nil, dueDate: Date? = nil, notes: String? = nil,
        templateId: String? = nil, createdAt: Date = .now, updatedAt: Date = .now,
        deletedAt: Date? = nil
    ) {
        self.id = id
        self.name = name
        self.kindLabel = kindLabel
        self.parentProjectId = parentProjectId
        self.street = street
        self.city = city
        self.clientName = clientName
        self.clientPhone = clientPhone
        self.status = status
        self.startDate = startDate
        self.dueDate = dueDate
        self.notes = notes
        self.templateId = templateId
        self.createdAt = createdAt
        self.updatedAt = updatedAt
        self.deletedAt = deletedAt
    }
}

/// Matches `ProjectRepository.Status` on Android, string for string.
enum ProjectStatus: String, CaseIterable {
    case planned = "PLANNED"
    case active = "ACTIVE"
    case onHold = "ON_HOLD"
    case done = "DONE"

    var labelKey: String {
        switch self {
        case .planned: return "proj_status_planned"
        case .active: return "proj_status_active"
        case .onHold: return "proj_status_hold"
        case .done: return "proj_status_done"
        }
    }
}

@Model
final class ProjectMaterial {
    @Attribute(.unique) var id: String
    var projectId: String
    var stockItemId: String?
    var catalogItemId: String?
    var label: String
    var unit: String
    var requiredQuantity: Double
    var allocatedQuantity: Double
    var sortOrder: Int

    init(
        id: String = UUID().uuidString, projectId: String, stockItemId: String? = nil,
        catalogItemId: String? = nil, label: String, unit: String,
        requiredQuantity: Double, allocatedQuantity: Double = 0, sortOrder: Int = 0
    ) {
        self.id = id
        self.projectId = projectId
        self.stockItemId = stockItemId
        self.catalogItemId = catalogItemId
        self.label = label
        self.unit = unit
        self.requiredQuantity = requiredQuantity
        self.allocatedQuantity = allocatedQuantity
        self.sortOrder = sortOrder
    }
}

@Model
final class ProjectTask {
    @Attribute(.unique) var id: String
    var projectId: String
    var title: String
    var sortOrder: Int
    var isDone: Bool
    var doneAt: Date?
    var doneByName: String?

    init(
        id: String = UUID().uuidString, projectId: String, title: String,
        sortOrder: Int, isDone: Bool = false, doneAt: Date? = nil, doneByName: String? = nil
    ) {
        self.id = id
        self.projectId = projectId
        self.title = title
        self.sortOrder = sortOrder
        self.isDone = isDone
        self.doneAt = doneAt
        self.doneByName = doneByName
    }
}

/// A block on a day's plan. The day is an epoch day and the times are minutes
/// past midnight, so a block never shifts when a phone crosses a timezone —
/// the 07:00 start stays 07:00. Same rule as Android.
@Model
final class TaskBlock {
    @Attribute(.unique) var id: String
    var epochDay: Int
    var startMinute: Int
    var endMinute: Int
    var title: String
    var notes: String?
    var projectId: String?
    var isDone: Bool
    var createdAt: Date
    var updatedAt: Date

    init(
        id: String = UUID().uuidString, epochDay: Int, startMinute: Int, endMinute: Int,
        title: String, notes: String? = nil, projectId: String? = nil,
        isDone: Bool = false, createdAt: Date = .now, updatedAt: Date = .now
    ) {
        self.id = id
        self.epochDay = epochDay
        self.startMinute = startMinute
        self.endMinute = endMinute
        self.title = title
        self.notes = notes
        self.projectId = projectId
        self.isDone = isDone
        self.createdAt = createdAt
        self.updatedAt = updatedAt
    }
}

@Model
final class TimeEntry {
    @Attribute(.unique) var id: String
    var projectId: String?
    var workerName: String
    var checkInAt: Date
    var checkOutAt: Date?
    var latitude: Double?
    var longitude: Double?

    var minutesWorked: Int? {
        guard let checkOutAt else { return nil }
        return Int(checkOutAt.timeIntervalSince(checkInAt) / 60)
    }

    init(
        id: String = UUID().uuidString, projectId: String? = nil, workerName: String,
        checkInAt: Date = .now, checkOutAt: Date? = nil,
        latitude: Double? = nil, longitude: Double? = nil
    ) {
        self.id = id
        self.projectId = projectId
        self.workerName = workerName
        self.checkInAt = checkInAt
        self.checkOutAt = checkOutAt
        self.latitude = latitude
        self.longitude = longitude
    }
}

@Model
final class SafetyTemplate {
    @Attribute(.unique) var id: String
    var tradeId: String
    var titles: [String: String]
    var mandatoryBeforeWork: Bool
    var references: [String]
    var catalogVersion: Int

    init(
        id: String, tradeId: String, titles: [String: String],
        mandatoryBeforeWork: Bool, references: [String], catalogVersion: Int
    ) {
        self.id = id
        self.tradeId = tradeId
        self.titles = titles
        self.mandatoryBeforeWork = mandatoryBeforeWork
        self.references = references
        self.catalogVersion = catalogVersion
    }
}

@Model
final class SafetyTemplateCheck {
    @Attribute(.unique) var id: String
    var templateId: String
    var texts: [String: String]
    /// A critical check that is failed or unanswered blocks sign-off entirely.
    var critical: Bool
    var sortOrder: Int

    init(id: String, templateId: String, texts: [String: String], critical: Bool, sortOrder: Int) {
        self.id = id
        self.templateId = templateId
        self.texts = texts
        self.critical = critical
        self.sortOrder = sortOrder
    }
}

@Model
final class ChecklistRun {
    @Attribute(.unique) var id: String
    var templateId: String
    var projectId: String?
    var startedAt: Date
    var completedAt: Date?
    var signedByName: String?
    var blocked: Bool

    init(
        id: String = UUID().uuidString, templateId: String, projectId: String? = nil,
        startedAt: Date = .now, completedAt: Date? = nil,
        signedByName: String? = nil, blocked: Bool = true
    ) {
        self.id = id
        self.templateId = templateId
        self.projectId = projectId
        self.startedAt = startedAt
        self.completedAt = completedAt
        self.signedByName = signedByName
        self.blocked = blocked
    }
}

@Model
final class ChecklistAnswer {
    /// "\(runId):\(checkId)" — answering the same check twice replaces the
    /// answer instead of leaving two contradictory rows behind.
    @Attribute(.unique) var id: String
    var runId: String
    var checkId: String
    var state: String
    var note: String?
    var answeredAt: Date

    init(runId: String, checkId: String, state: String, note: String? = nil, answeredAt: Date = .now) {
        self.id = "\(runId):\(checkId)"
        self.runId = runId
        self.checkId = checkId
        self.state = state
        self.note = note
        self.answeredAt = answeredAt
    }
}

enum CheckState: String {
    case pass = "PASS"
    case fail = "FAIL"
    case notApplicable = "NOT_APPLICABLE"
}

/// Append-only. There is no update path, and the only delete is the retention
/// purge, which records its own purge entry.
@Model
final class AuditEntry {
    @Attribute(.unique) var id: String
    var entityType: String
    var entityId: String
    var action: String
    var actorName: String
    var summary: String
    var occurredAt: Date

    init(
        id: String = UUID().uuidString, entityType: String, entityId: String,
        action: String, actorName: String, summary: String, occurredAt: Date = .now
    ) {
        self.id = id
        self.entityType = entityType
        self.entityId = entityId
        self.action = action
        self.actorName = actorName
        self.summary = summary
        self.occurredAt = occurredAt
    }
}

enum AuditAction {
    static let create = "CREATE"
    static let update = "UPDATE"
    static let delete = "DELETE"
    static let stockChange = "STOCK_CHANGE"
    static let signOff = "SIGN_OFF"
    static let export = "EXPORT"
    static let seed = "SEED"
    static let purge = "PURGE"
}
