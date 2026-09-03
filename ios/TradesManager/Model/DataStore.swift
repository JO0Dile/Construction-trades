import Foundation
import SwiftData

/// The behaviour behind the screens — the iOS counterpart of the Android
/// repositories, and deliberately a faithful port rather than a fresh design.
/// Where a rule matters (stock never moves without a movement row; a critical
/// safety check blocks sign-off) it is implemented here, once, so no view can
/// route around it.
@MainActor
final class DataStore {

    private let context: ModelContext
    private let settings: AppSettings

    init(context: ModelContext, settings: AppSettings) {
        self.context = context
        self.settings = settings
    }

    private var actor: String { settings.actorName.isEmpty ? "unknown" : settings.actorName }

    // MARK: - Audit

    /// Every change of consequence goes through here, on the same code path as
    /// the change itself, so a mutation without an audit row would have to be a
    /// deliberate edit rather than an oversight at a call site.
    private func record(_ type: String, _ id: String, _ action: String, _ summary: String) {
        context.insert(
            AuditEntry(entityType: type, entityId: id, action: action, actorName: actor, summary: summary)
        )
    }

    // MARK: - Catalogue

    /// Fills the read-only catalogue and safety tables from the shared JSON.
    /// Idempotent: it upserts by id and never touches anything a user typed.
    func seedReferenceData(from source: CatalogSource = CatalogSource()) throws -> Int {
        let manifest = try source.manifest()

        let existingTrades = try context.fetch(FetchDescriptor<Trade>())
        let tradesById = Dictionary(uniqueKeysWithValues: existingTrades.map { ($0.id, $0) })
        // Fetched once, not once per trade: the loop below inserts into these
        // sets as it goes, so a re-fetch each iteration would be both wasteful
        // and no more correct.
        var knownEntryIds = Set(try context.fetch(FetchDescriptor<CatalogEntry>()).map(\.id))
        var knownTemplateIds = Set(try context.fetch(FetchDescriptor<SafetyTemplate>()).map(\.id))

        for (index, trade) in manifest.trades.enumerated() {
            if let existing = tradesById[trade.id] {
                existing.names = trade.names
                existing.icon = trade.icon
                existing.colorHex = trade.colorHex
                existing.sortOrder = index
            } else {
                context.insert(
                    Trade(
                        id: trade.id, names: trade.names, icon: trade.icon,
                        colorHex: trade.colorHex, sortOrder: index
                    )
                )
            }

            let file = try source.items(for: trade)
            for item in file.items where !knownEntryIds.contains(item.id) {
                context.insert(
                    CatalogEntry(
                        id: item.id, tradeId: trade.id, kind: item.kind, category: item.category,
                        unit: item.unit, names: item.names, spec: item.spec,
                        attributes: item.attributes, tags: item.tags,
                        catalogVersion: file.catalogVersion,
                        searchIndex: Self.searchIndex(names: item.names, spec: item.spec, tags: item.tags, category: item.category)
                    )
                )
                knownEntryIds.insert(item.id)
            }

            if let safety = try source.safety(for: trade) {
                for list in safety.checklists where !knownTemplateIds.contains(list.id) {
                    context.insert(
                        SafetyTemplate(
                            id: list.id, tradeId: trade.id, titles: list.titles,
                            mandatoryBeforeWork: list.mandatoryBeforeWork,
                            references: list.references, catalogVersion: safety.catalogVersion
                        )
                    )
                    for (order, check) in list.items.enumerated() {
                        context.insert(
                            SafetyTemplateCheck(
                                id: check.id, templateId: list.id, texts: check.texts,
                                critical: check.critical, sortOrder: order
                            )
                        )
                    }
                    knownTemplateIds.insert(list.id)
                }
            }
        }

        record("catalog", "manifest", AuditAction.seed, "Loaded catalogue v\(manifest.catalogVersion)")
        try context.save()
        return manifest.catalogVersion
    }

