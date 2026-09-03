import SwiftData
import SwiftUI

struct InventoryEditView: View {

    @Environment(Localization.self) private var loc
    @Environment(AppSettings.self) private var settings
    @Environment(\.modelContext) private var context
    @Environment(\.dismiss) private var dismiss

    /// nil means a new item.
    let item: StockItem?

    @State private var name = ""
    @State private var spec = ""
    @State private var category = ""
    @State private var quantity = "0"
    @State private var minStock = "0"
    @State private var unit = "PCS"
    @State private var barcode = ""
    @State private var price = ""
    @State private var tags = ""
    @State private var showScanner = false
    @State private var loaded = false

    private var isNew: Bool { item?.modelContext == nil }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    TextField(loc["inv_name"], text: $name)
                    TextField(loc["inv_spec"], text: $spec, axis: .vertical)
                    TextField(loc["inv_category"], text: $category)
                }

                Section {
                    TextField(loc["inv_quantity"], text: $quantity).keyboardType(.decimalPad)
                    TextField(loc["inv_min_stock"], text: $minStock).keyboardType(.decimalPad)
                    TextField(loc["inv_unit"], text: $unit)
                }

                Section {
                    HStack {
                        TextField(loc["inv_barcode"], text: $barcode)
                        Button { showScanner = true } label: {
                            Image(systemName: "barcode.viewfinder")
                        }
                        .buttonStyle(.borderless)
                        .accessibilityLabel(loc["inv_scan"])
                    }
                    TextField(loc["inv_price"], text: $price).keyboardType(.decimalPad)
                    TextField(loc["inv_tags"], text: $tags)
                }

                if let item, !isNew {
                    Section {
                        Button(loc["action_delete"], role: .destructive) {
                            DataStore(context: context, settings: settings).delete(item)
                            dismiss()
                        }
                    }
                }
            }
            .navigationTitle(loc[isNew ? "inv_add_item" : "inv_edit_item"])
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(loc["action_cancel"]) { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button(loc["action_save"], action: save)
                        .disabled(name.trimmingCharacters(in: .whitespaces).isEmpty)
                }
            }
            .sheet(isPresented: $showScanner) {
                BarcodeScannerView { code in
                    barcode = code
                    showScanner = false
                }
            }
            .onAppear(perform: load)
        }
    }

    private func load() {
        guard !loaded else { return }
        loaded = true
        guard let item else { return }
        name = loc.resolve(item.names)
        spec = loc.resolve(item.spec)
        category = item.category
        quantity = Self.trim(item.quantity)
        minStock = Self.trim(item.minStock)
        unit = item.unit
        barcode = item.barcode ?? ""
        price = item.purchasePrice.map(Self.trim) ?? ""
        tags = item.tags.joined(separator: ", ")
    }

    /// A user's edit writes into the *active* language only, so a catalogue
    /// item keeps its Hebrew and Arabic names when someone corrects it in
    /// English — otherwise the first rename would discard the other two.
    private func save() {
        let target = item ?? StockItem()
        let language = String(loc.languageCode.prefix(while: { $0 != "-" }))

        target.names[language] = name
        target.spec[language] = spec
        target.category = category
        target.quantity = Double(quantity) ?? 0
        target.minStock = Double(minStock) ?? 0
        target.unit = unit.isEmpty ? "PCS" : unit
        target.barcode = barcode.isEmpty ? nil : barcode
        target.purchasePrice = Double(price)
        target.tags = tags.split(separator: ",")
            .map { $0.trimmingCharacters(in: .whitespaces) }
            .filter { !$0.isEmpty }

        DataStore(context: context, settings: settings).save(target, isNew: isNew)
        dismiss()
    }

    private static func trim(_ value: Double) -> String {
        value.truncatingRemainder(dividingBy: 1) == 0
            ? String(Int(value))
            : String(value)
    }
}
