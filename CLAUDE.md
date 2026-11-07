# Project Context — Dummy Bidders / DSP (read this first)

Briefs you (the terminal AI) on what this service is, where it sits in the system, and how to work with me.
**Working mode: peer.** AdTech, OpenRTB, and Java are my strengths — no 101s, lead with the answer.

## The system this belongs to

A minimal **SSP/Exchange simulator** running on an EKS cluster I built by hand. Four sibling repos under
`…/AWS_EKS/` (the umbrella `CLAUDE.md` there has the cross-repo map):

| Folder | Role |
|---|---|
| `ssp-ming-exchange/` | The exchange: OpenRTB 2.6 in → fan-out to bidders → first-price auction → bid response |
| **`ssp-ming-bidders/`** (this repo) | **Dummy demand partners (DSP bidders)** that the exchange calls |
| `ssp-ming-exchange-helm/` | GitOps config repo: Helm charts + Argo CD `Application`s |
| `ssp-ming-exchange-terraform/` | EKS cluster, platform add-ons, Argo CD install, the learning plan |

A supply-partner SSP (traffic source) comes later.

## What this service is

- **Synthetic demand.** One service plays several DSPs: `POST /bid/{bidderKey}` takes the exchange's outbound
  OpenRTB 2.6 request and answers with a bid or a no-bid. Per bidder: latency ± jitter, bid rate. Per creative:
  format, size, CPM price (USD), video duration/mimes.
- **It replaces the exchange's built-in `mock:` bidders** — together with the exchange-side changes in the design
  (§9): drop `mock:`, add `BIDDERS_BASE_URL` from helm values, `bidders.endpoint` becomes a path (`/bid/alpha`).
  Endpoints never hold cluster DNS names.
- **Purpose:** exercise the exchange's real HTTP fan-out path (timeouts, errors, latency, floors, size mismatches)
  and give observability a genuine second hop to trace.
- Design: `docs/superpowers/specs/2026-11-06-bidders-v1-design.md`; plan: `docs/superpowers/plans/2026-11-06-bidders-v1.md`
  (both local — `docs/` is gitignored).

## The contract with the exchange (source: exchange spec `docs/superpowers/specs/2026-07-19-exchange-v1-design.md`)

| Aspect | Behavior |
|---|---|
| Request | `POST /bid/{bidderKey}`, OpenRTB **2.6** JSON. `tmax` already reduced by the exchange. Unknown fields ignored. |
| Deadline | The bidder **never trims its latency to `tmax`** — a slow bidder must really time out on the exchange side. |
| Bid | `200` + `BidResponse`, one `bid` per matched imp (the exchange auctions the **first imp** only). `price` = the creative's `priceCpm`, first price, `cur` = USD. |
| No-bid | `204`, empty body (bid-rate miss, no matching creative). Non-200, malformed bodies and errors count as `ERROR` on the exchange. |
| Unknown bidder | `404` — the exchange counts it as `ERROR`, so a config typo is loud. |
| Seat | Ours is the bidder key; the exchange takes the winning `seat` from its own `bidders` config. |
| Macros | `adm`/`nurl` carry `${AUCTION_ID}`, `${AUCTION_BID_ID}`, `${AUCTION_IMP_ID}`, `${AUCTION_SEAT_ID}`, `${AUCTION_PRICE}` unsubstituted. The exchange substitutes them. |
| Notices | The exchange does **not** fire `nurl`; the future supply SSP will. No `lurl`, no `burl`. |

If the exchange contract changes, update this table — the two repos drift silently otherwise.

## Stack

- Spring Boot **4.1.2**, Java **25**, Spring MVC on **virtual threads**, Jackson 3, Spring Data MongoDB,
  Liquibase 5.0.x + `liquibase-mongodb` (migrations at startup), `spring-boot-starter-opentelemetry` (Boot 4's
  tracing auto-config lives there — the raw Micrometer/OTel libs alone give no Tracer) + OTLP metrics, MapStruct,
  Lombok, Commons Lang 3, Commons Collections 4. Tests: JUnit 5, Testcontainers 2 (Mongo 7) — `./mvnw test` needs Docker.
