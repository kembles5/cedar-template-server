package org.metadatacenter.cedar.template.resources;

import com.codahale.metrics.annotation.Timed;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.fge.jsonschema.core.exceptions.ProcessingException;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.constant.CustomHttpConstants;
import org.metadatacenter.constant.HttpConstants;
import org.metadatacenter.error.CedarErrorKey;
import org.metadatacenter.exception.CedarException;
import org.metadatacenter.model.CedarNodeType;
import org.metadatacenter.rest.context.CedarRequestContext;
import org.metadatacenter.rest.context.CedarRequestContextFactory;
import org.metadatacenter.rest.exception.CedarAssertionException;
import org.metadatacenter.server.model.provenance.ProvenanceInfo;
import org.metadatacenter.server.security.model.auth.CedarPermission;
import org.metadatacenter.server.service.FieldNameInEx;
import org.metadatacenter.server.service.TemplateFieldService;
import org.metadatacenter.server.service.TemplateService;
import org.metadatacenter.util.http.CedarResponse;
import org.metadatacenter.util.http.CedarUrlUtil;
import org.metadatacenter.util.http.LinkHeaderUtil;
import org.metadatacenter.util.mongo.MongoUtils;
import org.metadatacenter.util.provenance.ProvenanceUtil;

import javax.management.InstanceNotFoundException;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.net.URI;
import java.util.*;

import static org.metadatacenter.rest.assertion.GenericAssertions.LoggedIn;

@Path("/templates")
@Produces(MediaType.APPLICATION_JSON)
public class TemplatesResource extends AbstractTemplateServerResource {

  private final TemplateService<String, JsonNode> templateService;
  private final TemplateFieldService<String, JsonNode> templateFieldService;

  protected static List<String> FIELD_NAMES_SUMMARY_LIST;

  public TemplatesResource(CedarConfig cedarConfig, TemplateService<String, JsonNode> templateService,
                           TemplateFieldService<String, JsonNode> templateFieldService) {
    super(cedarConfig);
    this.templateService = templateService;
    this.templateFieldService = templateFieldService;
    FIELD_NAMES_SUMMARY_LIST = new ArrayList<>();
    FIELD_NAMES_SUMMARY_LIST.addAll(cedarConfig.getTemplateRESTAPISummaries().getTemplate().getFields());
  }

  @POST
  @Timed
  public Response createTemplate(@QueryParam("importMode") Optional<Boolean> importMode) throws
      CedarException {
    CedarRequestContext c = CedarRequestContextFactory.fromRequest(request);
    c.must(c.user()).be(LoggedIn);
    c.must(c.user()).have(CedarPermission.TEMPLATE_CREATE);

    //TODO: test if it is not empty
    //c.must(c.request().getRequestBody()).be(NonEmpty);
    JsonNode template = c.request().getRequestBody().asJson();

    ProvenanceInfo pi = ProvenanceUtil.build(cedarConfig, c.getCedarUser());
    checkImportModeSetProvenanceAndId(CedarNodeType.TEMPLATE, template, pi, importMode);

    JsonNode createdTemplate = null;
    try {
      templateFieldService.saveNewFieldsAndReplaceIds(template, pi,
          cedarConfig.getLinkedDataPrefix(CedarNodeType.FIELD));
      createdTemplate = templateService.createTemplate(template);
    } catch (IOException e) {
      return CedarResponse.internalServerError()
          .errorKey(CedarErrorKey.TEMPLATE_NOT_CREATED)
          .errorMessage("The template can not be created")
          .exception(e)
          .build();
    }
    MongoUtils.removeIdField(createdTemplate);

    String id = createdTemplate.get("@id").asText();

    URI uri = CedarUrlUtil.getIdURI(uriInfo, id);
    return Response.created(uri).entity(createdTemplate).build();
  }

  @GET
  @Timed
  @Path("/{id}")
  public Response findTemplate(@PathParam("id") String id) throws CedarAssertionException {
    CedarRequestContext c = CedarRequestContextFactory.fromRequest(request);
    c.must(c.user()).be(LoggedIn);
    c.must(c.user()).have(CedarPermission.TEMPLATE_READ);

    JsonNode template = null;
    try {
      template = templateService.findTemplate(id);
    } catch (IOException | ProcessingException e) {
      return CedarResponse.internalServerError()
          .id(id)
          .errorKey(CedarErrorKey.TEMPLATE_NOT_FOUND)
          .errorMessage("The template can not be found by id:" + id)
          .exception(e)
          .build();
    }
    if (template == null) {
      return CedarResponse.notFound()
          .id(id)
          .errorKey(CedarErrorKey.TEMPLATE_NOT_FOUND)
          .errorMessage("The template can not be found by id:" + id)
          .build();
    } else {
      MongoUtils.removeIdField(template);
      return Response.ok().entity(template).build();
    }
  }

