package com.ming.bidders.model.openrtb;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/** One bid. adm and nurl keep the exchange macros unsubstituted; mtype 1 = banner, 2 = video. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Bid(String id, String impid, double price, String adm, String nurl, String crid, List<String> adomain, List<String> cat, Integer w, Integer h, int mtype) { }
