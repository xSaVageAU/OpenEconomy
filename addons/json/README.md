# OpenEconomy JSON Storage Addon

This is the default, file-based storage implementation for OpenEconomy. It provides a robust reference example for building custom storage providers while being production-ready for small to medium-sized servers.

## Key Features

*   **File-per-Account Scalability**: Unlike single-file JSON databases, this provider stores each player account in an individual `.json` file within the `config/open-economy/accounts/` directory. This prevents file corruption from affecting the entire database and allows for better scalability.
*   **Atomic Saves**: To prevent data loss during crashes, the provider writes data to a temporary file (`.json.tmp`) first and then performs an atomic `move` operation to replace the existing file.
*   **Optimistic Locking**: The implementation respects the `revision` field in `AccountData`. It checks the existing file's revision before saving to prevent "lost updates" if multiple threads or processes attempt to modify the same account simultaneously.
*   **Async I/O**: All operations use `CompletableFuture` to ensure that disk I/O does not block the main Minecraft server thread.

## How it Works

While the provider itself is stateless and performs direct disk I/O, the OpenEconomy core engine utilizes this provider to:
1.  **Warm the Cache**: During startup, `loadAllAccounts()` is called to populate the in-memory `AccountCache`.
2.  **Persistent Storage**: Subsequent saves and loads are brokered through this provider to ensure persistence.

## Integration Example

This addon demonstrates how to register a provider using the OpenEconomy discovery system.

### 1. Implementation
The provider implements `StorageProvider` and returns a new instance of `JsonEconomyStorage` when requested.

### 2. Registration (`fabric.mod.json`)
To make the core engine aware of this provider, it is registered as an entrypoint:

```json
"entrypoints": {
  "openeconomy:storage": [
    "savage.openeconomy.json.JsonStorageProvider"
  ]
}
```

## Building Your Own
If you want to build a provider for a different database (e.g., MySQL, MongoDB, or Redis):
1.  Copy the structure of this project.
2.  Implement the `EconomyStorage` interface (and `StorageProvider` for the entrypoint).
3.  Register your provider class in your `fabric.mod.json` under the `openeconomy:storage` entrypoint.


## License
This addon is licensed under **CC0-1.0** (Public Domain). See the shared [LICENSE](../LICENSE) file for the full legal text. You are free to copy, modify, and distribute this code as a template for your own storage providers without any restrictions or attribution requirements.

*Note: The OpenEconomy core project remains under the MIT license.*
