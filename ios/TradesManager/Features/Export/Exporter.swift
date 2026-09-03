import Foundation
import PDFKit
import SwiftUI
import UIKit

/// What can be exported, and how it renders.
///
/// One type rather than three exporters, so a CSV and a PDF of the same thing
/// can never drift apart in what they contain — an audit is only worth
/// anything if the spreadsheet and the printout agree.
enum ExportDocument {
    case inventory([StockItem])
    case project(Project, tasks: [ProjectTask], materials: [ProjectMaterial])
    case checklist(SafetyTemplate, run: ChecklistRun, checks: [SafetyTemplateCheck], answers: [String: String])

    func title(_ loc: Localization) -> String {
        switch self {
        case .inventory: return loc["inv_title"]
        case .project(let project, _, _): return project.name
        case .checklist(let template, _, _, _): return loc.resolve(template.titles)
        }
    }

    func fileStem(_ loc: Localization) -> String {
        let stamp = ISO8601DateFormatter.filenameFormatter.string(from: .now)
        let base: String
        switch self {
        case .inventory: base = "inventory"
        case .project(let project, _, _): base = project.name
        case .checklist(let template, _, _, _): base = template.id
        }
        // Keep filenames ASCII-safe: a Hebrew filename survives the share sheet
        // but not every mail client and Windows share it may land in.
        let safe = base.unicodeScalars
            .map { CharacterSet.alphanumerics.contains($0) ? Character($0) : "-" }
            .reduce(into: "") { $0.append($1) }
        return "\(safe.isEmpty ? "export" : safe)-\(stamp)"
    }

    /// Column headings and rows, shared by both renderers.
    func table(_ loc: Localization) -> (headers: [String], rows: [[String]]) {
        switch self {
        case .inventory(let items):
            return (
                [loc["inv_name"], loc["inv_spec"], loc["inv_quantity"], loc["inv_unit"], loc["inv_min_stock"], loc["inv_barcode"]],
                items.map { item in
                    [
                        loc.resolve(item.names),
                        loc.resolve(item.spec),
                        Formats.quantity(item.quantity, locale: loc.locale),
                        loc[UnitLabel.key(for: item.unit)],
                        Formats.quantity(item.minStock, locale: loc.locale),
                        item.barcode ?? "",
                    ]
                }
            )

        case .project(_, let tasks, let materials):
            var rows: [[String]] = tasks.map { task in
                [loc["proj_tasks"], task.title, task.isDone ? loc["saf_pass"] : loc["saf_fail"], ""]
            }
            rows += materials.map { material in
                [
                    loc["proj_materials"],
                    material.label,
                    Formats.quantity(material.requiredQuantity, locale: loc.locale),
                    loc[UnitLabel.key(for: material.unit)],
                ]
            }
            return ([loc["action_filter"], loc["inv_name"], loc["proj_required_qty"], loc["inv_unit"]], rows)

        case .checklist(_, let run, let checks, let answers):
            var rows = checks.map { check in
                [
                    loc.resolve(check.texts),
                    check.critical ? loc["saf_critical"] : "",
                    Self.answerLabel(answers[check.id], loc),
                ]
            }
            rows.append([loc["saf_signed_by"], "", run.signedByName ?? ""])
            return ([loc["saf_title"], loc["saf_critical"], loc["saf_pass"]], rows)
        }
    }

    private static func answerLabel(_ state: String?, _ loc: Localization) -> String {
        switch state {
        case CheckState.pass.rawValue: return loc["saf_pass"]
        case CheckState.fail.rawValue: return loc["saf_fail"]
        case CheckState.notApplicable.rawValue: return loc["saf_na"]
        default: return "—"
        }
    }
}

enum Exporter {

    /// RFC 4180 quoting, and a UTF-8 BOM so Excel on Windows opens Hebrew and
    /// Arabic columns as text rather than as mojibake — the single most common
    /// complaint about exported site data.
    static func csv(_ document: ExportDocument, loc: Localization) -> Data {
        let table = document.table(loc)
        var text = "\u{FEFF}"
        text += table.headers.map(escape).joined(separator: ",") + "\r\n"
        for row in table.rows {
            text += row.map(escape).joined(separator: ",") + "\r\n"
        }
        return Data(text.utf8)
    }

