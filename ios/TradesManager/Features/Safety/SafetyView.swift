import SwiftData
import SwiftUI

struct SafetyView: View {

    @Environment(Localization.self) private var loc

    @Query private var templates: [SafetyTemplate]
    @Query private var trades: [Trade]

    private var visible: [SafetyTemplate] {
        let selected = Set(trades.filter(\.isSelected).map(\.id))
        return templates
            .filter { selected.contains($0.tradeId) }
            .sorted { $0.mandatoryBeforeWork && !$1.mandatoryBeforeWork }
    }

    var body: some View {
        NavigationStack {
            Group {
                if visible.isEmpty {
                    ContentUnavailableView(loc["saf_empty"], systemImage: "shield")
                } else {
                    List {
                        ForEach(visible) { template in
                            NavigationLink {
                                ChecklistRunView(template: template)
                            } label: {
                                VStack(alignment: .leading, spacing: 4) {
                                    if template.mandatoryBeforeWork {
                                        Text(loc["saf_mandatory"])
                                            .font(.caption2)
                                            .foregroundStyle(Brand.amber)
                                    }
                                    Text(loc.resolve(template.titles))
                                    Text(template.references.joined(separator: " · "))
                                        .font(.caption)
                                        .foregroundStyle(.secondary)
                                }
                            }
                        }

                        Section {
                            Text(loc["saf_disclaimer"])
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                    }
                }
            }
            .navigationTitle(loc["saf_title"])
        }
    }
}
