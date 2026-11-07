package com.ming.bidders.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.random.RandomGenerator;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.web.servlet.MockMvc;

import com.ming.bidders.MongoContainerConfig;
import com.ming.bidders.engine.Sleeper;

import io.micrometer.core.instrument.MeterRegistry;

@SpringBootTest
@AutoConfigureMockMvc
@Import(MongoContainerConfig.class)
class BidControllerIT {

    @Autowired MockMvc mvc;
    @Autowired MeterRegistry registry;

    /** No real latency in tests. */
    @TestBean Sleeper sleeper;
    /** nextLong() == 0 → nextDouble() == 0.0 (bid-rate roll always passes), nextInt(n) == 0 (first survivor). */
    @TestBean RandomGenerator random;

    static Sleeper sleeper() {
        return ms -> { };
    }

    static RandomGenerator random() {
        return () -> 0L;
    }

    private static String fixture() throws IOException {
        return new ClassPathResource("fixtures/bid-request-banner-300x250.json").getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    void contractFixtureGetsABid() throws Exception {
        mvc.perform(post("/bid/alpha").contentType(MediaType.APPLICATION_JSON).content(fixture()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("req-0001"))
                .andExpect(jsonPath("$.cur").value("USD"))
                .andExpect(jsonPath("$.seatbid[0].seat").value("alpha"))
                .andExpect(jsonPath("$.seatbid[0].bid[0].impid").value("imp-1"))
                .andExpect(jsonPath("$.seatbid[0].bid[0].price").value(Matchers.greaterThanOrEqualTo(0.5)))
                .andExpect(jsonPath("$.seatbid[0].bid[0].adm").value(Matchers.containsString("${AUCTION_PRICE}")))
                .andExpect(jsonPath("$.seatbid[0].bid[0].mtype").value(1));
    }

    @Test
    void floorAboveEveryPriceIsNoBid() throws Exception {
        var body = fixture().replace("\"bidfloor\": 0.5", "\"bidfloor\": 50.0");

        mvc.perform(post("/bid/alpha").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
    }

    @Test
    void unknownBidderIs404() throws Exception {
        mvc.perform(post("/bid/nope").contentType(MediaType.APPLICATION_JSON).content(fixture()))
                .andExpect(status().isNotFound());
    }

    @Test
    void malformedJsonIs400() throws Exception {
        mvc.perform(post("/bid/alpha").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void requestsAreCounted() throws Exception {
        mvc.perform(post("/bid/alpha").contentType(MediaType.APPLICATION_JSON).content(fixture()))
                .andExpect(status().isOk());

        var counter = registry.find("bidders.requests").tags("bidder", "alpha", "outcome", "BID").counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isGreaterThanOrEqualTo(1.0);
    }
}
