/// Diagnostic snapshot `AdsBrain.selfCheckReport()` returns. Mirrors Android's running habit of
/// surfacing misconfiguration where a developer will actually read it, instead of failing
/// silently in production.
public struct SelfCheckReport: Sendable, Equatable {
    public let isConfigured: Bool
    public let isEnabled: Bool
    public let consentTier: ConsentTier
    public let issues: [String]

    public init(isConfigured: Bool, isEnabled: Bool, consentTier: ConsentTier, issues: [String]) {
        self.isConfigured = isConfigured
        self.isEnabled = isEnabled
        self.consentTier = consentTier
        self.issues = issues
    }
}