- Package `com.ming.bidders`. **Code style (enforce on every change):**
  - **Apache Commons:** `StringUtils` / `ObjectUtils` (Lang 3) for strings and objects; **`CollectionUtils`
    (Collections 4)** for collections (`isEmpty`, `isNotEmpty`, `emptyIfNull`, `containsAny`). Not `x == null || x.isEmpty()`.
  - **MapStruct for object-to-object mapping** (`componentModel = "spring"`, `unmappedTargetPolicy = ERROR`);
    computed fields as explicit `@Mapping(expression = …)` or `default` methods. No hand-written field-by-field copies.
  - **Stream API over plain `for` loops.**
  - **No exceptions on the hot path** — outcomes are the sealed `BidOutcome`; defensive against odd-but-valid
    JSON (null imp entries, multi-format imps).
  - **No single-character identifiers** — variables, parameters, lambda params, loop counters included
    (`var r`, `e ->`, `for (int i …)` are out). Use a descriptive name: `response`, `bidder`, `index`.
  - **Always use curly braces** on `if` / `else` / `for` / `while` bodies, even one-liners.

## Decisions (06/11/2026)

- **One service, many bidders** by path (`/bid/{bidderKey}`), behavior per bidder in Mongo.
- **Mongo `ssp-bidders`** on the shared `mongodb` StatefulSet, collections `bidders` + `creatives`, read into an
  in-memory catalog refreshed every 30 s. **Liquibase** (YAML changelogs, `db/changelog/`) migrates and seeds at
  startup; applied changesets are never edited. Seed data is synthetic only.
- **Jackson**, not the exchange's dsl-json model (not the latency-critical hop). **No RestClient** — removed.
- **Image:** own ECR repo `eks-dev/bidders`; CI pushes to the helm repo with its **own** deploy key.
- **Mongo credentials:** reuse the root user via ESO from `eks-dev/exchange/mongodb` (simulator shortcut — prod
  would give each service its own user).
- GitHub: `AlexanderMinko/ssp-ming-bidders`.
- **No status / paused state (07/11/2026):** bidders and creatives carry no `status`. To switch a bidder or
  creative off, delete its Mongo document; the catalog refresh drops it within 30 s.

## Deployment target (fixed by the platform)

- EKS **`eks-dev`**, **`eu-central-1`**, namespace **`production`** (Terraform-owned — never `--create-namespace`).
- **In-cluster only.** The exchange calls it via Service DNS
  (`http://<service>.production.svc.cluster.local:<port>/...`). No `HTTPRoute`/ALB exposure needed unless I
  want to hit it from outside for testing.
- Deploy path mirrors the exchange: GitHub Actions (OIDC) → ECR → tag commit to the helm repo → Argo CD.
  The chart lives in `ssp-ming-exchange-helm/charts/`, not here.
- **Observability-ready:** emit OTLP (metrics + traces) like the exchange, and accept the incoming W3C
  `traceparent` so bidder spans join the exchange's auction trace. That cross-service trace is the payoff
  of Phase 13 (LGTM + OTel).

## How to work with me

1. **Assume domain expertise.** No OpenRTB/Spring/Java explainers. Lead with the answer.
2. **Be concise.** Tables/bullets over prose, no recap paragraphs.
3. **Push back.** If a bidder behaves unlike a real DSP in a way that hides an exchange bug, say so.
4. **Simulator-grade, not prod.** Simplest thing that exercises the exchange. Flag "prod would do X; here Y is fine."
5. **Code ownership:** generate boilerplate freely (controllers, config, Dockerfile, tests); for bidding
   behavior and contract decisions, propose and let me decide. **I run builds/tests/deploys myself.**
6. **Synthetic data only.** No real customer PII or revenue data (org rule).
7. **No secrets in git or chat.** Secrets live in AWS Secrets Manager → ESO.
8. AI-generated code needs my review before merge. Dates DD/MM/YYYY, metric units.
