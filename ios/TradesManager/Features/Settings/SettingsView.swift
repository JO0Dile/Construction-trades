import SwiftData
import SwiftUI

struct SettingsView: View {

    @Environment(Localization.self) private var loc
    @Environment(AppSettings.self) private var settings
    @Environment(\.modelContext) private var context
    @Environment(\.dismiss) private var dismiss

    @Query(sort: \Trade.sortOrder) private var trades: [Trade]

    @State private var confirmDelete = false
    @State private var reseeded: Int?

    private var store: DataStore { DataStore(context: context, settings: settings) }

    var body: some View {
        NavigationStack {
            Form {
                Section(loc["set_language"]) {
                    ForEach(loc.supported) { language in
                        Button {
                            loc.select(language.code)
                        } label: {
                            HStack {
                                Text(language.endonym)
                                Spacer()
                                if loc.languageCode.hasPrefix(language.code) {
                                    Image(systemName: "checkmark")
                                }
                            }
                        }
                        .foregroundStyle(.primary)
                    }
                }

                Section(loc["set_theme"]) {
                    Picker(loc["set_theme"], selection: Binding(
                        get: { settings.themeMode },
                        set: { settings.themeMode = $0 }
                    )) {
                        ForEach(AppSettings.ThemeMode.allCases, id: \.self) { mode in
                            Text(loc[mode.labelKey]).tag(mode)
                        }
                    }
                    .pickerStyle(.segmented)

                    Toggle(loc["set_large_text"], isOn: Binding(
                        get: { settings.largeText },
                        set: { settings.largeText = $0 }
                    ))
                }

                Section(loc["saf_signed_by"]) {
                    TextField(loc["saf_signed_by"], text: Binding(
                        get: { settings.actorName },
                        set: { settings.actorName = $0 }
                    ))
                }

                Section {
                    ForEach(trades) { trade in
                        Toggle(loc.resolve(trade.names), isOn: Binding(
                            get: { trade.isSelected },
                            set: { try? store.setTradeSelected(trade, $0) }
                        ))
                    }
                    Button(loc["set_reseed"]) {
                        _ = try? store.seedReferenceData()
                        reseeded = try? store.stockTrades(Set(store.selectedTradeIds))
                    }
                    if let reseeded {
                        Text(loc.plural("items_count", reseeded))
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                } header: {
                    Text(loc["set_trades"])
                } footer: {
                    Text(loc["set_reseed_note"])
                }

                Section {
                    Button(loc["set_delete_data"], role: .destructive) {
                        confirmDelete = true
                    }
                } footer: {
                    Text("\(loc["set_catalog_version"]): \(settings.seededCatalogVersion)")
                }
            }
            .navigationTitle(loc["set_title"])
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button(loc["action_close"]) { dismiss() }
                }
            }
            .confirmationDialog(
                loc["set_delete_data"],
                isPresented: $confirmDelete,
                titleVisibility: .visible
            ) {
                Button(loc["action_delete"], role: .destructive) {
                    store.deleteEverything()
                    dismiss()
                }
                Button(loc["action_cancel"], role: .cancel) {}
            }
        }
    }
}
