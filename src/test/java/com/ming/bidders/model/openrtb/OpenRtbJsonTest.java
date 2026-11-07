package com.ming.bidders.model.openrtb;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

class OpenRtbJsonTest {

    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void parsesRequestAndIgnoresUnknownFields() throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/fixtures/bid-request-banner-300x250.json")) {
            var request = mapper.readValue(in, BidRequest.class);

            assertThat(request.id()).isEqualTo("req-0001");
            assertThat(request.imp().get(0).banner().w()).isEqualTo(300);
            assertThat(request.imp().get(0).banner().h()).isEqualTo(250);
            assertThat(request.imp().get(0).bidfloor()).isEqualTo(0.5);
        }
    }

    @Test
    void responseOmitsNulls() {
        var bid = new Bid("b-1", "imp-1", 1.5, "<div/>", null, "cr-1",
                List.of("adv.example"), List.of("IAB1"), 300, 250, 1);

        var json = mapper.writeValueAsString(bid);

        assertThat(json).doesNotContain("\"nurl\"").contains("\"crid\":\"cr-1\"");
    }
}
