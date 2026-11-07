package com.ming.bidders.engine;

import java.util.List;
import java.util.UUID;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import com.ming.bidders.model.entity.Bidder;
import com.ming.bidders.model.entity.CreativeFormat;
import com.ming.bidders.model.openrtb.Bid;
import com.ming.bidders.model.openrtb.BidRequest;
import com.ming.bidders.model.openrtb.BidResponse;
import com.ming.bidders.model.openrtb.SeatBid;

/**
 * Picks → OpenRTB wire objects. {@link #toBid}: price is the creative's CPM; adm and nurl keep the exchange macros
 * ({@code ${AUCTION_PRICE}} …) for the exchange to substitute. {@link #toResponse}: one seat (the bidder key) holding
 * one bid per pick. Unmapped targets fail the build.
 */
@Mapper(componentModel = "spring", imports = UUID.class, unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface BidMapper {

    String CRID_PLACEHOLDER = "{crid}";
    String CURRENCY = "USD";

    @Mapping(target = "id", expression = "java(UUID.randomUUID().toString())")
    @Mapping(target = "impid", source = "pick.imp.id")
    @Mapping(target = "price", source = "pick.creative.priceCpm")
    @Mapping(target = "adm", source = "pick.creative.adm")
    @Mapping(target = "nurl", expression = "java(nurl(bidder, pick))")
    @Mapping(target = "crid", source = "pick.creative.crid")
    @Mapping(target = "adomain", source = "pick.creative.adomain")
    @Mapping(target = "cat", source = "pick.creative.cat")
    @Mapping(target = "w", source = "pick.creative.w")
    @Mapping(target = "h", source = "pick.creative.h")
    @Mapping(target = "mtype", expression = "java(mtype(pick.creative().format()))")
    Bid toBid(Bidder bidder, Pick pick);

    default BidResponse toResponse(BidRequest request, Bidder bidder, List<Pick> picks) {
        var bids = picks.stream().map(pick -> toBid(bidder, pick)).toList();
        return new BidResponse(request.id(), UUID.randomUUID().toString(), CURRENCY,
                List.of(new SeatBid(bidder.key(), bids)));
    }

    default String nurl(Bidder bidder, Pick pick) {
        return bidder.nurlTemplate().replace(CRID_PLACEHOLDER, pick.creative().crid());
    }

    /** OpenRTB 2.6 bid.mtype: 1 = banner, 2 = video. */
    default int mtype(CreativeFormat format) {
        return format == CreativeFormat.VIDEO ? 2 : 1;
    }
}
