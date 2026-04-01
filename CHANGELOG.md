# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

## [Unreleased]

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
