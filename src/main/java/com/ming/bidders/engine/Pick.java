package com.ming.bidders.engine;

import com.ming.bidders.model.entity.Creative;
import com.ming.bidders.model.openrtb.Imp;

/** The creative chosen for one imp. */
public record Pick(Imp imp, Creative creative) { }
