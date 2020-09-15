package org.opencds.cqf.r4.servlet;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.FhirVersionEnum;
import ca.uhn.fhir.rest.server.IResourceProvider;
import ca.uhn.fhir.rest.server.exceptions.AuthenticationException;
import ca.uhn.fhir.rest.server.exceptions.BaseServerResponseException;
import ca.uhn.fhir.rest.server.exceptions.ForbiddenOperationException;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;

import org.opencds.cqf.cds.discovery.DiscoveryResolutionR4;
import org.opencds.cqf.cds.evaluation.EvaluationContext;
import org.opencds.cqf.cds.evaluation.R4EvaluationContext;
import org.opencds.cqf.cds.hooks.Hook;
import org.opencds.cqf.cds.hooks.HookFactory;
import org.opencds.cqf.cds.hooks.R4HookEvaluator;
import org.opencds.cqf.cds.request.JsonHelper;
import org.opencds.cqf.cds.request.Request;
import org.opencds.cqf.cds.response.CdsCard;
import com.google.gson.*;
import org.apache.http.entity.ContentType;
import org.cqframework.cql.elm.execution.Library;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.PlanDefinition;
import org.opencds.cqf.cql.engine.data.CompositeDataProvider;
import org.opencds.cqf.cql.engine.execution.Context;
import org.opencds.cqf.common.evaluation.LibraryLoader;
import org.opencds.cqf.cql.engine.model.ModelResolver;
import org.opencds.cqf.cql.engine.fhir.model.R4FhirModelResolver;
import org.opencds.cqf.common.config.HapiProperties;
import org.opencds.cqf.common.exceptions.InvalidRequestException;
import org.opencds.cqf.common.providers.LibraryResolutionProvider;
import org.opencds.cqf.common.retrieve.JpaFhirRetrieveProvider;
import org.opencds.cqf.r4.helpers.LibraryHelper;
import org.opencds.cqf.r4.providers.JpaTerminologyProvider;
import org.opencds.cqf.r4.providers.PlanDefinitionApplyProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@WebServlet(name = "cds-services")
public class CdsHooksServlet extends HttpServlet
{
    private FhirVersionEnum version = FhirVersionEnum.R4;
    private static final Logger logger = LoggerFactory.getLogger(CdsHooksServlet.class);

    private static PlanDefinitionApplyProvider planDefinitionProvider;

    private static LibraryResolutionProvider<org.hl7.fhir.r4.model.Library> libraryResolutionProvider;

    private static JpaFhirRetrieveProvider fhirRetrieveProvider;

    private static JpaTerminologyProvider jpaTerminologyProvider;


    // TODO: There's probably a way to wire this all up using Spring
    public static void setPlanDefinitionProvider(PlanDefinitionApplyProvider planDefinitionProvider) {
        CdsHooksServlet.planDefinitionProvider = planDefinitionProvider;
    }

    public static void setLibraryResolutionProvider(
            LibraryResolutionProvider<org.hl7.fhir.r4.model.Library> libraryResolutionProvider) {
        CdsHooksServlet.libraryResolutionProvider = libraryResolutionProvider;
    }

    public static void setSystemRetrieveProvider(JpaFhirRetrieveProvider fhirRetrieveProvider) {
        CdsHooksServlet.fhirRetrieveProvider = fhirRetrieveProvider;
    }

    public static void setSystemTerminologyProvider(JpaTerminologyProvider jpaTerminologyProvider) {
        CdsHooksServlet.jpaTerminologyProvider = jpaTerminologyProvider;
    }

    // CORS Pre-flight
    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        setAccessControlHeaders(resp);

        resp.setHeader("Content-Type", ContentType.APPLICATION_JSON.getMimeType());
        resp.setHeader("X-Content-Type-Options", "nosniff");
        
