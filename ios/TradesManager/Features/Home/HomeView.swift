import SwiftData
import SwiftUI

struct HomeView: View {

    @Environment(Localization.self) private var loc
    @Environment(AppSettings.self) private var settings

    @Query private var stock: [StockItem]
    @Query private var projects: [Project]
    @Query private var blocks: [TaskBlock]

    @State private var showSettings = false

    private var today: Int { TimeOfDay.epochDay(from: .now) }

    private var todayBlocks: [TaskBlock] {
        blocks.filter { $0.epochDay == today }.sorted { $0.startMinute < $1.startMinute }
    }

    private var lowStock: [StockItem] {
        stock.filter { $0.deletedAt == nil && $0.isLowStock }
    }

    private var activeProjects: [Project] {
        projects.filter { $0.deletedAt == nil && $0.status == ProjectStatus.active.rawValue }
    }

    var body: some View {
        NavigationStack {
            List {
                Section(loc["home_today"]) {
                    if todayBlocks.isEmpty {
                        Text(loc["home_no_tasks_today"]).foregroundStyle(.secondary)
                    } else {
                        ForEach(todayBlocks) { block in
                            VStack(alignment: .leading, spacing: 2) {
                                Text(block.title)
                                Text("\(TimeOfDay.format(block.startMinute)) – \(TimeOfDay.format(block.endMinute))")
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                            }
                        }
                    }
                }

                Section {
                    LabeledContent(loc["home_low_stock"]) {
                        Text(loc.plural("low_stock_count", lowStock.count))
                            .foregroundStyle(lowStock.isEmpty ? .secondary : .red)
                    }
                    LabeledContent(loc["home_active_projects"]) {
                        Text("\(activeProjects.count)")
                    }
                }

                if !activeProjects.isEmpty {
                    Section(loc["home_active_projects"]) {
                        ForEach(activeProjects) { project in
                            VStack(alignment: .leading, spacing: 2) {
                                Text(project.name)
                                Text(project.kindLabel).font(.caption).foregroundStyle(.secondary)
                            }
                        }
                    }
                }
            }
            .navigationTitle(loc["app_name"])
            .toolbar {
                ToolbarItem(placement: .primaryAction) {
                    Button {
                        showSettings = true
                    } label: {
                        Label(loc["set_title"], systemImage: "gearshape")
                    }
                }
            }
            .sheet(isPresented: $showSettings) { SettingsView() }
        }
    }
}
