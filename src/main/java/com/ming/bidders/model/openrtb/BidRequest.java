package com.ming.bidders.model.openrtb;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Inbound OpenRTB 2.6 request — only the fields the bidders read; everything else is ignored. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BidRequest(String id, List<Imp> imp, Integer tmax) { }
