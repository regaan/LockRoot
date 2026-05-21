// swift-tools-version: 5.9

import PackageDescription

let package = Package(
    name: "LockrootMac",
    platforms: [
        .macOS(.v13)
    ],
    products: [
        .executable(name: "LockrootMac", targets: ["LockrootMac"])
    ],
    dependencies: [
        .package(url: "https://github.com/jedisct1/swift-sodium.git", branch: "master"),
        .package(url: "https://github.com/tmthecoder/Argon2Swift.git", branch: "main")
    ],
    targets: [
        .executableTarget(
            name: "LockrootMac",
            dependencies: [
                .product(name: "Clibsodium", package: "swift-sodium"),
                .product(name: "Argon2Swift", package: "Argon2Swift")
            ],
            resources: [
                .process("Resources")
            ]
        )
    ]
)
