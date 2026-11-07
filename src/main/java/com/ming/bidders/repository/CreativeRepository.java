package com.ming.bidders.repository;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.ming.bidders.model.entity.Creative;

/** Used only by the catalog loader — never on the request path. */
public interface CreativeRepository extends MongoRepository<Creative, String> { }
