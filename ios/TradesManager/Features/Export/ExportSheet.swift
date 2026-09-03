import SwiftData
import SwiftUI

/// Offers the CSV and the PDF of one thing through the system share sheet.
struct ExportSheet: View {

    @Environment(Localization.self) private var loc
    @Environment(\.dismiss) private var dismiss

    private let document: ExportDocument

    @State private var files: [URL] = []

    init(items: [StockItem]) {
        document = .inventory(items)
    }

    init(project: Project, tasks: [ProjectTask], materials: [ProjectMaterial]) {
        document = .project(project, tasks: tasks, materials: materials)
    }

    init(template: SafetyTemplate, run: ChecklistRun, checks: [SafetyTemplateCheck], answers: [String: String]) {
        document = .checklist(template, run: run, checks: checks, answers: answers)
    }

    var body: some View {
        NavigationStack {
            List {
                Section {
                    ForEach(files, id: \.self) { url in
                        ShareLink(item: url) {
                            Label(url.lastPathComponent, systemImage: url.pathExtension == "pdf" ? "doc.richtext" : "tablecells")
                        }
                    }
                } header: {
                    Text(document.title(loc))
                } footer: {
                    Text(loc["set_reseed_note"])
                }
            }
            .navigationTitle(loc["set_export"])
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(loc["action_close"]) { dismiss() }
                }
            }
            .task {
                files = Exporter.write(document, loc: loc)
            }
        }
    }
}
