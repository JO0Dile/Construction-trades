import SwiftData
import SwiftUI

struct ProjectsView: View {

    @Environment(Localization.self) private var loc
    @Environment(AppSettings.self) private var settings
    @Environment(\.modelContext) private var context

    @Query private var allProjects: [Project]

    @State private var showTemplates = false

    private var projects: [Project] {
        allProjects
            .filter { $0.deletedAt == nil }
            .sorted { ($0.dueDate ?? .distantFuture) < ($1.dueDate ?? .distantFuture) }
    }

    var body: some View {
        NavigationStack {
            Group {
                if projects.isEmpty {
                    ContentUnavailableView(
                        loc["proj_empty"],
                        systemImage: "hammer",
                        description: Text(loc["proj_from_template"])
                    )
                } else {
                    List(projects) { project in
                        NavigationLink {
                            ProjectDetailView(project: project)
                        } label: {
                            VStack(alignment: .leading, spacing: 2) {
                                Text(project.name)
                                HStack {
                                    Text(project.kindLabel)
                                    Spacer()
                                    Text(loc[ProjectStatus(rawValue: project.status)?.labelKey ?? "proj_status_planned"])
                                }
                                .font(.caption)
                                .foregroundStyle(.secondary)
                            }
                        }
                    }
                }
            }
            .navigationTitle(loc["proj_title"])
            .toolbar {
                ToolbarItem(placement: .primaryAction) {
                    Button { showTemplates = true } label: {
                        Label(loc["proj_new"], systemImage: "plus")
                    }
                }
            }
            .sheet(isPresented: $showTemplates) { TemplatePicker() }
        }
    }
}

private struct TemplatePicker: View {

    @Environment(Localization.self) private var loc
    @Environment(AppSettings.self) private var settings
    @Environment(\.modelContext) private var context
    @Environment(\.dismiss) private var dismiss

    @State private var templates: [(tradeId: String, template: ProjectTemplate)] = []

    var body: some View {
        NavigationStack {
            List(templates, id: \.template.id) { entry in
                Button {
                    create(entry.template, tradeId: entry.tradeId)
                } label: {
                    VStack(alignment: .leading, spacing: 4) {
                        Text(loc.resolve(entry.template.names)).font(.headline)
                        Text(loc.resolve(entry.template.descriptions))
                            .font(.caption)
                            .foregroundStyle(.secondary)
                        Text(loc.plural("days_left", entry.template.estimatedDays))
                            .font(.caption2)
                            .foregroundStyle(.tertiary)
                    }
                }
                .foregroundStyle(.primary)
            }
            .navigationTitle(loc["proj_from_template"])
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(loc["action_cancel"]) { dismiss() }
                }
            }
            .onAppear {
                templates = DataStore(context: context, settings: settings).templates()
            }
        }
    }

    private func create(_ template: ProjectTemplate, tradeId: String) {
        let store = DataStore(context: context, settings: settings)
        _ = try? store.createProject(
            from: template,
            tradeId: tradeId,
            name: "",
            languageCode: loc.languageCode
        )
        dismiss()
    }
}
