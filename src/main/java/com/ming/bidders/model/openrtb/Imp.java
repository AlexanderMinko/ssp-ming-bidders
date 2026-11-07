package com.ming.bidders.model.openrtb;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** One impression. A null banner and video (e.g. native-only) matches no creative. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Imp(String id, Banner banner, Video video, Double bidfloor, String bidfloorcur) { }
