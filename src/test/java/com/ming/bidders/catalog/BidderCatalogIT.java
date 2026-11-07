package com.ming.bidders.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;

import com.ming.bidders.MongoContainerConfig;
import com.ming.bidders.model.entity.Creative;
import com.ming.bidders.model.entity.CreativeFormat;

@SpringBootTest
@Import(MongoContainerConfig.class)
class BidderCatalogIT {

    @Autowired BidderCatalog catalog;
    @Autowired MongoTemplate mongo;

    @Test
    void loadsSeededBidders() {
        assertThat(catalog.bidder("alpha")).isPresent();
        assertThat(catalog.bidder("nope")).isEmpty();
    }

    @Test
    void unknownBidderHasNoCreatives() {
        assertThat(catalog.creatives("bidder-nope")).isEmpty();
    }

    @Test
    void refreshPicksUpNewCreative() {
        int before = catalog.creatives("bidder-beta").size();
        mongo.insert(new Creative("cr-beta-320x50-test", "bidder-beta", "beta-320x50-test",
                CreativeFormat.BANNER, 320, 50, 0.70, List.of("test.example"), List.of("IAB1"), "<div/>", null));

        catalog.refresh();

        assertThat(catalog.creatives("bidder-beta")).hasSize(before + 1);
    }
}
