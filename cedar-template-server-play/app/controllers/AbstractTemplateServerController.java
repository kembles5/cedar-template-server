package controllers;

import org.metadatacenter.constant.ConfigConstants;
import org.metadatacenter.server.play.AbstractCedarController;
import org.metadatacenter.server.service.TemplateFieldService;
import org.metadatacenter.server.service.UserService;
import play.Configuration;
import play.Play;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class AbstractTemplateServerController extends AbstractCedarController {
  protected static List<String> FIELD_NAMES_EXCLUSION_LIST;
  protected static Configuration config;

  static {
    config = Play.application().configuration();
    FIELD_NAMES_EXCLUSION_LIST = new ArrayList<>();
    FIELD_NAMES_EXCLUSION_LIST.addAll(config.getStringList(ConfigConstants.FIELD_NAMES_LIST_EXCLUSION));
  }

  protected static Integer ensureLimit(Integer limit) {
    return limit == null ? config.getInt(ConfigConstants.PAGINATION_DEFAULT_PAGE_SIZE) : limit;
  }

  protected static void checkPagingParameters(Integer limit, Integer offset) {
    // check offset
    if (offset < 0) {
      throw new IllegalArgumentException("Parameter 'offset' must be positive!");
    }
    // check limit
    if (limit <= 0) {
      throw new IllegalArgumentException("Parameter 'limit' must be greater than zero!");
    }
    int maxPageSize = config.getInt(ConfigConstants.PAGINATION_MAX_PAGE_SIZE);
    if (limit > maxPageSize) {
      throw new IllegalArgumentException("Parameter 'limit' must be at most " + maxPageSize + "!");
    }
  }

  protected static void checkPagingParametersAgainstTotal(Integer offset, long total) {
    if (offset != 0 && offset > total - 1) {
      throw new IllegalArgumentException("Parameter 'offset' must be smaller than the total count of objects, which " +
          "is " + total + "!");
    }
  }

  protected static List<String> getAndCheckFieldNames(String fieldNames, boolean summary) {
    if (fieldNames != null) {
      if (summary == true) {
        throw new IllegalArgumentException("It is no allowed to specify parameter 'fieldNames' and also set 'summary'" +
            " to true!");
      } else if (fieldNames.length() > 0) {
        return Arrays.asList(fieldNames.split(","));
      }
    }
    return null;
  }
}