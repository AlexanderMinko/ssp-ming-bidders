package com.ming.bidders.repository;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.ming.bidders.model.entity.Bidder;

/** Used only by the catalog loader — never on the request path. */
public interface BidderRepository extends MongoRepository<Bidder, String> { }
