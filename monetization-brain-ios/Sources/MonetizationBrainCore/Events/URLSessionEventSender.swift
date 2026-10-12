import Foundation

/// `EventSender` that POSTs the event as JSON to a configured endpoint.
///
/// Only a 2xx response counts as success, matching the backend contract's own rule: the
/// endpoint returns 2xx only once the event is durably written, so anything else — including a
/// timeout — must be treated as "not delivered, keep it queued."
public final class URLSessionEventSender: EventSender, @unchecked Sendable {
    private let endpoint: URL
    private let session: URLSession
    private let encoder: JSONEncoder
    private let headers: [String: String]
    private let timeoutInterval: TimeInterval

    /// - Parameters:
    ///   - headers: extra headers merged onto every request (e.g. `["x-api-key": "..."]` for a
    ///     backend with auth enabled). `Content-Type` is always set separately and cannot be
    ///     overridden this way.
    ///   - timeoutInterval: per-request timeout in seconds. Defaults to 30.
    public init(
        endpoint: URL,
        session: URLSession = .shared,
        headers: [String: String] = [:],
        timeoutInterval: TimeInterval = 30
    ) {
        self.endpoint = endpoint
        self.session = session
        self.headers = headers
        self.timeoutInterval = timeoutInterval
        self.encoder = JSONEncoder()
    }

    public func send(_ event: SdkEvent) async throws {
        var request = URLRequest(url: endpoint, timeoutInterval: timeoutInterval)
        request.httpMethod = "POST"
        for (key, value) in headers {
            request.setValue(value, forHTTPHeaderField: key)
        }
        // Set after the caller-supplied headers so it always wins, matching the doc comment's
        // claim below — the body really is JSON and nothing should be able to override that.
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try encoder.encode(event)

        let (_, response) = try await session.data(for: request)
        guard let httpResponse = response as? HTTPURLResponse else {
            throw EventSenderError.transport("Response was not an HTTP response")
        }
        guard (200..<300).contains(httpResponse.statusCode) else {
            throw EventSenderError.httpError(statusCode: httpResponse.statusCode)
        }
    }
}
