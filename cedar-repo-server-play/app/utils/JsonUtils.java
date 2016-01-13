package utils;

import checkers.nullness.quals.NonNull;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public final class JsonUtils {

  private JsonUtils() {

  }

  // Remove a particular field from a JsonNode object
  public static @NonNull JsonNode removeField(@NonNull JsonNode node, @NonNull String fieldName) {
    ObjectNode object = (ObjectNode) node;
    object.remove(fieldName);
    return object;
  }

}