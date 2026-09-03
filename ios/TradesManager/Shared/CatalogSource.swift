import Foundation

/// Reads the catalogues that ship inside the app bundle, preferring a newer
/// set downloaded into Application Support.
///
/// This is the same rule the Android `CatalogSource` follows, and for the same
/// reason: a phone that has never had a signal must still onboard with full
/// catalogues, while a correction between store releases must be able to reach
/// devices without an app update.
public struct CatalogSource {

    public enum LoadError: Error {
        case missingFile(String)
        case unsupportedSchema(found: Int, supported: Int)
    }

    /// The schema this build understands. A downloaded catalogue declaring a
    /// different one is ignored rather than half-read.
    public static let supportedSchemaVersion = 1

    private static let root = "catalog"

    private let bundle: Bundle
    private let downloadedRoot: URL?
    private let decoder = JSONDecoder()

    public init(bundle: Bundle = .main, downloadedRoot: URL? = CatalogSource.defaultDownloadRoot()) {
        self.bundle = bundle
        self.downloadedRoot = downloadedRoot
    }

    public static func defaultDownloadRoot() -> URL? {
        try? FileManager.default
            .url(for: .applicationSupportDirectory, in: .userDomainMask, appropriateFor: nil, create: true)
            .appendingPathComponent("catalog-update", isDirectory: true)
    }

    public func manifest() throws -> CatalogManifest {
        let bundled: CatalogManifest = try decode(relativePath: "manifest.json", allowDownloaded: false)
        guard bundled.schemaVersion == Self.supportedSchemaVersion else {
            throw LoadError.unsupportedSchema(found: bundled.schemaVersion, supported: Self.supportedSchemaVersion)
        }

        guard let downloaded: CatalogManifest = try? decode(relativePath: "manifest.json", allowDownloaded: true),
              downloaded.schemaVersion == Self.supportedSchemaVersion,
              downloaded.catalogVersion > bundled.catalogVersion
        else {
            return bundled
        }
        return downloaded
    }

    public func items(for trade: CatalogTrade) throws -> CatalogItemFile {
        try decode(relativePath: trade.itemsFile)
    }

    public func safety(for trade: CatalogTrade) throws -> SafetyFile? {
        guard let path = trade.safetyFile else { return nil }
        return try decode(relativePath: path)
    }

    public func templates(for trade: CatalogTrade) throws -> TemplateFile? {
        guard let path = trade.templatesFile else { return nil }
        return try decode(relativePath: path)
    }

    private func decode<T: Decodable>(relativePath: String, allowDownloaded: Bool = true) throws -> T {
        try decoder.decode(T.self, from: try data(relativePath: relativePath, allowDownloaded: allowDownloaded))
    }

    /// Downloaded copy wins per file, so a partial refresh is still coherent.
    private func data(relativePath: String, allowDownloaded: Bool) throws -> Data {
        if allowDownloaded,
           let downloadedRoot,
           case let candidate = downloadedRoot.appendingPathComponent("\(Self.root)/\(relativePath)"),
           FileManager.default.fileExists(atPath: candidate.path) {
            return try Data(contentsOf: candidate)
        }
        guard let url = bundle.url(forResource: "\(Self.root)/\(relativePath)", withExtension: nil) else {
            throw LoadError.missingFile(relativePath)
        }
        return try Data(contentsOf: url)
    }
}
