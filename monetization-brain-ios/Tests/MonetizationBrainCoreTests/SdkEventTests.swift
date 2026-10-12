import XCTest
@testable import MonetizationBrainCore

final class SdkEventTests: XCTestCase {
    func test_encodesAndDecodesRoundTrip() throws {
        let event = SdkEvent(
            eventName: "debug_ping",
            sequenceId: 3,
            eventData: [
                "count": .int(3),
                "ratio": .double(0.5),
                "label": .string("phase1"),
                "flag": .bool(true)
            ],
            ts: 1_725_000_000_000,
            userProperty: UserProperty(userId: "u1", platform: "ios")
        )

        let data = try JSONEncoder().encode(event)
        let decoded = try JSONDecoder().decode(SdkEvent.self, from: data)

        XCTAssertEqual(decoded, event)
    }

    func test_eventDataDefaultsToEmpty() {
        let event = SdkEvent(eventName: "debug_ping")
        XCTAssertTrue(event.eventData.isEmpty)
    }

    func test_sequenceIdDefaultsToZero() {
        let event = SdkEvent(eventName: "debug_ping")
        XCTAssertEqual(event.sequenceId, 0)
    }

    func test_userPropertyDefaultsToAllEmpty() {
        let event = SdkEvent(eventName: "debug_ping")
        XCTAssertEqual(event.userProperty, UserProperty())
    }

    /// Locks the exact wire shape the backend's `EventRequest`/`UserProperty` pydantic models
    /// require (`backend/app/models.py`) — an invented, "tidier" shape is silently rejected by
    /// `POST /push`, per that file's own warning. This test fails immediately if `SdkEvent` ever
    /// drifts back to independently-designed field names.
    func test_wireFormatMatchesBackendContract() throws {
        let event = SdkEvent(
            eventName: "debug_ping",
            sequenceId: 7,
            eventData: ["count": .int(3)],
            ts: 1_725_000_000_000,
            userProperty: UserProperty(userId: "u1", platform: "ios")
        )

        let data = try JSONEncoder().encode(event)
        let json = try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any])

        XCTAssertEqual(
            Set(json.keys), ["sequenceId", "eventName", "eventData", "ts", "userProperty"],
            "SdkEvent must send exactly these five top-level keys — no more, no fewer, so a " +
            "field added later can't silently ship to the backend without a test catching it"
        )
        XCTAssertEqual(json["sequenceId"] as? Int, 7)
        XCTAssertEqual(json["eventName"] as? String, "debug_ping")
        let eventData = try XCTUnwrap(json["eventData"] as? [String: Any])
        XCTAssertEqual(eventData["count"] as? Int, 3)
        XCTAssertNotNil(json["ts"], "timestamp must be sent under the wire key 'ts'")

        let userProperty = try XCTUnwrap(json["userProperty"] as? [String: Any])
        XCTAssertEqual(
            Set(userProperty.keys),
            ["user_id", "app_name", "app_version", "language", "country", "device_info",
             "event_date", "session_number", "session_id", "install_day", "retention_day",
             "local_hour", "local_weekday", "platform", "ab_test"],
            "UserProperty must send exactly these fifteen snake_case keys"
        )
        XCTAssertEqual(userProperty["user_id"] as? String, "u1")
        XCTAssertEqual(userProperty["platform"] as? String, "ios")
    }
}
