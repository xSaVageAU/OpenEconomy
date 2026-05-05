# OpenEconomy

**OpenEconomy** is a high-performance, modular economy framework for Minecraft Fabric. Designed for scalability and absolute data integrity, it separates the core economy logic from its implementation, allowing it to power everything from single-player worlds to massive multi-server clusters.

## Project Architecture

The project is split into several modules to ensure a clean separation of concerns:

*   **[Core Engine](./core/README.md)**: The implementation-agnostic "brain." Handles atomic transactions, account caching, and discovery registries.
*   **Mod Implementation**: The user-facing layer. Handles commands (`/bal`, `/pay`, `/eco`), configuration files, and player messaging.
*   **[Addons](./addons/)**: Modular extensions that provide the actual "muscles" for storage and networking.
    *   **[JSON Storage](./addons/json/README.md)**: Standard, file-based persistence for local servers.
    *   **NATS Addons**: High-performance distributed solutions.
        *   **[NATS Messaging](./addons/nats-messaging/README.md)**: Lightweight real-time cross-server synchronization.
        *   **[NATS Standalone](./addons/nats-standalone/README.md)**: Unified high-speed storage and zero-latency syncing.

## Core Philosophy

1.  **Atomicity First**: Every transaction uses a deadlock-safe, dual-locking strategy to ensure money is never lost or duplicated.
2.  **Implementation Agnostic**: The engine doesn't care if you use JSON, SQL, or NATS. It communicates through clean API interfaces.
3.  **Non-Blocking Performance**: All disk and network I/O is handled asynchronously to keep server tick times stable.
4.  **Distributed by Design**: Built-in support for real-time cache synchronization across multiple servers.

## Development

### Building
OpenEconomy is a multi-project Gradle build. To compile the entire suite:
```bash
./gradlew build
```

### Extending
You can extend OpenEconomy with your own storage addons. See the **[JSON Addon README](./addons/json/README.md)** for a reference on how to implement and register a new storage provider.

### Interoperability
OpenEconomy natively supports the **Common Economy API**. Any other mod that uses this standard (such as shops, auctions, etc) will automatically work with OpenEconomy.
