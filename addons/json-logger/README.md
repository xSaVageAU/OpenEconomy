# OpenEconomy JSON Logger Addon

This is a reference implementation of a transaction auditing provider for OpenEconomy. It provides a simple, high-performance way to maintain a permanent audit trail of all economy transactions using the JSON Lines (JSONL) format.

## Key Features

*   **JSON Lines (JSONL) Format**: Transactions are appended to the log file as individual JSON objects per line. This is ideal for log analysis tools and ensures that the log file is always valid even if a write is interrupted.
*   **Non-Blocking I/O**: Leveraging the OpenEconomy core's virtual thread integration, all disk writes for logging happen in the background. This ensures that recording a transaction never delays the player's experience or the server's tick rate.
*   **Thread-Safe**: The logger uses internal synchronization to ensure that concurrent transactions from different threads are written sequentially and cleanly to the log file.
*   **Persistent Audit Trail**: Every transaction recorded includes a high-precision timestamp, the initiator (actor), the target account, the amount changed, and the final balance.

## How it Works

The JSON Logger is discovered by the core engine via Fabric entrypoints. When enabled in the configuration, the core will fire log events to this provider every time a transaction successfully persists to storage.

The log file is located at:
`logs/openeconomy/transactions.jsonl`

### Example Log Entry:
```json
{"timestamp":"2026-05-06T15:00:00.123Z","category":"pay","actor":"a1b2c3d4...","target":"e5f6g7h8...","amount":500.00,"balance_after":1250.50,"metadata":"Source: a1b2c3d4..."}
```

## Setup & Configuration

To use this logger, ensure the addon is installed and update your `config/open-economy/core.json` (or equivalent):

```json
{
  "loggingType": "json"
}
```

## Building Your Own Logger
This addon serves as the primary template for building custom auditing solutions (e.g., SQL logging, Discord Webhooks, or ELK integration):
1.  Implement the `TransactionLogger` interface for your logic.
2.  Implement `LoggerProvider` to return your logger and a unique ID.
3.  Register your provider in `fabric.mod.json` under the `openeconomy:logging` entrypoint:

```json
"entrypoints": {
  "openeconomy:logging": [
    "your.package.path.MyCustomLoggerProvider"
  ]
}
```

## License
This addon is licensed under **CC0-1.0** (Public Domain). You are free to copy, modify, and distribute this code as a template for your own auditing providers without any restrictions.

*Note: The OpenEconomy core project remains under the MIT license.*
