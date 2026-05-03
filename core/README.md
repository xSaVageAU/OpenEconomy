# OpenEconomy Core Engine

The **OpenEconomy Core** is an implementation-agnostic economy framework for Minecraft Fabric. It provides the heavy-duty logic for storage orchestration, cross-server synchronization, and atomic transactions, while remaining entirely independent of specific file formats or command structures.

## 🚀 Key Features

*   **Implementation Agnostic**: Does not handle its own configuration files or commands.
*   **Atomic Transactions**: Provides a deadlock-safe `transfer()` method to ensure data integrity.
*   **Non-Blocking I/O**: All storage operations are wrapped in an asynchronous layer to prevent server stutters.
*   **Cross-Server Sync**: Built-in support for messaging providers (like NATS) to keep account caches synchronized across a cluster.
*   **Common Economy API**: Automatically registers itself with the `common-economy-api`.

## 🛠️ How to Use

To use the core engine in your mod implementation:

1.  **Implement the Configuration**:
    Create a class that implements `EconomyCoreConfig`. This provides the engine with its required settings (storage type, currency names, etc.).

2.  **Initialize the Engine**:
    In your mod's `onInitialize()`, call:
    ```java
    EconomyManager.setConfig(yourConfigInstance);
    ```
    This will boot the storage systems, discovery providers, and register the API.

3.  **Access the API**:
    Use `EconomyManager.getInstance()` to perform balance lookups and transactions.

## 📁 Architecture

*   **`savage.openeconomy.api`**: Interface definitions for Storage and Messaging.
*   **`savage.openeconomy.core`**: The main `EconomyManager` and configuration contracts.
*   **`savage.openeconomy.storage`**: Discovery registry and async wrapper for storage.
*   **`savage.openeconomy.messaging`**: Discovery registry and default No-Op provider.

## 🔌 Addon System

The core engine uses Fabric Entrypoints to discover providers at runtime. To add support for new databases or messaging systems:

1.  Implement `EconomyStorageProvider` or `EconomyMessagingProvider`.
2.  Register your provider in your addon's `fabric.mod.json`.
3.  The core will automatically detect and make them available via the configuration.
