package org.metadatacenter.cedar.template.resources;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.metadatacenter.cedar.util.dw.CedarMicroserviceResource;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.exception.CedarException;
import org.metadatacenter.exception.CedarProcessingException;
import org.metadatacenter.model.CedarNodeType;
import org.metadatacenter.rest.exception.CedarAssertionException;
import org.metadatacenter.server.jsonld.LinkedDataUtil;
import org.metadatacenter.server.model.provenance.ProvenanceInfo;
import org.metadatacenter.util.provenance.ProvenanceUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static com.fasterxml.jackson.databind.node.JsonNodeType.NULL;

public class AbstractTemplateServerResource extends CedarMicroserviceResource {

  protected final LinkedDataUtil linkedDataUtil;
  protected final ProvenanceUtil provenanceUtil;
  protected static List<String> FIELD_NAMES_EXCLUSION_LIST;

  protected AbstractTemplateServerResource(CedarConfig cedarConfig) {
    super(cedarConfig);
    this.linkedDataUtil = cedarConfig.buildLinkedDataUtil();
    this.provenanceUtil = new ProvenanceUtil(linkedDataUtil);
    FIELD_NAMES_EXCLUSION_LIST = new ArrayList<>();
    FIELD_NAMES_EXCLUSION_LIST.addAll(cedarConfig.getTemplateRESTAPI().getExcludedFields());
  }

  protected void checkImportModeSetProvenanceAndId(CedarNodeType cedarNodeType, JsonNode element,
                                                   ProvenanceInfo pi, Optional<Boolean> importMode) {
    boolean im = (importMode != null && importMode.isPresent() && importMode.get());
    if (im) {
      if ((element.get("@id") == null) || (NULL.equals(element.get("@id").getNodeType()))) {
        throw new IllegalArgumentException("You must specify @id when importing data");
      }
    } else {
      if ((element.get("@id") != null) && (!NULL.equals(element.get("@id").getNodeType()))) {
        throw new IllegalArgumentException("Specifying @id for new objects is not allowed");
      }
      provenanceUtil.addProvenanceInfo(element, pi);
      String id = linkedDataUtil.buildNewLinkedDataId(cedarNodeType);
      ((ObjectNode) element).put("@id", id);
    }
  }

  protected Boolean ensureSummary(Optional<Boolean> summary) {
    if (summary == null || !summary.isPresent()) {
      return false;
    } else {
      return summary.get();
    }
  }

  protected static List<String> getAndCheckFieldNames(Optional<String> fieldNames, boolean summary) throws
      CedarAssertionException {
    if (fieldNames != null && fieldNames.isPresent()) {
      if (summary == true) {
        throw new CedarAssertionException(
            "It is no allowed to specify parameter 'field_names' and also set 'summary' to true!");
      } else if (fieldNames.get().length() > 0) {
        return Arrays.asList(fieldNames.get().split(","));
      }
    }
    return null;
  }

  protected static void checkPagingParametersAgainstTotal(Integer offset, long total) throws CedarException {
    if (offset != 0 && offset > total - 1) {
      throw new CedarProcessingException(
          "Parameter 'offset' must be smaller than the total count of objects, which is " + total + "!")
          .parameter("offset", offset)
          .parameter("total", total);
    }
  }

}