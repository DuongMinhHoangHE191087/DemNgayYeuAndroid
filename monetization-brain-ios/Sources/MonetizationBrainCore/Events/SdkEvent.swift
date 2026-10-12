import Foundation

/// A single value in an `SdkEvent.eventData` payload. Mirrors the small set of JSON-safe types
/// event data actually needs — not a general-purpose JSON type.
public enum SdkEventValue: Codable, Equatable, Sendable {
    case string(String)
    case int(Int)
    case double(Double)
    case bool(Bool)

    public init(from decoder: Decoder) throws {
        let container = try decoder.singleValueContainer()
        if let value = try? container.decode(Bool.self) {
            self = .bool(value)
        } else if let value = try? container.decode(Int.self) {
            self = .int(value)
        } else if let value = try? container.decode(Double.self) {
            self = .double(value)
        } else if let value = try? container.decode(String.self) {
            self = .string(value)
        } else {
            throw DecodingError.dataCorruptedError(
                in: container,
                debugDescription: "SdkEventValue must be a string, int, double, or bool"
            )
        }
    }

    public func encode(to encoder: Encoder) throws {
        var container = encoder.singleValueContainer()
        switch self {
        case .string(let value): try container.encode(value)
        case .int(let value): try container.encode(value)
        case .double(let value): try container.encode(value)
        case .bool(let value): try container.encode(value)
        }
    }
}

/// Envelope for one event this SDK reports — shaped to match the Android SDK's wire format
/// EXACTLY (`com.app.plugin.logevent.EventRequest`/`UserProperty`, and the backend's
/// `app/models.py` `EventRequest`/`UserProperty`), not an independently-designed shape.
///
/// The backend's own source comments warn explicitly: inventing a "cleaner," internally
/// consistent shape (e.g. snake_case throughout, or renaming `ts`) makes the ingestion server
/// silently drop every event it receives — `POST /push` validates against the exact field names
/// below and rejects anything else. The camelCase-outer/snake_case-inner mix and the `ts`
/// (not `timestamp`) key are not inconsistencies to "fix"; they are the real, load-bearing
/// contract this type exists to reproduce byte-for-byte.
public struct SdkEvent: Codable, Equatable, Sendable {
    /// Client-assigned sequence number, stamped by `EventQueue.send(_:)` from
    /// `EventSequenceCounter` — a single install-scoped counter, persisted across relaunches,
    /// first event `1`. See `EventSequenceCounter` for the full contract and its Android
    /// provenance.
    ///
    /// The `0` default is NOT a value the wire path assigns; it is the "not yet numbered" state
    /// of an event that has been constructed but not handed to the queue. Do not set this by
    /// hand at a call site — the queue overwrites it, and it is the sole authority so that the
    /// numbering stays gapless and monotonic.
    public var sequenceId: Int

    /// Required by the backend (`EventRequest.eventName: str`, no default) — an event with no
    /// name fails validation and is rejected outright, so this has no default here either.
    public let eventName: String

    /// Arbitrary event-specific fields. Matches the backend's permissive `eventData: dict`.
    public var eventData: [String: SdkEventValue]

    /// Milliseconds since epoch, CLIENT-assigned. The wire key is `ts`, not `timestamp` — the
    /// backend partitions ingested rows by this client timestamp specifically so a durable
    /// queue replaying a record days later still lands in the right time bucket.
    public var ts: Int64

    /// Standard user/session context. Defaults to an all-empty `UserProperty` rather than
    /// making this optional, matching the backend's own default-filled model.
    public var userProperty: UserProperty

    public init(
        eventName: String,
        sequenceId: Int = 0,
        eventData: [String: SdkEventValue] = [:],
        ts: Int64 = Int64(Date().timeIntervalSince1970 * 1000),
        userProperty: UserProperty = UserProperty()
    ) {
        self.sequenceId = sequenceId
        self.eventName = eventName
        self.eventData = eventData
        self.ts = ts
        self.userProperty = userProperty
    }
}
