package com.ming.bidders.engine;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import com.ming.bidders.model.entity.Bidder;
import com.ming.bidders.model.openrtb.Imp;
import com.ming.bidders.model.openrtb.Video;

class BidMapperTest {

    private final BidMapper mapper = Mappers.getMapper(BidMapper.class);
    private final Bidder alpha = TestData.bidder(0, 0, 1.0);

    @Test
    void mapsEveryBannerField() {
        var pick = new Pick(TestData.bannerImp("imp-7"), TestData.bannerCreative("cr-1", 1.5));

        var bid = mapper.toBid(alpha, pick);

        assertThat(bid.id()).isNotBlank();
        assertThat(bid.impid()).isEqualTo("imp-7");
        assertThat(bid.price()).isEqualTo(1.5);
        assertThat(bid.adm()).isEqualTo("<div>${AUCTION_PRICE}</div>");
        assertThat(bid.nurl()).isEqualTo("https://alpha.bidders.invalid/win?crid=cr-1&price=${AUCTION_PRICE}");
        assertThat(bid.crid()).isEqualTo("cr-1");
        assertThat(bid.adomain()).containsExactly("adv.example");
        assertThat(bid.cat()).containsExactly("IAB1");
        assertThat(bid.w()).isEqualTo(300);
        assertThat(bid.h()).isEqualTo(250);
        assertThat(bid.mtype()).isEqualTo(1);
    }

    @Test
    void videoCreativeIsMtypeTwo() {
        var imp = new Imp("imp-1", null, new Video(320, 480, List.of("video/mp4"), 30), null, "USD");

        assertThat(mapper.toBid(alpha, new Pick(imp, TestData.videoCreative("cr-v", 6.0))).mtype()).isEqualTo(2);
    }

    @Test
    void everyBidGetsItsOwnId() {
        var pick = new Pick(TestData.bannerImp("1"), TestData.bannerCreative("cr-1", 1.0));

        assertThat(mapper.toBid(alpha, pick).id()).isNotEqualTo(mapper.toBid(alpha, pick).id());
    }

    // --- toResponse --------------------------------------------------------------------------------------------

    @Test
    void responseKeepsExchangeMacrosUntouched() {
        var pick = new Pick(TestData.bannerImp("1"), TestData.bannerCreative("cr-1", 1.5));

        var bid = mapper.toResponse(TestData.request(pick.imp()), alpha, List.of(pick)).seatbid().get(0).bid().get(0);

        assertThat(bid.adm()).isEqualTo("<div>${AUCTION_PRICE}</div>");
        assertThat(bid.nurl()).isEqualTo("https://alpha.bidders.invalid/win?crid=cr-1&price=${AUCTION_PRICE}");
    }

    @Test
    void responseSetsPriceAndMtypePerPick() {
        var bannerPick = new Pick(TestData.bannerImp("1"), TestData.bannerCreative("cr-b", 1.5));
        var videoImp = new Imp("2", null, new Video(320, 480, null, null), null, "USD");
        var videoPick = new Pick(videoImp, TestData.videoCreative("cr-v", 6.0));

        var bids = mapper.toResponse(TestData.request(bannerPick.imp(), videoImp), alpha, List.of(bannerPick, videoPick))
                .seatbid().get(0).bid();

        assertThat(bids.get(0).price()).isEqualTo(1.5);
        assertThat(bids.get(0).mtype()).isEqualTo(1);
        assertThat(bids.get(1).mtype()).isEqualTo(2);
        assertThat(bids.get(0).w()).isEqualTo(300);
        assertThat(bids.get(0).crid()).isEqualTo("cr-b");
    }

    @Test
    void responseHasOneBidPerPickUnderTheBidderSeat() {
        var firstPick = new Pick(TestData.bannerImp("1"), TestData.bannerCreative("cr-1", 1.0));
        var secondPick = new Pick(TestData.bannerImp("2"), TestData.bannerCreative("cr-2", 2.0));

        var response = mapper.toResponse(TestData.request(firstPick.imp(), secondPick.imp()), alpha,
                List.of(firstPick, secondPick));

        assertThat(response.id()).isEqualTo("req-1");
        assertThat(response.cur()).isEqualTo("USD");
        assertThat(response.bidid()).isNotBlank();
        assertThat(response.seatbid()).hasSize(1);
        assertThat(response.seatbid().get(0).seat()).isEqualTo("alpha");
        assertThat(response.seatbid().get(0).bid()).extracting("impid").containsExactly("1", "2");
        assertThat(response.seatbid().get(0).bid()).extracting("id").doesNotHaveDuplicates();
    }
}