  @GET
  @Timed
  public Response findAllTemplates(@QueryParam("limit") Optional<Integer> limitParam,
                                   @QueryParam("offset") Optional<Integer> offsetParam,
                                   @QueryParam("summary") Optional<Boolean> summaryParam,
                                   @QueryParam("fieldNames") Optional<String> fieldNamesParam) throws
      CedarAssertionException {

    CedarRequestContext c = CedarRequestContextFactory.fromRequest(request);
    c.must(c.user()).be(LoggedIn);
    c.must(c.user()).have(CedarPermission.TEMPLATE_READ);

    Integer limit = ensureLimit(limitParam);
    Integer offset = ensureOffset(offsetParam);
    Boolean summary = ensureSummary(summaryParam);

    checkPagingParameters(limit, offset);
    List<String> fieldNameList = getAndCheckFieldNames(fieldNamesParam, summary);
    Map<String, Object> r = new HashMap<>();
    List<JsonNode> templates = null;
    try {
      if (summary) {
        templates = templateService.findAllTemplates(limit, offset, FIELD_NAMES_SUMMARY_LIST, FieldNameInEx.INCLUDE);
      } else if (fieldNameList != null) {
        templates = templateService.findAllTemplates(limit, offset, fieldNameList, FieldNameInEx.INCLUDE);
      } else {
        templates = templateService.findAllTemplates(limit, offset, FIELD_NAMES_EXCLUSION_LIST, FieldNameInEx.EXCLUDE);
      }
    } catch (IOException e) {
      return CedarResponse.internalServerError()
          .errorKey(CedarErrorKey.TEMPLATES_NOT_LISTED)
          .errorMessage("The templates can not be listed")
          .exception(e)
          .build();
    }
    long total = templateService.count();
    checkPagingParametersAgainstTotal(offset, total);

    String absoluteUrl = uriInfo.getAbsolutePathBuilder().build().toString();
    String linkHeader = LinkHeaderUtil.getPagingLinkHeader(absoluteUrl, total, limit, offset);
    Response.ResponseBuilder responseBuilder = Response.ok().entity(templates);
    responseBuilder.header(CustomHttpConstants.HEADER_TOTAL_COUNT, String.valueOf(total));
    if (!linkHeader.isEmpty()) {
      responseBuilder.header(HttpConstants.HTTP_HEADER_LINK, linkHeader);
    }
    return responseBuilder.build();
  }

  @PUT
  @Timed
  @Path("/{id}")
  public Response updateTemplate(@PathParam("id") String id) throws CedarException {
    CedarRequestContext c = CedarRequestContextFactory.fromRequest(request);
    c.must(c.user()).be(LoggedIn);
    c.must(c.user()).have(CedarPermission.TEMPLATE_UPDATE);

    JsonNode newTemplate = c.request().getRequestBody().asJson();
    ProvenanceInfo pi = ProvenanceUtil.build(cedarConfig, c.getCedarUser());
    ProvenanceUtil.patchProvenanceInfo(newTemplate, pi);
    JsonNode updatedTemplate = null;
    try {
      templateFieldService.saveNewFieldsAndReplaceIds(newTemplate, pi,
          cedarConfig.getLinkedDataPrefix(CedarNodeType.FIELD));
      updatedTemplate = templateService.updateTemplate(id, newTemplate);
    } catch (InstanceNotFoundException e) {
      return CedarResponse.notFound()
          .id(id)
          .errorKey(CedarErrorKey.TEMPLATE_NOT_FOUND)
          .errorMessage("The template can not be found by id:" + id)
          .exception(e)
          .build();
    } catch (IOException e) {
      return CedarResponse.internalServerError()
          .id(id)
          .errorKey(CedarErrorKey.TEMPLATE_NOT_UPDATED)
          .errorMessage("The template can not be updated by id:" + id)
          .exception(e)
          .build();
    }
    MongoUtils.removeIdField(updatedTemplate);
    return Response.ok().entity(updatedTemplate).build();
  }

  @DELETE
  @Timed
  @Path("/{id}")
  public Response deleteTemplate(@PathParam("id") String id) throws CedarAssertionException {
    CedarRequestContext c = CedarRequestContextFactory.fromRequest(request);
    c.must(c.user()).be(LoggedIn);
    c.must(c.user()).have(CedarPermission.TEMPLATE_DELETE);

    try {
      templateService.deleteTemplate(id);
    } catch (InstanceNotFoundException e) {
      return CedarResponse.notFound()
          .id(id)
          .errorKey(CedarErrorKey.TEMPLATE_NOT_FOUND)
          .errorMessage("The template can not be found by id:" + id)
          .exception(e)
          .build();
    } catch (IOException e) {
      return CedarResponse.internalServerError()
          .id(id)
          .errorKey(CedarErrorKey.TEMPLATE_NOT_DELETED)
          .errorMessage("The template can not be deleted by id:" + id)
          .exception(e)
          .build();
    }
    return CedarResponse.noContent().build();
  }

}