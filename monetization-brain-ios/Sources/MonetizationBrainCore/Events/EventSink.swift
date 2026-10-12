/// What future event producers (IAP, paywall, brain decisions) publish through. `EventQueue`
/// is the only implementation Phase 1 ships.
public protocol EventSink: Sendable {
    func send(_ event: SdkEvent) async
}
