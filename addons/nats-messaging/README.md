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

### Registration (`fabric.mod.json`)
The addon is discovered by the core engine via the `openeconomy:messaging` entrypoint. If you are examining this as a reference:

```json
"entrypoints": {
  "openeconomy:messaging": [
    "savage.openeconomy.nats.messaging.NatsMessagingProvider"
  ]
}
```

## Configuration

The configuration is located at `config/open-economy/nats.yml`.

| Setting | Default | Description |
|---------|---------|-------------|
| `natsUrl` | `nats://localhost:4222` | The URL of your NATS server. |
| `authToken` | `""` | Optional authentication token for the NATS server. |
| `subject` | `openeconomy.accounts` | The base subject for broadcast messages. |

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

## Building Your Own
If you want to build a messaging provider for a different service (e.g., Redis Pub/Sub, RabbitMQ, or Velocity/Bungee Plugin Messaging):
1.  **Project Structure**: Copy the structure of this project.
2.  **Implementation**: 
    *   Implement the `EconomyMessaging` interface for the core logic (publishing/subscribing).
    *   Implement the `MessagingProvider` interface for registration.
3.  **Registration**: Add your provider class to your `fabric.mod.json` under the `openeconomy:messaging` entrypoint.
4.  **Integration**: Ensure your implementation broadcasts JSON packets containing the current server ID to avoid infinite loops and uses the `AccountData` revision for consistency.


## License
This addon is licensed under **CC0-1.0** (Public Domain). See the shared [LICENSE](../LICENSE) file for the full legal text. You are free to copy, modify, and distribute this code as a template for your own messaging providers without any restrictions or attribution requirements.

*Note: The OpenEconomy core project remains under the MIT license.*
