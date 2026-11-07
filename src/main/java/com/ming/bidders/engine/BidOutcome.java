package com.ming.bidders.engine;

import com.ming.bidders.model.openrtb.BidResponse;

/** Every way a bid request can end. Expected outcomes are values, never exceptions. */
public sealed interface BidOutcome {

    record Bid(BidResponse response) implements BidOutcome { }

    record NoBid(NoBidReason reason) implements BidOutcome { }

    record UnknownBidder() implements BidOutcome { }
}
