import SwiftUI

/// An app that exists only so that `StoreKit2BackendIntegrationTests` has an app identity.
///
/// StoreKit refuses an unentitled process. A SwiftPM test target runs inside Xcode's generic
/// `com.apple.dt.xctest.tool` agent, which has no bundle identifier of its own and no receipt,
/// and `SKTestSession` answers that with `.notEntitled` and an empty catalogue. A hosted test
/// target runs inside a real app instead, which is the whole reason this file exists.
///
/// It is deliberately empty: nothing here is under test, and anything added would be another
/// thing that can break the only job it has. It is never shipped and never submitted.
@main
struct HostApp: App {
    var body: some Scene {
        WindowGroup {
            Text("Test host — not a shipping app.")
                .padding()
        }
    }
}
