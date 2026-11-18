// swift-tools-version: 5.10
import PackageDescription

let package = Package(
    name: "InLoveIosCore",
    platforms: [
        .iOS(.v17),
        .macOS(.v14),
    ],
    products: [
        .library(
            name: "InLoveIosCore",
            targets: ["InLoveIosCore"]
        ),
    ],
    targets: [
        .target(
            name: "InLoveIosCore",
            swiftSettings: [
                .enableExperimentalFeature("StrictConcurrency"),
            ]
        ),
        .testTarget(
            name: "InLoveIosCoreTests",
            dependencies: ["InLoveIosCore"]
        ),
    ]
)
