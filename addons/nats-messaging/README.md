# OpenEconomy NATS Messaging Addon

The **NATS Messaging Addon** provides lightweight, high-performance cache synchronization for OpenEconomy using NATS Core **Pub/Sub**.

It is designed to keep local account caches across a server network synchronized in real-time. This is best used alongside a shared database (like MySQL) so that all servers stay in sync even after restarts.

## Key Features

*   **Real-Time Sync**: Uses NATS Core **Pub/Sub** to broadcast balance changes to every server in the cluster instantly.
*   **Decoupled Architecture**: Can be paired with any shared storage provider to add cross-server synchronization.
*   **Self-Filtering**: Intelligent messaging logic prevents servers from processing their own broadcasted updates.
*   **Optimistic Revisions**: Includes the internal account revision in messages to ensure cross-server consistency.

## How it Works

1.  **Event**: A balance changes on Server A.
2.  **Broadcast**: Server A publishes a JSON message to NATS on the subject `openeconomy.accounts.<uuid>`.
3.  **Synchronization**: All other servers (subscribed to `openeconomy.accounts.>`) receive the message, update their local `AccountCache`, and notify online players if necessary.

## Integration

To use this addon, register it in your main OpenEconomy `config.json`:

```json
{
  "messagingType": "nats"
}
```

## Configuration
| Setting | Default | Description |
|---------|---------|-------------|
| `natsUrl` | `nats://localhost:4222` | The URL of your NATS server. |
| `authToken` | `""` | Optional authentication token for the NATS server. |
| `subject` | `openeconomy.accounts` | The base subject for broadcast messages. |

The configuration is located at `config/open-economy/nats.yml`.

*Note: `serverId` is generated automatically as a random UUID on every startup to prevent message loops.*

## Wire Format

Updates are broadcasted as JSON-encoded packets:

```json
{
  "serverId": "550e8400-e29b-41d4-a716-446655440000",
  "uuid": "78b40000-e29b-41d4-a716-446655440000",
  "name": "PlayerName",
  "balance": "100.50",
  "revision": 42
}
```