    private static func escape(_ field: String) -> String {
        guard field.contains(where: { $0 == "," || $0 == "\"" || $0 == "\n" || $0 == "\r" }) else {
            return field
        }
        return "\"\(field.replacingOccurrences(of: "\"", with: "\"\""))\""
    }

    /// A4 portrait, mirrored for Hebrew and Arabic. The layout is deliberately
    /// plain: this ends up in a site file or a tender folder, printed in black
    /// and white, and read by someone who did not use the app.
    static func pdf(_ document: ExportDocument, loc: Localization) -> Data {
        let pageSize = CGSize(width: 595, height: 842)
        let margin: CGFloat = 40
        let rightToLeft = loc.layoutDirection == .rightToLeft
        let renderer = UIGraphicsPDFRenderer(bounds: CGRect(origin: .zero, size: pageSize))

        let table = document.table(loc)
        let columns = table.headers.count
        let usableWidth = pageSize.width - margin * 2
        let columnWidth = usableWidth / CGFloat(max(columns, 1))

        let paragraph = NSMutableParagraphStyle()
        paragraph.alignment = rightToLeft ? .right : .left
        paragraph.baseWritingDirection = rightToLeft ? .rightToLeft : .leftToRight
        paragraph.lineBreakMode = .byTruncatingTail

        let titleAttributes: [NSAttributedString.Key: Any] = [
            .font: UIFont.boldSystemFont(ofSize: 18),
            .paragraphStyle: paragraph,
        ]
        let headerAttributes: [NSAttributedString.Key: Any] = [
            .font: UIFont.boldSystemFont(ofSize: 10),
            .paragraphStyle: paragraph,
        ]
        let cellAttributes: [NSAttributedString.Key: Any] = [
            .font: UIFont.systemFont(ofSize: 10),
            .paragraphStyle: paragraph,
        ]

        return renderer.pdfData { context in
            var y: CGFloat = margin
            context.beginPage()

            func columnRect(_ index: Int, y: CGFloat, height: CGFloat) -> CGRect {
                // Mirroring the column order is what makes an RTL export read
                // correctly, rather than a left-to-right table with RTL text.
                let position = rightToLeft ? (columns - 1 - index) : index
                return CGRect(
                    x: margin + CGFloat(position) * columnWidth,
                    y: y,
                    width: columnWidth - 4,
                    height: height
                )
            }

            document.title(loc).draw(
                in: CGRect(x: margin, y: y, width: usableWidth, height: 24),
                withAttributes: titleAttributes
            )
            y += 28

            Formats.date(.now, locale: loc.locale).draw(
                in: CGRect(x: margin, y: y, width: usableWidth, height: 16),
                withAttributes: cellAttributes
            )
            y += 24

            for (index, header) in table.headers.enumerated() {
                header.draw(in: columnRect(index, y: y, height: 14), withAttributes: headerAttributes)
            }
            y += 18

            for row in table.rows {
                if y > pageSize.height - margin - 20 {
                    context.beginPage()
                    y = margin
                }
                for (index, cell) in row.enumerated() where index < columns {
                    cell.draw(in: columnRect(index, y: y, height: 14), withAttributes: cellAttributes)
                }
                y += 16
            }
        }
    }

    /// Writes both files into the caches directory for the share sheet to hand
    /// on. Caches, not Documents: an export is a copy, and the system may
    /// reclaim it once the user has sent it.
    static func write(_ document: ExportDocument, loc: Localization) -> [URL] {
        let directory = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("exports", isDirectory: true)
        try? FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)

        let stem = document.fileStem(loc)
        let csvURL = directory.appendingPathComponent("\(stem).csv")
        let pdfURL = directory.appendingPathComponent("\(stem).pdf")

        var urls: [URL] = []
        if (try? csv(document, loc: loc).write(to: csvURL, options: .atomic)) != nil { urls.append(csvURL) }
        if (try? pdf(document, loc: loc).write(to: pdfURL, options: .atomic)) != nil { urls.append(pdfURL) }
        return urls
    }
}

extension ISO8601DateFormatter {
    static let filenameFormatter: ISO8601DateFormatter = {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withYear, .withMonth, .withDay]
        return formatter
    }()
}
