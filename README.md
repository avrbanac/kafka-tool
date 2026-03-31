# kafka-tool

A desktop GUI application for Apache Kafka management. Built with Compose Multiplatform (JVM/Linux), Kotlin, and kafka-clients.

---

## Features

- **Cluster profiles** — manage multiple Kafka cluster connections; profiles are persisted locally and survive restarts
- **Topics** — list all topics with partition count and replication factor; create and delete topics; open a producer or consumer directly from a topic row
- **Topic config** — view per-topic configuration parameters in a scrollable, selectable detail panel
- **Consumer** — consume messages with configurable offset strategy (earliest, latest, or a specific offset), an optional message limit, or unlimited streaming mode; inspect individual messages in a detail panel
- **Producer** — send messages with an optional key, value, and arbitrary headers
- **Multi-tab** — open multiple consumer and producer tabs simultaneously, one per topic
- **Hostname mapping** — per-cluster `/etc/hosts`-style hostname overrides; useful when broker advertised hostnames do not resolve from your machine

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
# Output: build/compose/binaries/main/deb/kafka-tool_0.2.0_amd64.deb

# AppImage (portable, no installation required)
./gradlew packageAppImage
# Output: build/compose/binaries/main/app/kafka-tool/
```

### Installing the .deb package

```bash
sudo dpkg -i build/compose/binaries/main/deb/kafka-tool_0.2.0_amd64.deb
# Launch from your application menu or:
kafka-tool
```

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

### Connecting through a VPN / SSH tunnel with sshuttle

If your Kafka brokers live on a remote network that is not directly routable (e.g. a staging or production environment behind a VPN or bastion host), you can use [sshuttle](https://github.com/sshuttle/sshuttle) to transparently tunnel traffic without a full VPN client.

**Install sshuttle:**

```bash
# Debian/Ubuntu
sudo apt install sshuttle

# pip
pip install sshuttle
```

**Open the tunnel:**

```bash
# Route all traffic for the remote subnet through the bastion
sshuttle -r user@bastion.example.com 10.0.0.0/8

# Or route only the specific broker subnet
sshuttle -r user@bastion.example.com 10.10.5.0/24
```

Leave the `sshuttle` process running in a terminal, then start kafka-tool. Connections to broker addresses in the tunnelled subnet will be transparently routed through the bastion.

### Hostname mapping

Kafka brokers often advertise internal hostnames (e.g. `kafka-broker-1.internal`) that do not resolve from your machine even when the network is reachable via a tunnel. Use the **Hostname mapping** field in the cluster profile dialog to add `/etc/hosts`-style entries without touching your system hosts file:

```
# ip  hostname [alias ...]
10.10.5.11  kafka-broker-1.internal  kafka-broker-1
10.10.5.12  kafka-broker-2.internal  kafka-broker-2
```

These mappings apply only to connections made by kafka-tool for this profile. Lines starting with `#` are treated as comments and ignored.

---

## Data persistence

Profiles are saved to `~/.config/kafka-tool/profiles.json`. No passwords or credentials are stored beyond what you enter in the profile form. To reset all profiles, delete this file.

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
