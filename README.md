# kafka-tool

A desktop GUI application for Apache Kafka management. Built with Compose Multiplatform (JVM/Linux), Kotlin, and kafka-clients.

---

## Features

- **Cluster profiles** — manage multiple Kafka cluster connections; profiles are persisted locally and survive restarts
- **Topics** — list all topics with partition count and replication factor; create and delete topics; open a producer or consumer directly from a topic row
- **Topic config** — view per-topic configuration parameters in a scrollable, selectable detail panel; edit overrides via a dedicated dialog (add, change, or remove explicitly set config entries)
- **Topic metrics** — per-topic message count, message rate, average stored message size, throughput, and on-disk footprint (leader bytes and total bytes across replicas); samples every 15 seconds with a manual refresh; useful for Kafka capacity planning
- **Topic truncation** — delete all messages from a topic using the `deleteRecords` API, with a confirmation dialog
- **Topic structure** — increase partition count or change replication factor on an existing topic; replication changes use round-robin broker assignment
- **Consumer** — consume messages with configurable offset strategy (earliest, latest, or a specific offset), an optional message limit, or unlimited streaming mode; inspect individual messages in a detail panel; filter consumed messages by key, value, or headers with search-as-you-type
- **Producer** — send messages with an optional key, value, and arbitrary headers
- **Multi-tab** — open multiple consumer and producer tabs simultaneously, one per topic
- **SSH tunneling** — built-in SSH tunnel support with optional proxy jump; connects to remote Kafka clusters through an SSH host without external tools; broker addresses are discovered automatically and tunnels are created dynamically
- **Dark / light theme** — toggle in the sidebar footer; preference is persisted across restarts

---

## Requirements

| Dependency | Version |
|---|---|
| JDK | 21 (bundled in distributable packages) |
| Gradle | 8+ (only needed for building from source) |

The packaged `.deb` and AppImage distributions bundle a JRE — no separate JDK installation is needed to run them.

---

## Running from source

```bash
# Clone the repository
git clone <repo-url>
cd kafka-tool

# Run in development mode (requires JDK 21+ on PATH)
./gradlew run
```

---

## Building distributable packages

```bash
# Debian / Ubuntu package (.deb)
./gradlew packageDeb
# Output: build/compose/binaries/main/deb/kafka-tool_1.3.0_amd64.deb

# AppImage (portable, no installation required)
./gradlew packageAppImage
# Output: build/compose/binaries/main/app/kafka-tool/
```

### Installing the .deb package

```bash
sudo apt install ./build/compose/binaries/main/deb/kafka-tool_1.3.0_amd64.deb
```

The package installs everything under `/opt/kafka-tool/`. The binary is **not** placed on `PATH` automatically, so either run it directly:

```bash
/opt/kafka-tool/bin/kafka-tool
```

Or create a symlink once so you can just type `kafka-tool`:

```bash
sudo ln -s /opt/kafka-tool/bin/kafka-tool /usr/local/bin/kafka-tool
```

Note: the `.desktop` file bundled in the package is not placed in `/usr/share/applications`, so the app will not appear in your desktop application menu after install.

### Running the AppImage

```bash
chmod +x build/compose/binaries/main/app/kafka-tool/bin/kafka-tool
./build/compose/binaries/main/app/kafka-tool/bin/kafka-tool
```

---

## Connecting to a remote Kafka cluster

### Direct connection

Add a cluster profile via the **+** button in the sidebar. You need:

- **Bootstrap servers** — e.g. `broker1.internal:9092,broker2.internal:9092`
- **Profile name** — a label shown in the sidebar

### Connecting through an SSH tunnel

If your Kafka brokers live on a remote network (e.g. a staging or production environment behind a bastion host), enable **SSH Tunnel** in the cluster profile dialog. The application connects to the remote host via SSH, discovers all broker addresses automatically, and creates local port-forwarding tunnels for each broker — no external tools like sshuttle required.

**Profile configuration:**

| Field | Example | Description |
|---|---|---|
| Bootstrap Servers | `127.0.0.1:9092` | Kafka address as seen from the SSH host |
| SSH Host | `10.255.0.120` | Host to SSH into (must be reachable from your machine or via the jump host) |
| SSH Port | `22` | SSH port |
| SSH Username | `bulb` | SSH login user |
| Auth Type | Key File | Key file or password authentication |
| Private Key Path | *(blank)* | Leave blank to auto-detect from `~/.ssh/` |

**Proxy jump (optional):** If the SSH host is not directly reachable, enable **Proxy Jump** and configure the intermediate jump host. The application will first connect to the jump host, then hop to the target SSH host.

| Field | Example | Description |
|---|---|---|
| Jump Host | `10.255.0.38` | Intermediate bastion host |
| Jump Port | `22` | Jump host SSH port |
| Jump Username | `avrbanac` | Jump host login user |
| Jump Private Key Path | *(blank)* | Leave blank to auto-detect |

**How it works:**

1. The application establishes an SSH connection (optionally through the jump host)
2. A tunnel is created for the bootstrap server
3. A raw Kafka metadata request discovers all broker addresses in the cluster
4. Tunnels are created dynamically for every discovered broker
5. The Kafka client connects through the tunnels — all broker communication is encrypted via SSH

Each tunnel binds to a unique loopback address (`127.0.0.2`, `127.0.0.3`, ...) on the original broker port. If the cluster grows, new brokers are picked up automatically on the next connect.

---

## Logging

Logs are written to `~/.local/share/kafka-tool/logs/kafka-tool.log` with automatic rolling (daily + 10 MB size limit, 7-day retention, 50 MB total cap). Console output is also enabled when running from a terminal.

The default log level is **WARN**. Use verbosity flags to increase it:

```bash
kafka-tool        # WARN (default) — only problems
kafka-tool -v     # INFO — connections, operations, lifecycle events
kafka-tool -vv    # DEBUG — full operation details
```

---

## Data persistence

Profiles are saved to `~/.config/kafka-tool/profiles.json`. SSH passwords and key passphrases are stored in plain text in this file. To reset all profiles, delete this file.

Application settings (theme preference) are saved to `~/.config/kafka-tool/settings.json`.

---

## Project structure

```
src/main/kotlin/
├── main.kt                     # Entry point
├── model/                      # Data classes
├── persistence/                # JSON persistence (~/.config/kafka-tool/)
├── kafka/                      # kafka-clients wrappers
├── state/                      # ViewModels and app state (StateFlow-based)
└── ui/
    ├── App.kt                  # Root composable
    ├── sidebar/                # Cluster profile list + topic tree
    └── tabs/                   # Consumer, Producer, Topic Management tabs
```

---

## Roadmap

See [ROADMAP.md](ROADMAP.md).

## Changelog

See [CHANGELOG.md](CHANGELOG.md).
