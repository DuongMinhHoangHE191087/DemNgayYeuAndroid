import SwiftUI
import WidgetKit

struct CoupleWidgetSnapshotRecord: Codable, Equatable {
    var version: Int
    var coupleTitle: String
    var partner1Name: String
    var partner2Name: String
    var loveDays: Int
    var startDateIso: String
    var nextOccasionTitle: String
    var nextOccasionDaysRemaining: Int?
    var redacted: Bool
    var updatedAtEpochMs: Int64

    static let preview = CoupleWidgetSnapshotRecord(
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

    func displaySnapshot() -> CoupleWidgetSnapshotRecord {
        guard redacted else { return self }
        var copy = self
        copy.coupleTitle = "InLove"
        copy.partner1Name = "Bạn"
        copy.partner2Name = "Người ấy"
        copy.nextOccasionTitle = "Dịp đặc biệt sắp tới"
        return copy
    }

    static func loadFromAppGroup() -> CoupleWidgetSnapshotRecord {
        let groupId = "group.com.aistudio.inlove.kmrv"
        guard let containerURL = FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: groupId) else {
            return .preview
        }
        let fileURL = containerURL.appendingPathComponent("widget_snapshot_v1.json", isDirectory: false)
        guard let data = try? Data(contentsOf: fileURL),
              let decoded = try? JSONDecoder().decode(CoupleWidgetSnapshotRecord.self, from: data) else {
            return .preview
        }
        return decoded
    }
}

struct InLoveWidgetEntry: TimelineEntry {
    let date: Date
    let snapshot: CoupleWidgetSnapshotRecord
}

struct InLoveWidgetProvider: TimelineProvider {
    func placeholder(in context: Context) -> InLoveWidgetEntry {
        InLoveWidgetEntry(date: Date(), snapshot: .preview)
    }

    func getSnapshot(in context: Context, completion: @escaping (InLoveWidgetEntry) -> Void) {
        let record = context.isPreview ? .preview : CoupleWidgetSnapshotRecord.loadFromAppGroup()
        completion(InLoveWidgetEntry(date: Date(), snapshot: record))
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<InLoveWidgetEntry>) -> Void) {
        let now = Date()
        let record = CoupleWidgetSnapshotRecord.loadFromAppGroup()
        let entry = InLoveWidgetEntry(date: now, snapshot: record)
        let nextRefresh = Calendar.current.date(byAdding: .hour, value: 1, to: now) ?? now.addingTimeInterval(3600)
        completion(Timeline(entries: [entry], policy: .after(nextRefresh)))
    }
}

struct InLoveWidgetEntryView: View {
    @Environment(\.widgetFamily) private var family
    let entry: InLoveWidgetEntry

    private var softBlush: Color { Color(red: 1.0, green: 0.961, blue: 0.973) }
    private var deepPlum: Color { Color(red: 0.122, green: 0.016, blue: 0.086) }
    private var subtlePlum: Color { Color(red: 0.29, green: 0.098, blue: 0.204) }
    private var rose: Color { Color(red: 0.914, green: 0.118, blue: 0.388) }

    var body: some View {
        let data = entry.snapshot.displaySnapshot()
        Group {
            switch family {
            case .systemSmall:
                smallHomeView(data)
            case .systemMedium:
                mediumHomeView(data)
            case .accessoryCircular:
                accessoryCircularView(data)
            case .accessoryRectangular:
                accessoryRectangularView(data)
            default:
                smallHomeView(data)
            }
        }
        .widgetURL(URL(string: "inlove://tab/home"))
        .containerBackground(for: .widget) {
            softBlush
        }
    }

    private func smallHomeView(_ data: CoupleWidgetSnapshotRecord) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack {
                Image(systemName: "heart.fill")
                    .foregroundStyle(rose)
                Text(data.coupleTitle.isEmpty ? "InLove" : data.coupleTitle)
                    .font(.caption.weight(.bold))
                    .foregroundStyle(rose)
                    .lineLimit(1)
            }
            Spacer()
            Text("\(data.loveDays)")
                .font(.system(size: 36, weight: .heavy, design: .rounded))
                .foregroundStyle(deepPlum)
                .minimumScaleFactor(0.7)
            Text("ngày yêu nhau")
                .font(.caption.weight(.semibold))
                .foregroundStyle(subtlePlum)
            Text("\(data.partner1Name) & \(data.partner2Name)")
                .font(.caption2.weight(.bold))
                .foregroundStyle(deepPlum)
                .lineLimit(1)
                .privacySensitive()
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
    }

    private func mediumHomeView(_ data: CoupleWidgetSnapshotRecord) -> some View {
        HStack(spacing: 14) {
            VStack(alignment: .leading, spacing: 4) {
                Label(data.coupleTitle.isEmpty ? "InLove" : data.coupleTitle, systemImage: "heart.fill")
                    .font(.caption.weight(.bold))
                    .foregroundStyle(rose)
                Text("\(data.loveDays)")
                    .font(.system(size: 40, weight: .heavy, design: .rounded))
                    .foregroundStyle(deepPlum)
                Text("\(data.partner1Name) & \(data.partner2Name)")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(subtlePlum)
                    .lineLimit(1)
                    .privacySensitive()
            }
            Spacer()
            VStack(alignment: .trailing, spacing: 6) {
                Text("Dịp sắp tới")
                    .font(.caption2.weight(.semibold))
                    .foregroundStyle(rose)
                Text(data.nextOccasionTitle)
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(deepPlum)
                    .multilineTextAlignment(.trailing)
                    .lineLimit(2)
                    .privacySensitive()
                if let days = data.nextOccasionDaysRemaining {
                    Text(days == 0 ? "Hôm nay" : "Còn \(days) ngày")
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(.white)
                        .padding(.horizontal, 10)
                        .padding(.vertical, 4)
                        .background(Capsule().fill(rose))
                }
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    private func accessoryCircularView(_ data: CoupleWidgetSnapshotRecord) -> some View {
        ZStack {
            AccessoryWidgetBackground()
            VStack(spacing: 1) {
                Image(systemName: "heart.fill")
                    .font(.caption2)
                Text("\(data.loveDays)")
                    .font(.headline.weight(.heavy))
                    .minimumScaleFactor(0.6)
            }
        }
    }

    private func accessoryRectangularView(_ data: CoupleWidgetSnapshotRecord) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Label("\(data.loveDays) ngày yêu", systemImage: "heart.fill")
                .font(.headline.weight(.bold))
            Text("\(data.partner1Name) & \(data.partner2Name)")
                .font(.caption)
                .lineLimit(1)
                .privacySensitive()
            if let days = data.nextOccasionDaysRemaining {
                Text("\(data.nextOccasionTitle) (\(days)n)")
                    .font(.caption2)
                    .lineLimit(1)
                    .privacySensitive()
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

@main
struct InLoveWidget: Widget {
    let kind: String = "InLoveWidget"

    var body: some WidgetConfiguration {
        StaticConfiguration(kind: kind, provider: InLoveWidgetProvider()) { entry in
            InLoveWidgetEntryView(entry: entry)
        }
        .configurationDisplayName("InLove — Đếm Ngày Yêu")
        .description("Theo dõi số ngày yêu và đếm ngược dịp kỷ niệm sắp tới.")
        .supportedFamilies([
            .systemSmall,
            .systemMedium,
            .accessoryCircular,
            .accessoryRectangular,
        ])
    }
}
