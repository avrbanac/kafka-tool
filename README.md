# kafka-tool

A desktop GUI application for Apache Kafka management. Built with Compose Multiplatform (JVM/Linux), Kotlin, and kafka-clients.

## Features (Phase 1)

- **Cluster profiles** — manage multiple Kafka cluster connections, persisted locally
- **Topics** — list, create, delete topics with partition/replica metadata; open producer or consumer directly from the topic row
- **Consumer** — consume messages with configurable offset strategy (earliest, latest, specific offset) and optional message limit or unlimited streaming mode
- **Producer** — send messages with key, value, and optional headers
- **Topic config** — view per-topic configuration parameters in a detail panel
- **UI** — multi-tab layout, scrollable topic list with fixed cluster headers, tooltips on all buttons

## Requirements

- JDK 17+
- Gradle 8+

## Build & Run

```bash
# Run in development mode
./gradlew run

# Build distributable (Deb / AppImage)
./gradlew packageDeb
./gradlew packageAppImage
```

## Project Structure

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

## State Persistence

Profiles are saved to `~/.config/kafka-tool/profiles.json`. No credentials are stored beyond what you enter in the profile form.

## Roadmap

See [ROADMAP.md](ROADMAP.md).

## Changelog

See [CHANGELOG.md](CHANGELOG.md).
