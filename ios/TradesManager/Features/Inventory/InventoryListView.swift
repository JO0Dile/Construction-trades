import SwiftData
import SwiftUI

struct InventoryListView: View {

    @Environment(Localization.self) private var loc
    @Environment(AppSettings.self) private var settings
    @Environment(\.modelContext) private var context

    @Query private var allItems: [StockItem]

    @State private var query = ""
    @State private var kind: String?
    @State private var lowStockOnly = false
    @State private var editing: StockItem?
    @State private var creating = false
    @State private var showScanner = false
    @State private var showExport = false

    private static let kindFilters: [(kind: String?, key: String)] = [
        (nil, "inv_filter_all"),
        ("TOOL", "kind_tool"),
        ("MATERIAL", "kind_material"),
        ("SAFETY", "kind_safety"),
        ("CONSUMABLE", "kind_consumable"),
    ]

    /// One filter chain, matching the Android SQL query: the low-stock filter
    /// and the low-stock badge use the same rule so they cannot disagree.
    private var items: [StockItem] {
        let needle = query.trimmingCharacters(in: .whitespaces).lowercased()
        return allItems
            .filter { $0.deletedAt == nil }
            .filter { needle.isEmpty || $0.searchIndex.contains(needle) }
            .filter { kind == nil || $0.kind == kind }
            .filter { !lowStockOnly || $0.isLowStock }
            .sorted {
                if $0.isLowStock != $1.isLowStock { return $0.isLowStock }
                return $0.updatedAt > $1.updatedAt
            }
    }

    private var lowStockCount: Int {
        allItems.filter { $0.deletedAt == nil && $0.isLowStock }.count
    }

    private var store: DataStore { DataStore(context: context, settings: settings) }

    var body: some View {
        NavigationStack {
            List {
                Section {
                    Picker(loc["action_filter"], selection: $kind) {
                        ForEach(Self.kindFilters, id: \.key) { filter in
                            Text(loc[filter.key]).tag(filter.kind)
                        }
                    }
                    .pickerStyle(.menu)

                    Toggle(isOn: $lowStockOnly) {
                        HStack {
                            Text(loc["inv_filter_low"])
                            if lowStockCount > 0 {
                                Text("\(lowStockCount)")
                                    .font(.caption)
                                    .padding(.horizontal, 6)
                                    .background(Capsule().fill(.red.opacity(0.15)))
                            }
                        }
                    }
                }

                if items.isEmpty {
                    ContentUnavailableView(loc["inv_empty"], systemImage: "shippingbox", description: Text(loc["inv_from_catalog"]))
                } else {
                    ForEach(items) { item in
                        InventoryRow(item: item) { delta in
                            store.adjustStock(item, by: delta, reason: delta > 0 ? "restocked" : "used_on_site")
                        } onEdit: {
                            editing = item
                        }
                    }
                }
            }
            .searchable(text: $query, prompt: loc["inv_search_hint"])
            .navigationTitle(loc["inv_title"])
            .toolbar {
                ToolbarItem(placement: .primaryAction) {
                    Button { creating = true } label: {
                        Label(loc["inv_add_item"], systemImage: "plus")
                    }
                }
                ToolbarItem(placement: .topBarLeading) {
                    Menu {
                        Button { showScanner = true } label: {
                            Label(loc["inv_scan"], systemImage: "barcode.viewfinder")
                        }
                        Button { showExport = true } label: {
                            Label(loc["set_export"], systemImage: "square.and.arrow.up")
                        }
                    } label: {
                        Image(systemName: "ellipsis.circle")
                    }
                }
            }
            .sheet(isPresented: $creating) { InventoryEditView(item: nil) }
            .sheet(item: $editing) { item in InventoryEditView(item: item) }
            .sheet(isPresented: $showScanner) {
                BarcodeScannerView { code in
                    showScanner = false
                    handleScan(code)
                }
            }
            .sheet(isPresented: $showExport) {
                ExportSheet(items: items)
            }
        }
    }

    /// A scan that matches opens the item; one that does not starts a new item
    /// with the code already filled in, which is the whole point of scanning a
    /// label on a box you have never stocked before.
    private func handleScan(_ code: String) {
        if let match = store.item(withBarcode: code) {
            editing = match
        } else {
            // Not inserted into the context yet: the editor treats an item with
            // no modelContext as new, and discards it if the user cancels.
            editing = StockItem(names: [loc.languageCode: ""], barcode: code)
        }
    }
}

private struct InventoryRow: View {

    @Environment(Localization.self) private var loc

    let item: StockItem
    let onAdjust: (Double) -> Void
    let onEdit: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            if item.isLowStock {
                Text(loc["inv_low_stock"]).font(.caption2).foregroundStyle(.red)
            }
            Text(loc.resolve(item.names)).font(.body)
            Text(loc.resolve(item.spec))
                .font(.caption)
                .foregroundStyle(.secondary)
                .lineLimit(2)

            HStack {
                Button { onAdjust(-1) } label: { Image(systemName: "minus.circle") }
                    .buttonStyle(.borderless)
                    .accessibilityLabel(loc["inv_stock_remove"])

                Button(action: onEdit) {
                    Text("\(Formats.quantity(item.quantity, locale: loc.locale)) \(loc[UnitLabel.key(for: item.unit)])")
                        .monospacedDigit()
                }
                .buttonStyle(.bordered)

                Button { onAdjust(1) } label: { Image(systemName: "plus.circle") }
                    .buttonStyle(.borderless)
                    .accessibilityLabel(loc["inv_stock_add"])
            }
            .padding(.top, 2)
        }
        .padding(.vertical, 2)
    }
}

/// Units are catalogue data, so an unknown code degrades to "pcs" rather than
/// showing a raw enum name to a user.
enum UnitLabel {
    static func key(for unit: String) -> String {
        switch unit.uppercased() {
        case "M": return "unit_m"
        case "M2": return "unit_m2"
        case "M3": return "unit_m3"
        case "KG": return "unit_kg"
        case "L": return "unit_l"
        case "ROLL": return "unit_roll"
        case "BAG": return "unit_bag"
        case "PAIR": return "unit_pair"
        case "BOX": return "unit_box"
        default: return "unit_pcs"
        }
    }
}
