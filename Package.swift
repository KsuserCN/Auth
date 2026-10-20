// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "KsuserAuthSDK",
    platforms: [.iOS("17.4"), .macOS(.v13)],
    products: [.library(name: "KsuserAuthSDK", targets: ["KsuserAuthSDK"])],
    targets: [
        .target(name: "KsuserAuthSDK", path: "sdk/ios/Sources/KsuserAuthSDK"),
        .testTarget(name: "KsuserAuthSDKTests", dependencies: ["KsuserAuthSDK"], path: "sdk/ios/Tests/KsuserAuthSDKTests")
    ]
)
