package com.ming.bidders.model.entity;

import java.util.List;

/** Video-only creative attributes, matched against imp.video.maxduration and imp.video.mimes. */
public record VideoSpec(int durationSec, List<String> mimes) { }
