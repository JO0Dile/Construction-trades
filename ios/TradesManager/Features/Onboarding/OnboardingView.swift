import SwiftData
import SwiftUI

struct OnboardingView: View {

    @Environment(Localization.self) private var loc
    @Environment(AppSettings.self) private var settings
    @Environment(\.modelContext) private var context

    @Query(sort: \Trade.sortOrder) private var trades: [Trade]

    @State private var selected: Set<String> = []
    @State private var isWorking = false

    var body: some View {
        NavigationStack {
            Group {
                if isWorking {
                    ProgressView(loc["onboarding_seeding"])
                } else {
                    form
                }
            }
            .navigationTitle(loc["app_name"])
        }
        .task { await ensureCatalogueLoaded() }
    }

    private var form: some View {
        Form {
            Section {
                Text(loc["onboarding_welcome_body"])
                    .font(.callout)
                    .foregroundStyle(.secondary)
            } header: {
                Text(loc["onboarding_welcome_title"])
            }

            Section(loc["onboarding_language_title"]) {
                // Built from whichever .lproj folders are in the bundle, so a
                // new translation appears here without a code change.
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

            Section {
                ForEach(trades) { trade in
                    Button {
                        if selected.contains(trade.id) {
                            selected.remove(trade.id)
                        } else {
                            selected.insert(trade.id)
                        }
                    } label: {
                        HStack {
                            Text(loc.resolve(trade.names))
                            Spacer()
                            Image(systemName: selected.contains(trade.id) ? "checkmark.square.fill" : "square")
                                .foregroundStyle(selected.contains(trade.id) ? Brand.amber : .secondary)
                        }
                    }
                    .foregroundStyle(.primary)
                }
            } header: {
                Text(loc["onboarding_trade_title"])
            } footer: {
                Text(loc["onboarding_trade_body"])
            }

            Section {
                Button(loc["action_continue"]) {
                    Task { await finish() }
                }
                .disabled(selected.isEmpty)
            } footer: {
                if selected.isEmpty {
                    Text(loc["onboarding_select_one"]).foregroundStyle(.red)
                }
            }
        }
    }

    /// The trade list comes from the catalogue, so onboarding has to be able to
    /// load it itself rather than assume the launch task got there first.
    private func ensureCatalogueLoaded() async {
        guard trades.isEmpty else { return }
        let store = DataStore(context: context, settings: settings)
        if let version = try? store.seedReferenceData() {
            settings.seededCatalogVersion = version
        }
    }

    private func finish() async {
        guard !selected.isEmpty else { return }
        isWorking = true
        defer { isWorking = false }

        let store = DataStore(context: context, settings: settings)
        for trade in trades {
            trade.isSelected = selected.contains(trade.id)
        }
        try? store.stockTrades(selected)
        settings.onboardingComplete = true
    }
}
