// swift-tools-version: 5.10
import PackageDescription

/// Swift 6's concurrency checking, on for every target.
///
/// This is not a style preference. `MonetizationBrainUMP` shipped a main-actor isolation defect
/// for several phases — `finish(onDone:)` called from a nonisolated UMP completion handler — and
/// the reason nothing caught it is that the only build that compiles that target at all is an iOS
/// build, which nobody was reading warnings from. Under this setting the whole class of defect is
/// a diagnostic, and `Scripts/verify.sh` fails on any diagnostic from this package's own sources.
///
/// Every target is clean under it today; keep it that way rather than removing it.
let strictConcurrency: [SwiftSetting] = [
    .enableExperimentalFeature("StrictConcurrency")
]

let package = Package(
    name: "MonetizationBrainIOS",
    platforms: [
        .iOS(.v15),
        .macOS(.v12)
    ],
    products: [
        .library(
            name: "MonetizationBrainCore",
            targets: ["MonetizationBrainCore"]
        ),
        .library(
            name: "MonetizationBrainUMP",
            targets: ["MonetizationBrainUMP"]
        ),
        .library(
            name: "MonetizationBrainStoreKit",
            targets: ["MonetizationBrainStoreKit"]
        ),
        .library(
            name: "MonetizationBrainFirebase",
            targets: ["MonetizationBrainFirebase"]
        )
    ],
    dependencies: [
        .package(
            url: "https://github.com/googleads/swift-package-manager-google-user-messaging-platform.git",
            from: "3.1.0"
        ),
        .package(
            url: "https://github.com/firebase/firebase-ios-sdk.git",
            "11.0.0"..<"13.0.0"
        )
    ],
    targets: [
        .target(
            name: "MonetizationBrainCore",
            dependencies: [],
            swiftSettings: strictConcurrency
        ),
        .testTarget(
            name: "MonetizationBrainCoreTests",
            dependencies: ["MonetizationBrainCore"],
            swiftSettings: strictConcurrency
        ),
        .target(
            name: "MonetizationBrainUMP",
            dependencies: [
                "MonetizationBrainCore",
                .product(name: "GoogleUserMessagingPlatform", package: "swift-package-manager-google-user-messaging-platform")
            ],
            swiftSettings: strictConcurrency
        ),
        .testTarget(
            name: "MonetizationBrainUMPTests",
            dependencies: ["MonetizationBrainUMP"],
            swiftSettings: strictConcurrency
        ),
        .target(
            name: "MonetizationBrainStoreKit",
            dependencies: ["MonetizationBrainCore"],
            swiftSettings: strictConcurrency
        ),
        .testTarget(
            name: "MonetizationBrainStoreKitTests",
            dependencies: ["MonetizationBrainStoreKit"],
            swiftSettings: strictConcurrency
        ),
        .target(
            name: "MonetizationBrainFirebase",
            dependencies: [
                "MonetizationBrainCore",
                .product(name: "FirebaseRemoteConfig", package: "firebase-ios-sdk")
            ],
            swiftSettings: strictConcurrency
        )
    ]
)
