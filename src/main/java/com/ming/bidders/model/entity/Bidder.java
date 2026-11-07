package com.ming.bidders.model.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * One logical DSP. {@code key} is its URL path segment ({@code POST /bid/{key}}). Latency is
 * {@code latencyMs ± jitterMs} (uniform); {@code bidRate} is rolled once per request.
 * {@code nurlTemplate} may contain {@code {crid}} and the exchange macros. A bidder is switched off by
 * removing its document from Mongo — there is no status flag.
 */
@Document("bidders")
public record Bidder(
        @Id String id,
        String key,
        String name,
        int latencyMs,
        int jitterMs,
        double bidRate,
        String nurlTemplate) { }
