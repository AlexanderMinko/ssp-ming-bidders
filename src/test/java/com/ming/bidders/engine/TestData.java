package com.ming.bidders.engine;

import java.util.List;

import com.ming.bidders.model.entity.Bidder;
import com.ming.bidders.model.entity.Creative;
import com.ming.bidders.model.entity.CreativeFormat;
import com.ming.bidders.model.entity.VideoSpec;
import com.ming.bidders.model.openrtb.Banner;
import com.ming.bidders.model.openrtb.BidRequest;
import com.ming.bidders.model.openrtb.Imp;

/** Shared builders for the engine unit tests. */
final class TestData {

    static final String NURL = "https://alpha.bidders.invalid/win?crid={crid}&price=${AUCTION_PRICE}";

    private TestData() { }

    static Bidder bidder(int latencyMs, int jitterMs, double bidRate) {
        return new Bidder("bidder-alpha", "alpha", "Alpha", latencyMs, jitterMs, bidRate, NURL);
    }

    static Creative bannerCreative(String crid, double price) {
        return new Creative(crid, "bidder-alpha", crid, CreativeFormat.BANNER, 300, 250, price,
                List.of("adv.example"), List.of("IAB1"), "<div>${AUCTION_PRICE}</div>", null);
    }

    static Creative videoCreative(String crid, double price) {
        return new Creative(crid, "bidder-alpha", crid, CreativeFormat.VIDEO, 320, 480, price,
                List.of("adv.example"), List.of("IAB1"), "<VAST/>", new VideoSpec(15, List.of("video/mp4")));
    }

    static Imp bannerImp(String id) {
        return new Imp(id, new Banner(300, 250), null, null, "USD");
    }

    static BidRequest request(Imp... imps) {
        return new BidRequest("req-1", List.of(imps), 300);
    }
}
