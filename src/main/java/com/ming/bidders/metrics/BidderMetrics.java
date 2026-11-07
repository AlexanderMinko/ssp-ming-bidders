package com.ming.bidders.metrics;

import java.time.Duration;

import org.springframework.stereotype.Component;

import com.ming.bidders.engine.BidOutcome;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;

/**
 * bidders.requests{bidder, outcome=BID|NO_BID|UNKNOWN, reason}, bidders.bid.price{bidder} and
 * bidders.latency.simulated{bidder}. Unknown keys share bidder=unknown to keep cardinality bounded.
 */
@Component
@RequiredArgsConstructor
public class BidderMetrics {

    public static final String UNKNOWN_BIDDER = "unknown";

    private final MeterRegistry registry;

    public void outcome(String bidder, BidOutcome outcome) {
        String name = outcome instanceof BidOutcome.UnknownBidder ? UNKNOWN_BIDDER : bidder;
        String result = switch (outcome) {
            case BidOutcome.Bid _ -> "BID";
            case BidOutcome.NoBid _ -> "NO_BID";
            case BidOutcome.UnknownBidder _ -> "UNKNOWN";
        };
        String reason = outcome instanceof BidOutcome.NoBid(var noBidReason) ? noBidReason.name() : "none";
        registry.counter("bidders.requests", "bidder", name, "outcome", result, "reason", reason).increment();
    }

    public void price(String bidder, double cpm) {
        DistributionSummary.builder("bidders.bid.price").tag("bidder", bidder).baseUnit("usd_cpm")
                .register(registry).record(cpm);
    }

    public void simulatedLatency(String bidder, long ms) {
        registry.timer("bidders.latency.simulated", "bidder", bidder).record(Duration.ofMillis(ms));
    }
}
