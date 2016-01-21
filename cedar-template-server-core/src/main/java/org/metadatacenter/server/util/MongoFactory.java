package org.metadatacenter.server.util;

import checkers.nullness.quals.NonNull;
import com.mongodb.MongoClient;

public class MongoFactory {

  @NonNull
  private static final MongoClient mongoClient = new MongoClient();

  @NonNull
  public static MongoClient getClient() {
    return mongoClient;
  }
}