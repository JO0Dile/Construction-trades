import SwiftData
import SwiftUI

struct ProjectDetailView: View {

    @Environment(Localization.self) private var loc
    @Environment(AppSettings.self) private var settings
    @Environment(\.modelContext) private var context

    let project: Project

    @Query private var allTasks: [ProjectTask]
    @Query private var allMaterials: [ProjectMaterial]

    @State private var showExport = false

    private var tasks: [ProjectTask] {
        allTasks.filter { $0.projectId == project.id }.sorted { $0.sortOrder < $1.sortOrder }
    }

    private var materials: [ProjectMaterial] {
        allMaterials.filter { $0.projectId == project.id }.sorted { $0.sortOrder < $1.sortOrder }
    }

    /// An empty task list is 0 % rather than NaN.
    private var progress: Double {
        tasks.isEmpty ? 0 : Double(tasks.filter(\.isDone).count) / Double(tasks.count)
    }

    var body: some View {
        List {
            Section {
                LabeledContent(loc["proj_status"]) {
                    Text(loc[ProjectStatus(rawValue: project.status)?.labelKey ?? "proj_status_planned"])
                }
                if let city = project.city { LabeledContent(loc["proj_address"], value: city) }
                if let client = project.clientName { LabeledContent(loc["proj_client"], value: client) }
            }

            if !tasks.isEmpty {
                Section {
                    VStack(alignment: .leading, spacing: 6) {
                        Text("\(loc["proj_progress"])  \(Formats.percent(progress, locale: loc.locale))")
                            .font(.caption)
                        ProgressView(value: progress)
                    }
                    ForEach(tasks) { task in
                        Button {
                            DataStore(context: context, settings: settings)
                                .setTaskDone(task, !task.isDone)
                        } label: {
                            HStack {
                                Image(systemName: task.isDone ? "checkmark.circle.fill" : "circle")
                                    .foregroundStyle(task.isDone ? Brand.amber : .secondary)
                                Text(task.title)
                            }
                        }
                        .foregroundStyle(.primary)
                    }
                } header: {
                    Text(loc["proj_tasks"])
                }
            }

            if !materials.isEmpty {
                Section(loc["proj_materials"]) {
                    ForEach(materials) { material in
                        LabeledContent {
                            Text(Formats.quantity(material.requiredQuantity, locale: loc.locale))
                                .monospacedDigit()
                        } label: {
                            VStack(alignment: .leading) {
                                Text(material.label)
                                Text(loc[UnitLabel.key(for: material.unit)])
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                            }
                        }
                    }
                }
            }
        }
        .navigationTitle(project.name)
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                Button { showExport = true } label: {
                    Label(loc["set_export"], systemImage: "square.and.arrow.up")
                }
            }
        }
        .sheet(isPresented: $showExport) {
            ExportSheet(project: project, tasks: tasks, materials: materials)
        }
    }
}
