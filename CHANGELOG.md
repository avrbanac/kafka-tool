# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

## [Unreleased]

## [1.2.0] - 2026-04-17

### Changed
- Consumer message details: replaced the right-side detail panel with an inline expandable row. Clicking the chevron at the start of a row expands it in place, showing headers (key-value pairs) above a scrollable prettified value box (capped at 300dp). Only one row can be expanded at a time.
- Row content in both collapsed and expanded form is now text-selectable; toggling expansion is handled exclusively by the leading chevron so selection is not disturbed.

## [1.1.0] - 2026-04-16

### Added
- Built-in SSH tunneling: connect to remote Kafka clusters through an SSH host without external tools like sshuttle; tunnels are created dynamically for all discovered brokers
- Proxy jump support: optional two-hop SSH connection through an intermediate jump host to reach the target SSH host
- Broker auto-discovery: raw Kafka metadata fetch discovers all cluster brokers and creates tunnels automatically on connect
- Fast-fail DNS resolver: unmapped hostnames fail instantly during SSH tunneling instead of blocking on slow DNS timeouts

### Changed
- Removed manual hostname mapping field from the profile dialog; hostname resolution for tunneled brokers is now fully automatic
- Profile dialog SSH section: all connection details (host, port, username, key path, proxy jump) are configured explicitly in the application — no hidden `~/.ssh/config` parsing

## [1.0.0] - 2026-04-15

### Added
- File-based logging with Logback: logs written to `~/.local/share/kafka-tool/logs/kafka-tool.log` with daily + size-based rolling (10 MB per file, 7-day retention, 50 MB cap); console output preserved for terminal use
- Logging instrumented throughout the entire application: persistence, Kafka wrappers, view models, hostname resolver, and app lifecycle
- Verbosity flags: `-v` for INFO level, `-vv` for DEBUG level; default is WARN

### Changed
- Replaced `slf4j-simple` with `logback-classic` for structured, configurable logging
- Persistence layer errors are now logged instead of silently swallowed

## [0.6.0] - 2026-04-14

### Added
- Consumer message filter: search-as-you-type text field above the message list filters consumed messages by key, value, or headers (both keys and values); case-insensitive matching; status bar shows filtered count (e.g. "5 of 42 messages"); clear button to reset the filter

## [0.5.0] - 2026-04-09

### Added
- Partition count increase: "Edit partitions / replication" button in the topic detail panel opens a dialog to increase the partition count via `createPartitions`; input is validated to be ≥ the current count
- Replication factor change: same dialog allows changing the replication factor up or down; replicas are assigned round-robin across available brokers via `alterPartitionReassignments`; error shown if broker count is insufficient

## [0.4.0] - 2026-04-09

### Added
- Topic config edit: "Edit Config" button in the topic detail panel opens a dialog showing only explicitly overridden config entries (`DYNAMIC_TOPIC_CONFIG`); supports editing values, deleting overrides, and adding new key-value overrides; changes applied via `incrementalAlterConfigs`
- Topic truncation: "Delete All Messages" button in the topic detail panel deletes all records using the `deleteRecords` API; confirmation dialog warns about impact on consumers with uncommitted offsets

### Fixed
- Application now appears under the Development category in the KDE application menu (`menuGroup = "Development"` set in build config)

## [0.3.0] - 2026-04-01

### Added
- Dark / light theme toggle: icon button pinned at the bottom of the sidebar switches between Material 3 dark and light color schemes
- Theme preference persisted to `~/.config/kafka-tool/settings.json` and restored on startup with no flash
- Scrollbar thumb color now adapts to the active theme (visible in both light and dark modes)

## [0.2.0] - 2026-03-31

### Added
- Per-cluster hostname mapping: optional textarea in the cluster profile dialog accepts `/etc/hosts`-style entries (`ip hostname1 hostname2 ...`), supports multiple names per IP and `#` comments
- Hostname mapping is applied before each Kafka client is created (consumer, producer, admin) and falls back to system DNS for any hostname not in the map
- Mapping is persisted as part of the cluster profile in `profiles.json`

### Fixed
- Topic detail panel and message detail panel in the right sidebar are now scrollable and text is selectable

## [0.1.1] - 2026-03-24

### Added
- Tooltips on all sidebar buttons: Add cluster, Connect/Disconnect, More options, Refresh topics, Manage topics
- Tooltip showing full topic name on hover in sidebar topic list
- Producer and Consumer shortcut buttons per topic row in Topic Management tab
- Application window icon

### Fixed
- Sidebar topic list now scrolls independently — cluster headers stay fixed while topics scroll
- Vertical scrollbar added to sidebar topic list
- Topic list not refreshing after create/delete: added 500ms delay to allow broker metadata propagation
- Improved connection error messages to surface the actual exception cause

## [0.1.0] - 2026-03-24

### Added
- Cluster profile management: add, edit, delete, persist to `~/.config/kafka-tool/profiles.json`
- Connect / disconnect per cluster profile
- Topic list with partition count and replication factor
- Create topic dialog (name, partitions, replication factor)
- Delete topic with confirmation
- Consumer tab: offset strategy selection (earliest, latest, specific offset), configurable message limit, unlimited streaming mode, message detail panel
- Producer tab: key (optional), value, headers (add/remove key-value pairs)
- Multi-tab UI: open multiple consumer and producer tabs simultaneously
- Topic Management tab per connected cluster
