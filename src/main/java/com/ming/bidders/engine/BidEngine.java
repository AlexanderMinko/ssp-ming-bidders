package com.ming.bidders.engine;

import java.util.Objects;
import java.util.Optional;
import java.util.random.RandomGenerator;

import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Component;

import com.ming.bidders.catalog.BidderCatalog;
import com.ming.bidders.metrics.BidderMetrics;
import com.ming.bidders.model.entity.Bidder;
import com.ming.bidders.model.openrtb.BidRequest;

import lombok.RequiredArgsConstructor;

/**
 * One request through one logical DSP, top to bottom:
 * look up the bidder → simulate its latency → roll the bid rate → pick a creative per imp → build the response.
 * Every imp that matches gets a bid, as a real DSP does; the exchange only auctions the first.
 */
@Component
@RequiredArgsConstructor
public class BidEngine {

    private final BidderCatalog catalog;
    private final CreativeSelector selector;
    private final BidMapper bidMapper;
    private final BidderMetrics metrics;
    private final RandomGenerator random;
    private final Sleeper sleeper;

    public BidOutcome bid(String bidderKey, BidRequest request) {
        var outcome = catalog.bidder(bidderKey)
                .map(bidder -> bid(bidder, request))
                .orElseGet(BidOutcome.UnknownBidder::new);
        metrics.outcome(bidderKey, outcome);
        return outcome;
    }

    private BidOutcome bid(Bidder bidder, BidRequest request) {
        metrics.simulatedLatency(bidder.key(), simulateLatency(bidder));

        if (random.nextDouble() >= bidder.bidRate()) {
            return new BidOutcome.NoBid(NoBidReason.BID_RATE);
        }

        var creatives = catalog.creatives(bidder.id());
        var picks = CollectionUtils.emptyIfNull(request.imp()).stream()
                .filter(Objects::nonNull)   // "imp":[null] is valid JSON; skip it rather than NPE
                .map(imp -> selector.select(imp, creatives))
                .flatMap(Optional::stream)
                .toList();
        if (picks.isEmpty()) {
            return new BidOutcome.NoBid(NoBidReason.NO_MATCH);
        }

        picks.forEach(pick -> metrics.price(bidder.key(), pick.creative().priceCpm()));
        return new BidOutcome.Bid(bidMapper.toResponse(request, bidder, picks));
    }

    /**
     * Sleeps {@code latencyMs ± jitterMs} (uniform, clamped at 0). Deliberately ignores the request's tmax: a slow
     * bidder must really be slow so the exchange's TIMEOUT path gets exercised. Runs on a virtual thread, so a long
     * sleep costs no platform thread.
     *
     * @return the milliseconds actually slept
     */
    long simulateLatency(Bidder bidder) {
        int jitter = Math.max(0, bidder.jitterMs());
        int offset = jitter == 0 ? 0 : random.nextInt(2 * jitter + 1) - jitter;
        long milliseconds = Math.max(0, (long) bidder.latencyMs() + offset);
        sleeper.sleep(milliseconds);
        return milliseconds;
    }
}
