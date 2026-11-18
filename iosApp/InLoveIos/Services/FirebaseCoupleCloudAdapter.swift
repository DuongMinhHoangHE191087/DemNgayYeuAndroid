import Foundation
import InLoveIosCore
#if canImport(FirebaseFirestore)
import FirebaseFirestore
#endif
#if canImport(Network)
import Network
#endif

/// Owner-only cloud dossier backup adapter & network reachability observer.
/// Syncs the private partner dossier JSON to `/users/{uid}/privateDossier/current`.
public final class FirebaseCoupleCloudAdapter: CloudDossierBackupSyncing, @unchecked Sendable {
    public init() {}

    public func savePrivateDossierBackup(_ dossier: PartnerDossier, uid: String) async throws {
        #if canImport(FirebaseFirestore)
        let json = try PartnerDossierCodec.encode(dossier)
        guard json.utf8.count <= 32_768 else { return }
        let ref = Firestore.firestore()
            .collection("users")
            .document(uid)
            .collection("privateDossier")
            .document("current")
        try await ref.setData(
            [
                "payloadJson": json,
                "schemaVersion": 1,
                "updatedAtEpochMs": dossier.updatedAtEpochMs,
                "updatedAt": FieldValue.serverTimestamp(),
            ],
            merge: true
        )
        #endif
    }

    public func loadPrivateDossierBackup(uid: String) async throws -> PartnerDossier? {
        #if canImport(FirebaseFirestore)
        let ref = Firestore.firestore()
            .collection("users")
            .document(uid)
            .collection("privateDossier")
            .document("current")
        let snapshot = try await ref.getDocument()
        guard let payload = snapshot.data()?["payloadJson"] as? String, !payload.isEmpty else {
            return nil
        }
        return try PartnerDossierCodec.decode(payload)
        #else
        return nil
        #endif
    }
}

@MainActor
public final class NetworkReachabilityObserver {
    #if canImport(Network)
    private let monitor = NWPathMonitor()
    private let queue = DispatchQueue(label: "com.aistudio.inlove.network-monitor")
    #endif
    private var started = false

    public init() {}

    public func start(onReachabilityChange: @escaping @MainActor (Bool) -> Void) {
        guard !started else { return }
        started = true
        #if canImport(Network)
        monitor.pathUpdateHandler = { path in
            let reachable = path.status == .satisfied
            Task { @MainActor in
                onReachabilityChange(reachable)
            }
        }
        monitor.start(queue: queue)
        #else
        onReachabilityChange(true)
        #endif
    }

    public func stop() {
        guard started else { return }
        started = false
        #if canImport(Network)
        monitor.cancel()
        #endif
    }
}
