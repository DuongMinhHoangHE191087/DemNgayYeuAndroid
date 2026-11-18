import Foundation

public struct CoupleWidgetSnapshot: Codable, Equatable, Sendable {
    public var version: Int
    public var coupleTitle: String
    public var partner1Name: String
    public var partner2Name: String
    public var loveDays: Int
    public var startDateIso: String
    public var nextOccasionTitle: String
    public var nextOccasionDaysRemaining: Int?
    public var redacted: Bool
    public var updatedAtEpochMs: Int64

    public init(
        version: Int = 1,
        coupleTitle: String = "",
        partner1Name: String = "",
        partner2Name: String = "",
        loveDays: Int = 0,
        startDateIso: String = "",
        nextOccasionTitle: String = "",
        nextOccasionDaysRemaining: Int? = nil,
        redacted: Bool = false,
        updatedAtEpochMs: LongLike = 0
    ) {
        self.version = version
        self.coupleTitle = coupleTitle
        self.partner1Name = partner1Name
        self.partner2Name = partner2Name
        self.loveDays = loveDays
        self.startDateIso = startDateIso
        self.nextOccasionTitle = nextOccasionTitle
        self.nextOccasionDaysRemaining = nextOccasionDaysRemaining
        self.redacted = redacted
        self.updatedAtEpochMs = updatedAtEpochMs
    }

    public typealias LongLike = Int64

    public func withPrivacyRedaction(isEnglish: Bool) -> CoupleWidgetSnapshot {
        guard redacted else { return self }
        var copy = self
        copy.coupleTitle = "InLove"
        copy.partner1Name = isEnglish ? "You" : "Bạn"
        copy.partner2Name = isEnglish ? "Partner" : "Người ấy"
        copy.nextOccasionTitle = isEnglish ? "Upcoming special day" : "Dịp đặc biệt sắp tới"
        return copy
    }

    public static func samplePreview() -> CoupleWidgetSnapshot {
        CoupleWidgetSnapshot(
            version: 1,
            coupleTitle: "InLove",
            partner1Name: "Hoàng",
            partner2Name: "Mai",
            loveDays: 365,
            startDateIso: "2025-11-18",
            nextOccasionTitle: "Lễ Tình Nhân Valentine",
            nextOccasionDaysRemaining: 14,
            redacted: false,
            updatedAtEpochMs: 0
        )
    }
}

public enum WidgetSnapshotStorage {
    public static let appGroupIdentifier = "group.com.aistudio.inlove.kmrv"
    public static let snapshotFileName = "widget_snapshot_v1.json"

    public static func defaultSnapshotURL(
        fileManager: FileManager = .default,
        groupId: String = appGroupIdentifier
    ) -> URL? {
        fileManager
            .containerURL(forSecurityApplicationGroupIdentifier: groupId)?
            .appendingPathComponent(snapshotFileName, isDirectory: false)
    }

    public static func encodeJSON(_ snapshot: CoupleWidgetSnapshot) throws -> String {
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.sortedKeys]
        let data = try encoder.encode(snapshot)
        return String(decoding: data, as: UTF8.self)
    }

    public static func decodeJSON(_ json: String) throws -> CoupleWidgetSnapshot {
        let data = Data(json.utf8)
        return try JSONDecoder().decode(CoupleWidgetSnapshot.self, from: data)
    }

    public static func save(_ snapshot: CoupleWidgetSnapshot, to url: URL) throws {
        let data = try JSONEncoder().encode(snapshot)
        let dir = url.deletingLastPathComponent()
        try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        try data.write(to: url, options: .atomic)
    }

    public static func load(from url: URL) -> CoupleWidgetSnapshot? {
        guard let data = try? Data(contentsOf: url) else { return nil }
        return try? JSONDecoder().decode(CoupleWidgetSnapshot.self, from: data)
    }
}
