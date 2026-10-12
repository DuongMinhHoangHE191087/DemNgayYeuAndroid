import Foundation

/// Standard user/session context attached to every event, matching the Android SDK's wire
/// format exactly (`com.app.plugin.logevent.EventRequest.UserProperty`) — every field is
/// snake_case on the wire even though the enclosing envelope's own fields are camelCase; see
/// `SdkEvent`'s doc comment for why that mix is the real contract, not an inconsistency.
public struct UserProperty: Codable, Equatable, Sendable {
    public var userId: String
    public var appName: String
    public var appVersion: String
    public var language: String
    public var country: String
    public var deviceInfo: String
    public var eventDate: Int
    public var sessionNumber: Int
    public var sessionId: String
    public var installDay: Int
    public var retentionDay: Int
    public var localHour: Int
    public var localWeekday: String
    public var platform: String
    public var abTest: Int

    public init(
        userId: String = "",
        appName: String = "",
        appVersion: String = "",
        language: String = "",
        country: String = "",
        deviceInfo: String = "",
        eventDate: Int = 0,
        sessionNumber: Int = 0,
        sessionId: String = "",
        installDay: Int = 0,
        retentionDay: Int = 0,
        localHour: Int = 0,
        localWeekday: String = "",
        platform: String = "",
        abTest: Int = 0
    ) {
        self.userId = userId
        self.appName = appName
        self.appVersion = appVersion
        self.language = language
        self.country = country
        self.deviceInfo = deviceInfo
        self.eventDate = eventDate
        self.sessionNumber = sessionNumber
        self.sessionId = sessionId
        self.installDay = installDay
        self.retentionDay = retentionDay
        self.localHour = localHour
        self.localWeekday = localWeekday
        self.platform = platform
        self.abTest = abTest
    }

    private enum CodingKeys: String, CodingKey {
        case userId = "user_id"
        case appName = "app_name"
        case appVersion = "app_version"
        case language
        case country
        case deviceInfo = "device_info"
        case eventDate = "event_date"
        case sessionNumber = "session_number"
        case sessionId = "session_id"
        case installDay = "install_day"
        case retentionDay = "retention_day"
        case localHour = "local_hour"
        case localWeekday = "local_weekday"
        case platform
        case abTest = "ab_test"
    }
}
