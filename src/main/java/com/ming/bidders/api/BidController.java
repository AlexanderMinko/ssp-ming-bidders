package com.ming.bidders.api;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.ming.bidders.engine.BidEngine;
import com.ming.bidders.engine.BidOutcome;
import com.ming.bidders.model.openrtb.BidRequest;
import com.ming.bidders.model.openrtb.BidResponse;

import lombok.RequiredArgsConstructor;

/**
 * One endpoint, many DSPs: {@code POST /bid/{bidderKey}}. 200 + BidResponse, 204 for any no-bid, 404 for an
 * unknown key (the exchange counts it as ERROR, so a config typo is loud rather than a silent no-bid).
 */
@RestController
@RequiredArgsConstructor
public class BidController {

    private final BidEngine engine;

    @PostMapping(value = "/bid/{bidderKey}", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<BidResponse> bid(@PathVariable String bidderKey, @RequestBody BidRequest request) {
        return switch (engine.bid(bidderKey, request)) {
            case BidOutcome.Bid(var response) -> ResponseEntity.ok(response);
            case BidOutcome.NoBid _ -> ResponseEntity.noContent().build();
            case BidOutcome.UnknownBidder _ -> ResponseEntity.notFound().build();
        };
    }
}
