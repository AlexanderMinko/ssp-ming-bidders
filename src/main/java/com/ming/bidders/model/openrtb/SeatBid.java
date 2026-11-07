package com.ming.bidders.model.openrtb;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/** One seat (the bidder key) with its bids. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SeatBid(String seat, List<Bid> bid) { }