        resp.setStatus(HttpServletResponse.SC_OK);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException
    {
        logger.info(request.getRequestURI());
        if (!request.getRequestURL().toString().endsWith("cds-services"))
        {
            logger.error(request.getRequestURI());
            throw new ServletException("This servlet is not configured to handle GET requests.");
        }

        this.setAccessControlHeaders(response);
        response.setHeader("Content-Type", ContentType.APPLICATION_JSON.getMimeType());
        response.getWriter().println(new GsonBuilder().setPrettyPrinting().create().toJson(getServices()));
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException
    {
        logger.info(request.getRequestURI());

        try {
            // validate that we are dealing with JSON
            if (!request.getContentType().startsWith("application/json"))
            {
                throw new ServletException(
                        String.format(
                                "Invalid content type %s. Please use application/json.",
                                request.getContentType()
                        )
                );
            }

            String baseUrl =
                    request.getRequestURL().toString()
                            .replace(request.getPathInfo(), "").replace(request.getServletPath(), "") + "/fhir";
            String service = request.getPathInfo().replace("/", "");

            JsonParser parser = new JsonParser();
            Request cdsHooksRequest =
                    new Request(
                            service,
                            parser.parse(request.getReader()).getAsJsonObject(),
                            JsonHelper.getObjectRequired(getService(service), "prefetch")
                    );

            logger.info(cdsHooksRequest.getRequestJson().toString());

            Hook hook = HookFactory.createHook(cdsHooksRequest);

            PlanDefinition planDefinition = planDefinitionProvider.getDao().read(new IdType(hook.getRequest().getServiceName()));
            LibraryLoader libraryLoader = LibraryHelper.createLibraryLoader(libraryResolutionProvider);
            Library library = LibraryHelper.resolvePrimaryLibrary(planDefinition, libraryLoader, libraryResolutionProvider);

        
            R4FhirModelResolver resolver = new R4FhirModelResolver();
            CompositeDataProvider provider = new CompositeDataProvider(resolver, fhirRetrieveProvider);

            Context context = new Context(library);
            context.registerDataProvider("http://hl7.org/fhir", provider); // TODO make sure tooling handles remote provider case
            context.registerTerminologyProvider(jpaTerminologyProvider);
            context.registerLibraryLoader(libraryLoader);
            context.setContextValue("Patient", hook.getRequest().getContext().getPatientId().replace("Patient/", ""));
            context.setExpressionCaching(true);


            EvaluationContext evaluationContext = new R4EvaluationContext(hook, version, FhirContext.forR4().newRestfulGenericClient(baseUrl), jpaTerminologyProvider, context, library, planDefinition);

            this.setAccessControlHeaders(response);

            response.setHeader("Content-Type", ContentType.APPLICATION_JSON.getMimeType());

            R4HookEvaluator evaluator = new R4HookEvaluator();

            String jsonResponse = toJsonResponse(evaluator.evaluate(evaluationContext));

            logger.info(jsonResponse);

            response.getWriter().println(jsonResponse);
        }
        catch (BaseServerResponseException e){
            this.setAccessControlHeaders(response);

            switch (e.getStatusCode()) {
                case 401:
                case 403:
                case 404:
                    response.getWriter().println("ERROR: Precondition Failed. FHIR server returned: " + e.getStatusCode());
                    response.getWriter().println(e.getMessage());
                    response.setStatus(412);
                    break;
                default:
                    response.getWriter().println("ERROR: Unhandled error. FHIR server returned: " + e.getStatusCode());
                    response.getWriter().println(e.getMessage());
                    response.setStatus(500);
            }
        }
        catch(Exception e) {
            this.setAccessControlHeaders(response);
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            String exceptionAsString = sw.toString();
            response.getWriter().println("ERROR: Unhandled error.");
            response.getWriter().println(exceptionAsString);
            response.setStatus(500);
        }
    }

    private JsonObject getService(String service)
    {
        JsonArray services = getServices().get("services").getAsJsonArray();
        List<String> ids = new ArrayList<>();
        for (JsonElement element : services)
        {
            if (element.isJsonObject() && element.getAsJsonObject().has("id"))
            {
                ids.add(element.getAsJsonObject().get("id").getAsString());
                if (element.isJsonObject() && element.getAsJsonObject().get("id").getAsString().equals(service))
                {
                    return element.getAsJsonObject();
                }
            }
        }
        throw new InvalidRequestException("Cannot resolve service: " + service + "\nAvailable services: " + ids.toString());
    }

    private JsonObject getServices()
    {
        return new DiscoveryResolutionR4(FhirContext.forR4().newRestfulGenericClient(HapiProperties.getServerAddress())).resolve().getAsJson();
    }

    private String toJsonResponse(List<CdsCard> cards)
    {
        JsonObject ret = new JsonObject();
        JsonArray cardArray = new JsonArray();

        for (CdsCard card : cards)
        {
            cardArray.add(card.toJson());
        }

        ret.add("cards", cardArray);

        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        return  gson.toJson(ret);
    }


    private void setAccessControlHeaders(HttpServletResponse resp) {
        if (HapiProperties.getCorsEnabled())
        {
            resp.setHeader("Access-Control-Allow-Origin", HapiProperties.getCorsAllowedOrigin());
            resp.setHeader("Access-Control-Allow-Methods", String.join(", ", Arrays.asList("GET", "HEAD", "POST", "OPTIONS")));
            resp.setHeader("Access-Control-Allow-Headers", 
                String.join(", ", Arrays.asList(
                    "x-fhir-starter",
                    "Origin",
                    "Accept",
                    "X-Requested-With",
                    "Content-Type",
                    "Authorization",
                    "Cache-Control")));
            resp.setHeader("Access-Control-Expose-Headers", String.join(", ", Arrays.asList("Location", "Content-Location")));
            resp.setHeader("Access-Control-Max-Age", "86400");
        }
    }
}