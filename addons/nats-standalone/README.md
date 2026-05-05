# OpenEconomy NATS Standalone Addon

The **NATS Standalone Addon** is the high-performance "gold standard" for distributed OpenEconomy clusters. It provides a unified solution for both **Storage** and **Messaging** using NATS JetStream **Key-Value (KV)** and **KV Watchers**.

Unlike the standard messaging addon, this standalone provider uses **zero extra network traffic** for synchronization. Instead of publishing separate "update" messages, it watches the KV bucket directly. When any server saves an account to NATS, all other servers see the update instantly via the KV event stream.

## Key Features

*   **Unified Provider**: Acts as both Storage and Messaging simultaneously.
*   **Zero-Traffic Sync**: Uses JetStream **KV Watchers** for synchronization. Saving data automatically triggers the sync event for all other servers.
*   **Centralized Source of Truth**: Data is persisted in NATS JetStream, making it accessible to any server in the cluster.
*   **Optimistic Locking**: Uses internal revisions to prevent race conditions during concurrent updates.
*   **Auto-Provisioning**: Automatically creates the JetStream KV bucket if it doesn't exist.

## How it Works

1.  **Save**: Server A saves an account to the NATS KV bucket.
2.  **Watch**: Every server in the network (including Server B, C, etc.) has a background `Watcher` on that bucket.
3.  **Sync**: The moment the data hits NATS, the Watcher on all other servers triggers, updating their local `AccountCache` with the new balance and revision.

## Integration

To use the standalone provider, set both storage and messaging types to `nats-standalone` in your main `config.json`:

```json
{
  "storageType": "nats-standalone",
  "messagingType": "nats-standalone"
}
```

## Configuration

The configuration is located at `config/open-economy/nats-standalone.yml`.

| Setting | Default | Description |
|---------|---------|-------------|
| `natsUrl` | `nats://localhost:4222` | The URL of your NATS server. |
| `authToken` | `""` | Optional authentication token for the NATS server. |
| `kvBucket` | `openeconomy_accounts` | The JetStream KV bucket for player data. |

*Note: `serverId` is generated automatically as a random UUID on every startup to prevent message loops.*

## Storage Format

Account data is stored as JSON-encoded packets in the KV bucket, using the player's UUID as the key:

```json
{
  "serverId": "550e8400-e29b-41d4-a716-446655440000",
  "name": "PlayerName",
  "balance": "100.50",
  "revision": 42
}
```
