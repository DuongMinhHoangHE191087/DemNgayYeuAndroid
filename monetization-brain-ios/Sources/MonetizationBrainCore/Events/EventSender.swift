/// Sends one `SdkEvent` over the network. A protocol so `EventQueue` (Task 5) can be tested
/// against a fake instead of real network calls.
public protocol EventSender: Sendable {
    /// Attempts delivery. Throws on any failure (network, non-2xx status, etc.) — `EventQueue`
    /// treats a throw as "keep this event queued and retry later."
    func send(_ event: SdkEvent) async throws
}

public enum EventSenderError: Error, Sendable, Equatable {
    case invalidEndpoint
    case httpError(statusCode: Int)
    case transport(String)
}
