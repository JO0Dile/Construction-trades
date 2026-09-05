package il.co.tradesmanager.data.catalog

import il.co.tradesmanager.core.i18n.LocalizedText
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The on-disk shape of shared/assets/catalog. These classes are the contract
 * between the catalogue authors and both apps: the identical files are read by
 * the iOS target, so a change here is a change to a published data format, not
 * an internal refactor. See docs/CATALOG_FORMAT.md.
 */
@Serializable
data class CatalogManifest(
    val schemaVersion: Int,
    val catalogVersion: Int,
    val revisedOn: String = "",
    val sourceNote: LocalizedText = emptyMap(),
    /**
     * Which icon each catalogue category is drawn with. Shared with iOS so the
     * two apps agree on what a cable or a valve looks like; each platform maps
     * the name to its own asset. A new category needs an entry here, not a
     * code change.
     */
    val categoryIcons: Map<String, String> = emptyMap(),
    /**
     * Suggested kinds of place a job happens in. Content, not code: a Hebrew
     * "לובי" is a translation, and a user can always type something the list
     * does not offer.
     */
    val projectKinds: List<ProjectKind> = emptyList(),
    val trades: List<CatalogTrade> = emptyList(),
)

@Serializable
data class ProjectKind(
    val id: String,
    val names: LocalizedText,
)

@Serializable
data class CatalogTrade(
    val id: String,
    val icon: String = "construction",
    val colorHex: String = "#7F8C8D",
    val names: LocalizedText,
    val itemsFile: String,
    val safetyFile: String? = null,
    val templatesFile: String? = null,
)

@Serializable
data class CatalogItemFile(
    val tradeId: String,
    val catalogVersion: Int,
    val items: List<CatalogItemDto> = emptyList(),
)

@Serializable
data class CatalogItemDto(
    val id: String,
    val kind: String,
    val category: String,
    val unit: String,
    val names: LocalizedText,
    val spec: LocalizedText = emptyMap(),
    val attributes: Map<String, String> = emptyMap(),
    val tags: List<String> = emptyList(),
)

@Serializable
data class SafetyFile(
    val tradeId: String,
    val catalogVersion: Int,
    val checklists: List<SafetyChecklistDto> = emptyList(),
)

@Serializable
data class SafetyChecklistDto(
    val id: String,
    val mandatoryBeforeWork: Boolean = false,
    val titles: LocalizedText,
    val references: List<String> = emptyList(),
    val items: List<SafetyCheckDto> = emptyList(),
)

@Serializable
data class SafetyCheckDto(
    val id: String,
    val critical: Boolean = false,
    val texts: LocalizedText,
)

@Serializable
data class TemplateFile(
    val tradeId: String,
    val catalogVersion: Int,
    val templates: List<ProjectTemplateDto> = emptyList(),
)

@Serializable
data class ProjectTemplateDto(
    val id: String,
    val estimatedDays: Int = 1,
    val names: LocalizedText,
    val descriptions: LocalizedText = emptyMap(),
    val materials: List<TemplateMaterialDto> = emptyList(),
    val tasks: List<TemplateTaskDto> = emptyList(),
)

@Serializable
data class TemplateMaterialDto(
    val itemId: String,
    val quantity: Double,
)

@Serializable
data class TemplateTaskDto(
    val id: String,
    @SerialName("order") val sortOrder: Int = 0,
    val titles: LocalizedText,
)
