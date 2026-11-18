import Foundation

public protocol PartnerDossierStoring: Sendable {
    func loadDossier(scopeId: String) async -> PartnerDossier
    func saveDossier(_ dossier: PartnerDossier) async throws
}

public enum PartnerDossierCodec {
    public static func encode(_ dossier: PartnerDossier) throws -> String {
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.sortedKeys]
        let data = try encoder.encode(dossier)
        return String(decoding: data, as: UTF8.self)
    }

    public static func decode(_ json: String) throws -> PartnerDossier {
        let data = Data(json.utf8)
        return try JSONDecoder().decode(PartnerDossier.self, from: data)
    }
}

public actor FilePartnerDossierStore: PartnerDossierStoring {
    private let directoryURL: URL

    public init(directoryURL: URL? = nil, fileManager: FileManager = .default) {
        if let directoryURL {
            self.directoryURL = directoryURL
        } else {
            let appSupport = fileManager.urls(for: .applicationSupportDirectory, in: .userDomainMask).first
                ?? fileManager.temporaryDirectory
            self.directoryURL = appSupport.appendingPathComponent("PartnerDossier", isDirectory: true)
        }
    }

    public func loadDossier(scopeId: String) async -> PartnerDossier {
        let url = fileURL(for: scopeId)
        guard let data = try? Data(contentsOf: url),
              let decoded = try? JSONDecoder().decode(PartnerDossier.self, from: data) else {
            return PartnerDossier(scopeId: scopeId)
        }
        return decoded
    }

    public func saveDossier(_ dossier: PartnerDossier) async throws {
        try FileManager.default.createDirectory(at: directoryURL, withIntermediateDirectories: true)
        let data = try JSONEncoder().encode(dossier)
        try data.write(to: fileURL(for: dossier.scopeId), options: .atomic)
    }

    private func fileURL(for scopeId: String) -> URL {
        let safeScope = scopeId
            .replacingOccurrences(of: "/", with: "_")
            .replacingOccurrences(of: "..", with: "_")
        let name = safeScope.isEmpty ? "local" : safeScope
        return directoryURL.appendingPathComponent("dossier_\(name).json", isDirectory: false)
    }
}

public actor InMemoryPartnerDossierStore: PartnerDossierStoring {
    private var storage: [String: PartnerDossier] = [:]

    public init(initial: [PartnerDossier] = []) {
        for item in initial {
            storage[item.scopeId] = item
        }
    }

    public func loadDossier(scopeId: String) async -> PartnerDossier {
        storage[scopeId] ?? PartnerDossier(scopeId: scopeId)
    }

    public func saveDossier(_ dossier: PartnerDossier) async throws {
        storage[dossier.scopeId] = dossier
    }
}
