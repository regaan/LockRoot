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
        .package(url: "https://github.com/jedisct1/swift-sodium.git", revision: "cfd195c76882aa9b997560ca7cb95d72fbf5db00"),
        .package(url: "https://github.com/tmthecoder/Argon2Swift.git", revision: "53543623fefe68461b7eeea03d7f96677c2fd76d")
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
        ),
        .testTarget(
            name: "LockrootMacTests",
            dependencies: ["LockrootMac"]
        )
    ]
)
