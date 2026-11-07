package com.ming.bidders.model.openrtb;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Outbound OpenRTB bid response. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BidResponse(String id, String bidid, String cur, List<SeatBid> seatbid) { }