    /// Stocks the user's inventory from the catalogues of the given trades.
    ///
    /// Returns how many rows were actually added — zero on a repeat run, which
    /// is the whole point of the duplicate guard. Seeded rows start at quantity
    /// zero: the catalogue says what exists in the trade, not what is in this
    /// user's van. Counting is theirs to do.
    @discardableResult
    func stockTrades(_ tradeIds: Set<String>, from source: CatalogSource = CatalogSource()) throws -> Int {
        guard !tradeIds.isEmpty else { return 0 }

        let alreadyStocked = Set(
            try context.fetch(FetchDescriptor<StockItem>()).compactMap(\.catalogItemId)
        )
        let manifest = try source.manifest()
        var added = 0

        for trade in manifest.trades where tradeIds.contains(trade.id) {
            for item in try source.items(for: trade).items where !alreadyStocked.contains(item.id) {
                context.insert(
                    StockItem(
                        catalogItemId: item.id, tradeId: trade.id, kind: item.kind,
                        category: item.category, unit: item.unit, names: item.names,
                        spec: item.spec, attributes: item.attributes, tags: item.tags,
                        searchIndex: Self.searchIndex(names: item.names, spec: item.spec, tags: item.tags, category: item.category)
                    )
                )
                added += 1
            }
        }

        if added > 0 {
            record("inventory", tradeIds.sorted().joined(separator: ","), AuditAction.seed, "Stocked \(added) catalogue items")
        }
        try context.save()
        return added
    }

    func setTradeSelected(_ trade: Trade, _ selected: Bool) throws {
        trade.isSelected = selected
        // Turning a trade on stocks its catalogue; turning one off never
        // removes stock, because the quantities are the user's own record.
        if selected { try stockTrades([trade.id]) }
        try context.save()
    }

    var selectedTradeIds: [String] {
        ((try? context.fetch(FetchDescriptor<Trade>())) ?? []).filter(\.isSelected).map(\.id)
    }

    // MARK: - Inventory

    /// Moves stock and writes the movement in the same call. Stock is clamped
    /// at zero: a van cannot hold minus three sockets, and a negative figure
    /// would quietly corrupt every cost report built on it.
    func adjustStock(_ item: StockItem, by delta: Double, reason: String, projectId: String? = nil) {
        let before = item.quantity
        let resulting = max(0, before + delta)
        item.quantity = resulting
        item.updatedAt = .now

        context.insert(
            StockMovement(
                itemId: item.id, delta: resulting - before, resultingQuantity: resulting,
                reason: reason, projectId: projectId, actorName: actor
            )
        )
        record("inventory_item", item.id, AuditAction.stockChange, "\(before) -> \(resulting) (\(reason))")
        try? context.save()
    }

    func save(_ item: StockItem, isNew: Bool) {
        item.updatedAt = .now
        item.searchIndex = Self.searchIndex(
            names: item.names, spec: item.spec, tags: item.tags,
            category: item.category, barcode: item.barcode
        )
        if isNew { context.insert(item) }
        record(
            "inventory_item", item.id,
            isNew ? AuditAction.create : AuditAction.update,
            item.names.values.first ?? ""
        )
        try? context.save()
    }

    /// Soft delete: an audited app never loses the row that explains a movement.
    func delete(_ item: StockItem) {
        item.deletedAt = .now
        item.updatedAt = .now
        record("inventory_item", item.id, AuditAction.delete, "Item removed from inventory")
        try? context.save()
    }

