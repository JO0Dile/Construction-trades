import Foundation

/// Swift mirror of the catalogue format in `shared/assets/catalog`.
///
/// These types and the Kotlin ones in `data/catalog/CatalogAssets.kt` describe
/// the same files. The format is documented in `docs/CATALOG_FORMAT.md`; adding
/// a field means adding it in both places, which is why the format has a
/// `schemaVersion` that a loader can refuse.
public struct CatalogManifest: Codable, Sendable {
    public let schemaVersion: Int
    public let catalogVersion: Int
    public let revisedOn: String?
    public let sourceNote: LocalizedText?
    public let trades: [CatalogTrade]
}

public struct CatalogTrade: Codable, Sendable, Identifiable {
    public let id: String
    public let icon: String
    public let colorHex: String
    public let names: LocalizedText
    public let itemsFile: String
    public let safetyFile: String?
    public let templatesFile: String?
}

public struct CatalogItemFile: Codable, Sendable {
    public let tradeId: String
    public let catalogVersion: Int
    public let items: [CatalogItem]
}

public struct CatalogItem: Codable, Sendable, Identifiable {
    public let id: String
    public let kind: String
    public let category: String
    public let unit: String
    public let names: LocalizedText
    public let spec: LocalizedText
    public let attributes: [String: String]
    public let tags: [String]
}

public struct SafetyFile: Codable, Sendable {
    public let tradeId: String
    public let catalogVersion: Int
    public let checklists: [SafetyChecklist]
}

public struct SafetyChecklist: Codable, Sendable, Identifiable {
    public let id: String
    public let mandatoryBeforeWork: Bool
    public let titles: LocalizedText
    public let references: [String]
    public let items: [SafetyCheck]
}

public struct SafetyCheck: Codable, Sendable, Identifiable {
    public let id: String
    public let critical: Bool
    public let texts: LocalizedText
}

public struct TemplateFile: Codable, Sendable {
    public let tradeId: String
    public let catalogVersion: Int
    public let templates: [ProjectTemplate]
}

public struct ProjectTemplate: Codable, Sendable, Identifiable {
    public let id: String
    public let estimatedDays: Int
    public let names: LocalizedText
    public let descriptions: LocalizedText
    public let materials: [TemplateMaterial]
    public let tasks: [TemplateTask]
}

public struct TemplateMaterial: Codable, Sendable {
    public let itemId: String
    public let quantity: Double
}

public struct TemplateTask: Codable, Sendable, Identifiable {
    public let id: String
    public let order: Int
    public let titles: LocalizedText
}
