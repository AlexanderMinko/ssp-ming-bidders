# ssp-ming-bidders

Dummy DSPs for the SSP/Exchange simulator. One service plays several bidders: the exchange calls
`POST /bid/{bidderKey}` with an OpenRTB 2.6 bid request, and each bidder answers like a DSP would.

| Response | When |
|---|---|
| `200` + `BidResponse` | at least one imp got a creative |
| `204` | bid-rate roll missed, or no creative matched (format, size, video limits, floor) |
| `404` | unknown `bidderKey` |
| `400` | malformed JSON |

Per request: sleep `latencyMs ± jitterMs` (never trimmed to `tmax`), roll `bidRate`, then pick a random
creative per imp among those of the right format and size, within the video limits, priced at or above
`bidfloor`. The bid price is the creative's `priceCpm` (USD, first price). `adm` and `nurl` keep the exchange
macros (`${AUCTION_PRICE}` …) for the exchange to substitute.

Design: `docs/superpowers/specs/2026-11-06-bidders-v1-design.md` (local, gitignored).

## Seed bidders

| Key | Latency | Bid rate | Notes |
|---|---|---|---|
| `alpha` | 30 ± 10 ms | 1.0 | 300x250 and 320x50 banners, 320x480 video |
| `beta` | 80 ± 30 ms | 0.6 | 300x250 banner, 320x480 video |
| `gamma` | 600 ± 50 ms | 1.0 | slow on purpose: exercises the exchange's TIMEOUT path |

All seed data is synthetic (`*.example` / `*.invalid` domains).

## Run locally

```bash
docker compose up -d
```

```bash
MONGO_URI=mongodb://localhost:27018/ssp-bidders ./mvnw spring-boot:run
```

```bash
curl -s -X POST localhost:8080/bid/alpha -H 'Content-Type: application/json' --data @src/test/resources/fixtures/bid-request-banner-300x250.json | jq
```

Tests need Docker (Testcontainers): `./mvnw test`.

## Data and migrations

Mongo database `ssp-bidders`, collections `bidders` and `creatives`, migrated by Liquibase
(`liquibase-mongodb`) at startup — `src/main/resources/db/changelog/`. The service reads them into memory and
refreshes every 30 s, so the request path never touches Mongo.

**Add or change a creative:** add a new changeset (YAML, `insertMany` / `runCommand`) to a changelog under
`db/changelog/`. Never edit a changeset that has already run. In seed `adm` HTML/VAST, use single-quoted
attributes so the inline JSON needs no escaping. `MIGRATION_ENABLED=false` skips migrations.

## Configuration

| Env | Default | Purpose |
|---|---|---|
| `MONGO_URI` | built from `MONGO_USER`/`MONGO_PASSWORD`/`MONGO_HOSTS`/`MONGO_DATABASE_NAME` | Mongo connection |
| `MIGRATION_ENABLED` | `true` | run Liquibase at startup |
| `BIDDERS_CATALOG_REFRESH_MS` | `30000` | catalog refresh period |
| `OTLP_METRICS_ENABLED`, `TRACING_ENABLED`, `OTLP_ENDPOINT` | off / `http://localhost:4318` | OTLP export (Phase 13) |

Metrics: `bidders.requests{bidder,outcome,reason}`, `bidders.bid.price{bidder}`, `bidders.latency.simulated{bidder}`.
