package com.ming.bidders.model.openrtb;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Banner slot size. Null w/h (format[]-only requests) matches no creative. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Banner(Integer w, Integer h) { }
