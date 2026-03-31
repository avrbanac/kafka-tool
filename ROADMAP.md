# Roadmap

## Phase 1 — Core MVP ✅ (current)

- [x] Cluster profile management (add / edit / delete / persist)
- [x] Connect / disconnect per profile
- [x] Topic list with partition + replica counts
- [x] Create and delete topics
- [x] Topic config view (read-only detail panel)
- [x] Open producer / consumer directly from topic row in Topic Management tab
- [x] Consumer tab: configurable offset strategy, max message limit, streaming mode
- [x] Producer tab: key / value / headers input
- [x] Multiple tabs (multiple consumers open simultaneously)
- [x] Local JSON persistence for cluster profiles

## Phase 2 — Power Features

- [ ] Topic config edit (per-topic configuration parameters)
- [ ] Partition reassignment
- [ ] Replica management
- [ ] Consumer group list and describe
- [ ] Consumer group offset tracking (persist locally)
- [ ] SSL / SASL authentication per cluster profile (PLAIN, SCRAM-SHA-256/512)

## Phase 3 — Polish

- [ ] Schema Registry support (Avro / Protobuf deserialization)
- [ ] Load messages from file (producer bulk send)
- [ ] Export consumed messages to file (JSON / CSV)
- [ ] Message search and filter in consumer view
- [ ] Keyboard shortcuts
