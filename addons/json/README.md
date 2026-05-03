# OpenEconomy JSON Storage Addon

This is the standard, file-based storage implementation for OpenEconomy. It serves as a reference example for how to build custom storage providers for the core engine.

## How it Works

*   **File Structure**: Stores individual player accounts as JSON files in the `world/open-economy/` directory.
*   **Memory Model**: For performance, this provider loads **all** account data into memory during the server startup phase. 
*   **Atomicity**: Uses GSON for serialization, ensuring that account data is consistently formatted.

## Integration Example

This addon demonstrates how to register a provider using the OpenEconomy discovery system.

### 1. Implementation
The provider implements `EconomyStorageProvider` and returns a new instance of `JsonEconomyStorage` when requested.

### 2. Registration (`fabric.mod.json`)
To make the core engine aware of this provider, it is registered as an entrypoint:

```json
"entrypoints": {
  "open-economy:storage": [
    "savage.openeconomy.storage.json.JsonStorageProvider"
  ]
}
```

## Building Your Own
If you want to build a provider for a different database (e.g., MySQL, MongoDB, or Redis):
1.  Copy the structure of this project.
2.  Implement the `EconomyStorage` interface.
3.  Register your provider class in your `fabric.mod.json` under the `open-economy:storage` entrypoint.
