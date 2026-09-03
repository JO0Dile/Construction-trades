import CoreLocation
import SwiftData
import SwiftUI

struct ScheduleView: View {

    @Environment(Localization.self) private var loc
    @Environment(AppSettings.self) private var settings
    @Environment(\.modelContext) private var context

    @Query private var allBlocks: [TaskBlock]
    @Query private var entries: [TimeEntry]

    @State private var day = TimeOfDay.epochDay(from: .now)
    @State private var showAdd = false
    @State private var location = LocationStamp()

    private var blocks: [TaskBlock] {
        allBlocks.filter { $0.epochDay == day }.sorted { $0.startMinute < $1.startMinute }
    }

    private var openEntry: TimeEntry? {
        entries.filter { $0.checkOutAt == nil }.max { $0.checkInAt < $1.checkInAt }
    }

    private var store: DataStore { DataStore(context: context, settings: settings) }

    var body: some View {
        NavigationStack {
            List {
                Section {
                    Button {
                        Task { await checkInOrOut() }
                    } label: {
                        Label(
                            loc[openEntry == nil ? "sch_check_in" : "sch_check_out"],
                            systemImage: openEntry == nil ? "location.circle" : "checkmark.circle"
                        )
                    }
                    if !blocks.isEmpty {
                        Button(loc["sch_next_day"]) {
                            store.copyBlocks(blocks, to: day + 1)
                        }
                    }
                }

                if blocks.isEmpty {
                    ContentUnavailableView(loc["sch_empty"], systemImage: "calendar")
                } else {
                    ForEach(blocks) { block in
                        HStack {
                            Button {
                                store.setBlockDone(block, !block.isDone)
                            } label: {
                                Image(systemName: block.isDone ? "checkmark.circle.fill" : "circle")
                                    .foregroundStyle(block.isDone ? Brand.amber : .secondary)
                            }
                            .buttonStyle(.borderless)

                            VStack(alignment: .leading, spacing: 2) {
                                Text(block.title)
                                Text("\(TimeOfDay.format(block.startMinute)) – \(TimeOfDay.format(block.endMinute))")
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                            }
                        }
                        .swipeActions {
                            Button(loc["action_delete"], role: .destructive) {
                                store.delete(block)
                            }
                        }
                    }
                }
            }
            .navigationTitle(Formats.date(TimeOfDay.date(fromEpochDay: day), locale: loc.locale))
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button { day -= 1 } label: {
                        Label(loc["sch_previous_day"], systemImage: "chevron.backward")
                    }
                }
                ToolbarItemGroup(placement: .primaryAction) {
                    Button { day += 1 } label: {
                        Label(loc["sch_next_day"], systemImage: "chevron.forward")
                    }
                    Button { showAdd = true } label: {
                        Label(loc["sch_new_block"], systemImage: "plus")
                    }
                }
            }
            .sheet(isPresented: $showAdd) {
                AddBlockSheet { title, start, end in
                    store.addBlock(on: day, title: title, start: start, end: end)
                    showAdd = false
                }
            }
        }
    }

    /// The GPS stamp is asked for at the moment of a check-in, with a reason,
    /// and a refusal still records the check-in — just without the stamp.
    private func checkInOrOut() async {
        guard openEntry == nil else {
            store.toggleCheckIn(latitude: nil, longitude: nil)
            return
        }
        let fix = await location.requestOnce()
        store.toggleCheckIn(latitude: fix?.latitude, longitude: fix?.longitude)
    }
}

private struct AddBlockSheet: View {

    @Environment(Localization.self) private var loc
    @Environment(\.dismiss) private var dismiss

    let onConfirm: (String, Int, Int) -> Void

    @State private var title = ""
    @State private var start = "07:00"
    @State private var end = "12:00"

    var body: some View {
        NavigationStack {
            Form {
                TextField(loc["sch_task"], text: $title)
                TextField(loc["sch_start"], text: $start)
                TextField(loc["sch_end"], text: $end)
            }
            .navigationTitle(loc["sch_new_block"])
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(loc["action_cancel"]) { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button(loc["action_save"]) {
                        onConfirm(
                            title,
                            TimeOfDay.parse(start, fallback: 7 * 60),
                            TimeOfDay.parse(end, fallback: 12 * 60)
                        )
                    }
                    .disabled(title.trimmingCharacters(in: .whitespaces).isEmpty)
                }
            }
        }
    }
}

/// A single location fix, asked for only when a check-in needs one.
///
/// Everything here tolerates refusal: the continuation always resumes, so a
/// denied permission returns nil promptly instead of leaving the check-in
/// button spinning on a scaffold.
@Observable
final class LocationStamp: NSObject, CLLocationManagerDelegate {

    private let manager = CLLocationManager()
    private var continuation: CheckedContinuation<CLLocationCoordinate2D?, Never>?

    override init() {
        super.init()
        manager.delegate = self
        manager.desiredAccuracy = kCLLocationAccuracyHundredMeters
    }

    func requestOnce() async -> CLLocationCoordinate2D? {
        guard continuation == nil else { return nil }
        return await withCheckedContinuation { continuation in
            self.continuation = continuation
            switch manager.authorizationStatus {
            case .notDetermined:
                manager.requestWhenInUseAuthorization()
            case .authorizedAlways, .authorizedWhenInUse:
                manager.requestLocation()
            default:
                finish(nil)
            }
        }
    }

    func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        switch manager.authorizationStatus {
        case .authorizedAlways, .authorizedWhenInUse:
            manager.requestLocation()
        case .notDetermined:
            break
        default:
            finish(nil)
        }
    }

    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        finish(locations.last?.coordinate)
    }

    func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        finish(nil)
    }

    private func finish(_ coordinate: CLLocationCoordinate2D?) {
        continuation?.resume(returning: coordinate)
        continuation = nil
    }
}
