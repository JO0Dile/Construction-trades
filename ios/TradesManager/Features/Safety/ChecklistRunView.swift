import SwiftData
import SwiftUI

struct ChecklistRunView: View {

    @Environment(Localization.self) private var loc
    @Environment(AppSettings.self) private var settings
    @Environment(\.modelContext) private var context
    @Environment(\.dismiss) private var dismiss

    let template: SafetyTemplate

    @Query private var allChecks: [SafetyTemplateCheck]
    @Query private var allAnswers: [ChecklistAnswer]

    @State private var run: ChecklistRun?
    @State private var signer = ""
    @State private var showExport = false

    private var checks: [SafetyTemplateCheck] {
        allChecks.filter { $0.templateId == template.id }.sorted { $0.sortOrder < $1.sortOrder }
    }

    private func answer(for check: SafetyTemplateCheck) -> String? {
        guard let run else { return nil }
        return allAnswers.first { $0.runId == run.id && $0.checkId == check.id }?.state
    }

    private var blocked: Bool { run?.blocked ?? true }
    private var signed: Bool { run?.completedAt != nil }

    private var store: DataStore { DataStore(context: context, settings: settings) }

    var body: some View {
        List {
            if !template.references.isEmpty {
                Section(loc["saf_references"]) {
                    ForEach(template.references, id: \.self) { reference in
                        Text(reference).font(.caption)
                    }
                }
            }

            ForEach(checks) { check in
                VStack(alignment: .leading, spacing: 6) {
                    if check.critical {
                        Text(loc["saf_critical"]).font(.caption2).foregroundStyle(.red)
                    }
                    Text(loc.resolve(check.texts))

                    Picker("", selection: Binding(
                        get: { answer(for: check) ?? "" },
                        set: { newValue in
                            guard let run, let state = CheckState(rawValue: newValue) else { return }
                            store.answer(run: run, checkId: check.id, state: state)
                        }
                    )) {
                        Text(loc["saf_pass"]).tag(CheckState.pass.rawValue)
                        Text(loc["saf_fail"]).tag(CheckState.fail.rawValue)
                        Text(loc["saf_na"]).tag(CheckState.notApplicable.rawValue)
                    }
                    .pickerStyle(.segmented)
                    .disabled(signed)
                }
                .padding(.vertical, 4)
                .listRowBackground(
                    answer(for: check) == CheckState.fail.rawValue && check.critical
                        ? Color.red.opacity(0.12)
                        : Color.clear
                )
            }

            Section {
                if blocked && !signed {
                    Text(loc["saf_blocked"]).font(.callout).foregroundStyle(.red)
                }
                TextField(loc["saf_signed_by"], text: $signer)
                    .disabled(signed)
                Button(loc["saf_sign_off"]) {
                    if let run, store.signOff(run, signerName: signer) { dismiss() }
                }
                // Disabled rather than hidden: the worker can see the sign-off
                // exists and that a critical check is why it is not available.
                .disabled(blocked || signed || signer.trimmingCharacters(in: .whitespaces).isEmpty)
            }
        }
        .navigationTitle(loc.resolve(template.titles))
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                Button { showExport = true } label: {
                    Label(loc["set_export"], systemImage: "square.and.arrow.up")
                }
                .disabled(run == nil)
            }
        }
        .sheet(isPresented: $showExport) {
            if let run {
                ExportSheet(
                    template: template,
                    run: run,
                    checks: checks,
                    answers: Dictionary(
                        uniqueKeysWithValues: allAnswers
                            .filter { $0.runId == run.id }
                            .map { ($0.checkId, $0.state) }
                    )
                )
            }
        }
        .onAppear {
            if run == nil {
                run = store.startRun(templateId: template.id)
                signer = settings.actorName
            }
        }
    }
}
