package com.ming.bidders.model.entity;

import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * One creative in a bidder's library. The bid price is {@code priceCpm} (USD CPM, first price).
 * {@code adm} is HTML (banner) or VAST 3.0 XML (video) with the exchange macros left in.
 * Every creative in Mongo is live; remove the document to retire it.
 */
@Document("creatives")
public record Creative(
        @Id String id,
        String bidderId,
        String crid,
        CreativeFormat format,
        int w,
        int h,
        double priceCpm,
        List<String> adomain,
        List<String> cat,
        String adm,
        VideoSpec video) { }
