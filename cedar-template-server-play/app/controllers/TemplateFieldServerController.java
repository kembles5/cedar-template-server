package controllers;

import com.fasterxml.jackson.databind.JsonNode;
import org.metadatacenter.constant.ConfigConstants;
import org.metadatacenter.constant.CustomHttpConstants;
import org.metadatacenter.constant.HttpConstants;
import org.metadatacenter.server.security.Authorization;
import org.metadatacenter.server.security.CedarAuthFromRequestFactory;
import org.metadatacenter.server.security.exception.CedarAccessException;
import org.metadatacenter.server.security.model.auth.CedarPermission;
import org.metadatacenter.server.service.FieldNameInEx;
import org.metadatacenter.server.service.TemplateFieldService;
import org.metadatacenter.util.http.LinkHeaderUtil;
import org.metadatacenter.util.http.UrlUtil;
import org.metadatacenter.util.json.JsonUtils;
import play.Logger;
import play.libs.Json;
import play.mvc.Result;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TemplateFieldServerController extends AbstractTemplateServerController {

  private static TemplateFieldService<String, JsonNode> templateFieldService;
  protected static List<String> FIELD_NAMES_SUMMARY_LIST;

  static {
    FIELD_NAMES_SUMMARY_LIST = new ArrayList<>();
    FIELD_NAMES_SUMMARY_LIST.addAll(config.getStringList(ConfigConstants.FIELD_NAMES_SUMMARY_TEMPLATE_FIELD));
  }

  public static void injectTemplateFieldService(TemplateFieldService<String, JsonNode> tfs) {
    templateFieldService = tfs;
  }

  public static Result findAllTemplateFields(Integer limit, Integer offset, boolean summary, String fieldNames) {
    try {
      Authorization.mustHavePermission(CedarAuthFromRequestFactory.fromRequest(request()), CedarPermission
          .TEMPLATE_FIELD_READ);
      limit = ensureLimit(limit);
      checkPagingParameters(limit, offset);
      List<String> fieldNameList = getAndCheckFieldNames(fieldNames, summary);
      Map<String, Object> r = new HashMap<>();
      List<JsonNode> elements = null;
      if (summary) {
        elements = templateFieldService.findAllTemplateFields(limit, offset, FIELD_NAMES_SUMMARY_LIST,
            FieldNameInEx.INCLUDE);
      } else if (fieldNameList != null) {
        elements = templateFieldService.findAllTemplateFields(limit, offset, fieldNameList, FieldNameInEx.INCLUDE);
      } else {
        elements = templateFieldService.findAllTemplateFields(limit, offset, FIELD_NAMES_EXCLUSION_LIST,
            FieldNameInEx.EXCLUDE);
      }
      long total = templateFieldService.count();
      response().setHeader(CustomHttpConstants.HEADER_TOTAL_COUNT, String.valueOf(total));
      checkPagingParametersAgainstTotal(offset, total);
      String absoluteUrl = routes.TemplateFieldServerController.findAllTemplateFields(0, 0, false, null).absoluteURL
          (request());
      absoluteUrl = UrlUtil.trimUrlParameters(absoluteUrl);
      String linkHeader = LinkHeaderUtil.getPagingLinkHeader(absoluteUrl, total, limit, offset);
      if (!linkHeader.isEmpty()) {
        response().setHeader(HttpConstants.HTTP_HEADER_LINK, linkHeader);
      }
      return ok(Json.toJson(elements));
    } catch (CedarAccessException e) {
      Logger.error("Access Error while reading the template fields", e);
      return forbiddenWithError(e);
    } catch (Exception e) {
      Logger.error("Error while reading the template fields", e);
      return internalServerErrorWithError(e);
    }
  }

  public static Result findTemplateField(String templateFieldId) {
    try {
      Authorization.mustHavePermission(CedarAuthFromRequestFactory.fromRequest(request()), CedarPermission
          .TEMPLATE_FIELD_READ);
      JsonNode templateField = templateFieldService.findTemplateField(templateFieldId);
      if (templateField != null) {
        // Remove autogenerated _id field to avoid exposing it
        templateField = JsonUtils.removeField(templateField, "_id");
        return ok(templateField);
      }
      Logger.error("Template field not found:(" + templateFieldId + ")");
      return notFound();
    } catch (IllegalArgumentException e) {
      Logger.error("Illegal Argument while reading the template field", e);
      return badRequestWithError(e);
    } catch (CedarAccessException e) {
      Logger.error("Access Error while reading the template field", e);
      return forbiddenWithError(e);
    } catch (Exception e) {
      Logger.error("Error while reading the template field", e);
      return internalServerErrorWithError(e);
    }
  }


}
