# OpenEconomy NATS Addon

The **NATS Addon** provides high-performance, distributed economy support for large Minecraft server clusters. It allows multiple servers to share a single global database while keeping their local caches synchronized in real-time.

## Key Features

*   **Distributed Storage**: Uses NATS JetStream **Key-Value (KV)** as a high-speed, persistent global database.
*   **Real-Time Sync**: Uses NATS Core **Pub/Sub** to broadcast balance changes to every server in the cluster instantly.
*   **Self-Filtering**: Intelligent messaging logic prevents servers from processing their own broadcasted updates.
*   **Auto-Provisioning**: Automatically creates the required KV buckets if they don't already exist.

## How it Works

1.  **Storage**: When an account is loaded or saved, the engine communicates with the configured NATS KV bucket (default: `openeconomy-accounts`).
2.  **Messaging**: When a balance changes on Server A, a message is published to the subject `openeconomy.accounts.<uuid>`.
3.  **Synchronization**: Server B, Server C, etc., are subscribed to `openeconomy.accounts.>`. They receive the update and refresh their local memory cache immediately.

## Integration Example

This addon implements both the `EconomyStorageProvider` and `EconomyMessagingProvider` interfaces.

### Registration (`fabric.mod.json`)

```json
"entrypoints": {
  "open-economy:storage": [
    "savage.openeconomy.nats.storage.NatsStorageProvider"
  ],
  "open-economy:messaging": [
    "savage.openeconomy.nats.messaging.NatsMessagingProvider"
  ]
}
```

## Messaging Reference

This addon is the primary reference implementation for the `EconomyMessaging` interface.

### Wire Format
Updates are broadcasted as JSON-encoded packets. This format ensures that even if different servers have slightly different internal versions, they can still communicate using standard types.

```json
{
  "serverId": "UUID",
  "uuid": "Player-UUID",
  "name": "PlayerName",
  "balance": "100.50"
}
```

### Subject Wildcards
NATS uses a hierarchical subject system. This addon uses:
*   **Publishing**: `openeconomy.accounts.<uuid>`
*   **Subscribing**: `openeconomy.accounts.>`

Using the `>` (greater than) wildcard allows the server to maintain a single subscription that captures every account update without needing to subscribe to individual players.

### Loopback Protection (Self-Filtering)
To prevent infinite loops (where a server processes its own update, re-saves it, and re-broadcasts it), every message includes a `serverId`. 
*   On startup, each server generates a unique `SERVER_ID`.
*   If an incoming message's `serverId` matches the local one, it is silently discarded.

## Configuration

The configuration is located at `config/open-economy/nats.yml`.

| Setting | Default | Description |
|---------|---------|-------------|
| `natsUrl` | `nats://localhost:4222` | The URL of your NATS server. |
| `authToken` | `""` | Optional token for server authentication. |
| `kvBucket` | `openeconomy-accounts` | The JetStream KV bucket for player data. |
| `subject` | `openeconomy.accounts` | The base subject for broadcast messages. |