    func item(withBarcode barcode: String) -> StockItem? {
        let trimmed = barcode.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return nil }
        let all = (try? context.fetch(FetchDescriptor<StockItem>())) ?? []
        return all.first { $0.deletedAt == nil && $0.barcode == trimmed }
    }

    // MARK: - Projects

    /// Expands a template into a project. Material labels are resolved in the
    /// language that is active now, so a job sheet reads in the language of
    /// whoever set it up rather than the template's authoring language.
    @discardableResult
    func createProject(
        from template: ProjectTemplate,
        tradeId: String,
        name: String,
        languageCode: String,
        source: CatalogSource = CatalogSource()
    ) throws -> Project {
        let resolvedName = name.isEmpty ? Localised.resolve(template.names, languageCode: languageCode) : name
        let project = Project(
            name: resolvedName,
            kindLabel: Localised.resolve(template.names, languageCode: languageCode),
            notes: Localised.resolve(template.descriptions, languageCode: languageCode),
            templateId: template.id
        )
        context.insert(project)

        let catalogue = Dictionary(
            uniqueKeysWithValues: try context.fetch(FetchDescriptor<CatalogEntry>()).map { ($0.id, $0) }
        )
        for (index, line) in template.materials.enumerated() {
            let entry = catalogue[line.itemId]
            context.insert(
                ProjectMaterial(
                    projectId: project.id,
                    catalogItemId: line.itemId,
                    label: entry.map { Localised.resolve($0.names, languageCode: languageCode) } ?? line.itemId,
                    unit: entry?.unit ?? "PCS",
                    requiredQuantity: line.quantity,
                    sortOrder: index
                )
            )
        }
        for (index, task) in template.tasks.sorted(by: { $0.order < $1.order }).enumerated() {
            context.insert(
                ProjectTask(
                    projectId: project.id,
                    title: Localised.resolve(task.titles, languageCode: languageCode),
                    sortOrder: index
                )
            )
        }

        record("project", project.id, AuditAction.create, "Created from template \(template.id)")
        try context.save()
        return project
    }

    func setTaskDone(_ task: ProjectTask, _ done: Bool) {
        task.isDone = done
        task.doneAt = done ? .now : nil
        task.doneByName = done ? actor : nil
        record("project_task", task.id, AuditAction.update, done ? "done" : "reopened")
        try? context.save()
    }

    func templates(source: CatalogSource = CatalogSource()) -> [(tradeId: String, template: ProjectTemplate)] {
        let selected = Set(selectedTradeIds)
        guard let manifest = try? source.manifest() else { return [] }
        return manifest.trades
            .filter { selected.contains($0.id) }
            .flatMap { trade in
                ((try? source.templates(for: trade))?.templates ?? []).map { (trade.id, $0) }
            }
    }

    // MARK: - Schedule

    func addBlock(on day: Int, title: String, start: Int, end: Int) {
        guard !title.trimmingCharacters(in: .whitespaces).isEmpty else { return }
        let range = TimeOfDay.sanitiseRange(start: start, end: end)
        let block = TaskBlock(
            epochDay: day, startMinute: range.start, endMinute: range.end, title: title
        )
        context.insert(block)
        record("task_block", block.id, AuditAction.create, title)
        try? context.save()
    }

    func setBlockDone(_ block: TaskBlock, _ done: Bool) {
        block.isDone = done
        block.updatedAt = .now
        record("task_block", block.id, AuditAction.update, done ? "done" : "reopened")
        try? context.save()
    }

    func delete(_ block: TaskBlock) {
        record("task_block", block.id, AuditAction.delete, "Time block removed")
        context.delete(block)
        try? context.save()
    }

    /// "Same again tomorrow" — copies a day's blocks onto another date.
    func copyBlocks(_ blocks: [TaskBlock], to day: Int) {
        for block in blocks {
            context.insert(
                TaskBlock(
                    epochDay: day, startMinute: block.startMinute, endMinute: block.endMinute,
                    title: block.title, notes: block.notes, projectId: block.projectId
                )
            )
        }
        record("task_block", String(day), AuditAction.create, "Copied \(blocks.count) blocks")
        try? context.save()
    }

    func openTimeEntry() -> TimeEntry? {
        let all = (try? context.fetch(FetchDescriptor<TimeEntry>())) ?? []
        return all.filter { $0.checkOutAt == nil }.max(by: { $0.checkInAt < $1.checkInAt })
    }

    /// A refused location permission costs the GPS stamp, not the check-in.
    func toggleCheckIn(latitude: Double?, longitude: Double?) {
        if let open = openTimeEntry() {
            open.checkOutAt = .now
            record("time_entry", open.id, AuditAction.update, "Checked out after \(open.minutesWorked ?? 0) min")
        } else {
            let entry = TimeEntry(
                workerName: actor, latitude: latitude, longitude: longitude
            )
            context.insert(entry)
            record("time_entry", entry.id, AuditAction.create, "Checked in")
        }
        try? context.save()
    }

    // MARK: - Safety

    func startRun(templateId: String) -> ChecklistRun {
        let run = ChecklistRun(templateId: templateId)
        context.insert(run)
        record("checklist_run", run.id, AuditAction.create, "Started checklist \(templateId)")
        try? context.save()
        return run
    }

    func answer(run: ChecklistRun, checkId: String, state: CheckState) {
        let id = "\(run.id):\(checkId)"
        let existing = ((try? context.fetch(FetchDescriptor<ChecklistAnswer>())) ?? []).first { $0.id == id }
        if let existing {
            existing.state = state.rawValue
            existing.answeredAt = .now
        } else {
            context.insert(ChecklistAnswer(runId: run.id, checkId: checkId, state: state.rawValue))
        }
        refreshBlockedState(run)
        try? context.save()
    }

    /// Recomputed from the answers every time rather than tracked as a flag a
    /// screen could clear.
    @discardableResult
    func refreshBlockedState(_ run: ChecklistRun) -> Bool {
        let criticalIds = Set(
            ((try? context.fetch(FetchDescriptor<SafetyTemplateCheck>())) ?? [])
                .filter { $0.templateId == run.templateId && $0.critical }
                .map(\.id)
        )
        let answers = Dictionary(
            uniqueKeysWithValues: ((try? context.fetch(FetchDescriptor<ChecklistAnswer>())) ?? [])
                .filter { $0.runId == run.id }
                .map { ($0.checkId, $0.state) }
        )
        let blocked = criticalIds.contains { answers[$0] == nil || answers[$0] == CheckState.fail.rawValue }
        run.blocked = blocked
        return blocked
    }

    /// Returns false, changing nothing, when a critical check is outstanding:
    /// the regulation the checklist encodes is not something a signature is
    /// allowed to override.
    func signOff(_ run: ChecklistRun, signerName: String) -> Bool {
        guard !refreshBlockedState(run) else { return false }
        run.completedAt = .now
        run.signedByName = signerName
        run.blocked = false
        record("checklist_run", run.id, AuditAction.signOff, "Checklist signed")
        try? context.save()
        return true
    }

    // MARK: - Deletion

    /// Account and data deletion, as both stores require it to be offered.
    func deleteEverything() {
        // delete(model:) is generic over a concrete PersistentModel, so these
        // cannot be looped over as `any PersistentModel.Type`.
        try? context.delete(model: StockItem.self)
        try? context.delete(model: StockMovement.self)
        try? context.delete(model: Project.self)
        try? context.delete(model: ProjectMaterial.self)
        try? context.delete(model: ProjectTask.self)
        try? context.delete(model: TaskBlock.self)
        try? context.delete(model: TimeEntry.self)
        try? context.delete(model: ChecklistRun.self)
        try? context.delete(model: ChecklistAnswer.self)
        try? context.delete(model: AuditEntry.self)
        try? context.delete(model: CatalogEntry.self)
        try? context.delete(model: SafetyTemplate.self)
        try? context.delete(model: SafetyTemplateCheck.self)
        try? context.delete(model: Trade.self)
        settings.reset()
        try? context.save()
    }

    // MARK: - Helpers

    /// Every translation, spec and tag in one lowercased string, so a search
    /// typed in Hebrew finds an item the user remembers in English.
    static func searchIndex(
        names: [String: String], spec: [String: String], tags: [String],
        category: String, barcode: String? = nil
    ) -> String {
        [
            Localised.searchable(names),
            Localised.searchable(spec),
            tags.joined(separator: " ").lowercased(),
            category.lowercased(),
            (barcode ?? "").lowercased(),
        ].joined(separator: " ").trimmingCharacters(in: .whitespaces)
    }
}
