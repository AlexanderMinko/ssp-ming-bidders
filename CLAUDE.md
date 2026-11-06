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

- **Synthetic demand.** HTTP endpoint(s) that accept the exchange's outbound OpenRTB bid request and answer
  with a bid or a no-bid, with **configurable behavior**: price range, no-bid rate, latency/jitter.
- **It replaces the exchange's built-in `mock:` bidders.** Today the exchange serves bidders whose
  `endpoint` starts with `mock:` in-process (`MockBidderClient`, config `{price, latencyMs, bidRate}`).
  Switching to this service = updating `bidders.endpoint` in the exchange's Mongo to this service's URL.
  **Zero exchange code changes** — keep this service compatible with that contract.
- **Purpose:** exercise the exchange's real HTTP fan-out path (timeouts, errors, latency) and give
  observability a genuine second hop to trace.

## The contract the exchange imposes (source: exchange spec `docs/superpowers/specs/2026-07-19-exchange-v1-design.md`)

| Aspect | What the exchange does / expects |
|---|---|
| Request | `POST` OpenRTB **2.6** JSON. `tmax` already reduced by the exchange (`effectiveTmax − 50`, or the bidder's lower `tmaxMs`). Fields outside the exchange's model are dropped. |
| Deadline | Responses after the auction deadline count as `TIMEOUT` and are discarded. Honour `tmax` unless deliberately simulating a slow bidder. |
| Bid | `200` + `BidResponse`. The exchange uses the **first imp** only; `bid.impid` must match a request imp. Price is first-price, CPM, `cur` = USD. |
| No-bid | `204`, empty body. Non-200, malformed bodies, and errors count as `ERROR`, not no-bid. |
| Seat | The winning `seat` comes from the exchange's `bidders` config, not from this response. |
| Macros | Put `${AUCTION_ID}`, `${AUCTION_BID_ID}`, `${AUCTION_IMP_ID}`, `${AUCTION_SEAT_ID}`, `${AUCTION_PRICE}` in `adm`/`nurl`. The exchange substitutes them. |
| Notices | The exchange does **not** fire `nurl`; the future supply SSP will. No `lurl`, no `burl`. |

If the exchange contract changes, update this table — the two repos drift silently otherwise.

## Stack (as scaffolded)

- Spring Boot **4.1.2**, Java **25**, `spring-boot-starter-webmvc` (blocking MVC — virtual threads like the
  exchange is the likely fit), `spring-boot-starter-restclient`, `spring-boot-starter-mongodb`, Lombok.
- Package `com.ming.bidders`.

## Open decisions (ask me — don't assume)

- **One service, many bidders?** e.g. one app hosting several logical bidders by path (`/bid/{bidderName}`)
  with per-bidder behavior, vs one deployment per bidder.
- **Where behavior config lives.** Mongo is on the classpath — own DB/collection, static config file, or
  request-driven? Confirm before wiring Mongo.
- **What RestClient is for** (e.g. simulating outbound calls) — confirm before using it.
- **Serialization:** reuse the exchange's dsl-json OpenRTB model, or plain Jackson? Payload model sharing
  between repos is not set up.
- **Image/ECR:** the existing ECR repo `eks-dev/app` has **IMMUTABLE** tags and holds the exchange
  (`0.0.<run>`). A second app needs either its own ECR repo (infra repo, `infra/07-ecr.tf`) or a tag prefix.

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
