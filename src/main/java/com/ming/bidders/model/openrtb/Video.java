package com.ming.bidders.model.openrtb;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Video player size plus the limits a creative must respect. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Video(Integer w, Integer h, List<String> mimes, Integer maxduration) { }
