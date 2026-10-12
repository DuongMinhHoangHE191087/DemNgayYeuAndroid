import XCTest
@testable import MonetizationBrainCore

final class URLSessionEventSenderTests: XCTestCase {
    private func makeSession() -> URLSession {
        let config = URLSessionConfiguration.ephemeral
        config.protocolClasses = [StubURLProtocol.self]
        return URLSession(configuration: config)
    }

    override func tearDown() {
        StubURLProtocol.stub = nil
        super.tearDown()
    }

    func test_sendIncludesCustomHeaders() async throws {
        var capturedHeaders: [String: String]?
        StubURLProtocol.stub = { request in
            capturedHeaders = request.allHTTPHeaderFields
            let response = HTTPURLResponse(url: request.url!, statusCode: 200, httpVersion: nil, headerFields: nil)!
            return (response, Data())
        }
        let sender = URLSessionEventSender(
            endpoint: URL(string: "https://example.com/events")!,
            session: makeSession(),
            headers: ["x-api-key": "test-key"]
        )
        try await sender.send(SdkEvent(eventName: "debug_ping"))

        let headers = try XCTUnwrap(capturedHeaders)
        XCTAssertEqual(headers["x-api-key"], "test-key")
    }

    func test_sendUsesConfiguredTimeout() async throws {
        var capturedTimeout: TimeInterval?
        StubURLProtocol.stub = { request in
            capturedTimeout = request.timeoutInterval
            let response = HTTPURLResponse(url: request.url!, statusCode: 200, httpVersion: nil, headerFields: nil)!
            return (response, Data())
        }
        let sender = URLSessionEventSender(
            endpoint: URL(string: "https://example.com/events")!,
            session: makeSession(),
            timeoutInterval: 5
        )
        try await sender.send(SdkEvent(eventName: "debug_ping"))

        XCTAssertEqual(capturedTimeout, 5)
    }

    func test_sendSucceedsOn2xxResponse() async throws {
        StubURLProtocol.stub = { request in
            let response = HTTPURLResponse(url: request.url!, statusCode: 200, httpVersion: nil, headerFields: nil)!
            return (response, Data())
        }
        let sender = URLSessionEventSender(endpoint: URL(string: "https://example.com/events")!, session: makeSession())
        try await sender.send(SdkEvent(eventName: "debug_ping"))
    }

    func test_sendThrowsOnNon2xxResponse() async {
        StubURLProtocol.stub = { request in
            let response = HTTPURLResponse(url: request.url!, statusCode: 500, httpVersion: nil, headerFields: nil)!
            return (response, Data())
        }
        let sender = URLSessionEventSender(endpoint: URL(string: "https://example.com/events")!, session: makeSession())

        do {
            try await sender.send(SdkEvent(eventName: "debug_ping"))
            XCTFail("Expected send to throw")
        } catch EventSenderError.httpError(let statusCode) {
            XCTAssertEqual(statusCode, 500)
        } catch {
            XCTFail("Expected EventSenderError.httpError, got \(error)")
        }
    }

    func test_sendPostsEventBodyAsJson() async throws {
        var capturedBody: Data?
        StubURLProtocol.stub = { request in
            capturedBody = request.bodyDataForTesting
            let response = HTTPURLResponse(url: request.url!, statusCode: 200, httpVersion: nil, headerFields: nil)!
            return (response, Data())
        }
        let sender = URLSessionEventSender(endpoint: URL(string: "https://example.com/events")!, session: makeSession())
        try await sender.send(SdkEvent(eventName: "debug_ping", eventData: ["k": .string("v")], ts: 42))

        let body = try XCTUnwrap(capturedBody)
        let decoded = try JSONDecoder().decode(SdkEvent.self, from: body)
        XCTAssertEqual(decoded.eventName, "debug_ping")
        XCTAssertEqual(decoded.ts, 42)
    }
}

/// Minimal `URLProtocol` stub. The loading system moves a POST's `httpBody` into
/// `httpBodyStream` before handing the request to `startLoading`, so the body is read back out
/// of the stream via `bodyDataForTesting` rather than `request.httpBody` (which is `nil` by
/// then).
private extension URLRequest {
    var bodyDataForTesting: Data? {
        guard let stream = httpBodyStream else { return httpBody }
        stream.open()
        defer { stream.close() }
        var data = Data()
        let bufferSize = 4096
        var buffer = [UInt8](repeating: 0, count: bufferSize)
        while stream.hasBytesAvailable {
            let read = stream.read(&buffer, maxLength: bufferSize)
            if read <= 0 { break }
            data.append(buffer, count: read)
        }
        return data
    }
}

private final class StubURLProtocol: URLProtocol {
    static var stub: ((URLRequest) -> (HTTPURLResponse, Data))?

    override class func canInit(with request: URLRequest) -> Bool { true }
    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }

    override func startLoading() {
        guard let stub = StubURLProtocol.stub else {
            client?.urlProtocol(self, didFailWithError: URLError(.badServerResponse))
            return
        }
        let (response, data) = stub(request)
        client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
        client?.urlProtocol(self, didLoad: data)
        client?.urlProtocolDidFinishLoading(self)
    }

    override func stopLoading() {}
}
