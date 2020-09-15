package org.opencds.cqf.dstu3.evaluation;

import org.opencds.cqf.cql.engine.data.CompositeDataProvider;
import org.opencds.cqf.cql.engine.data.DataProvider;
import org.opencds.cqf.cql.engine.engine.model.Dstu3FhirModelResolver;
import org.opencds.cqf.cql.engine.fhir.searchparam.SearchParameterResolver;
import org.opencds.cqf.cql.engine.terminology.TerminologyProvider;
import org.opencds.cqf.cql.engine.terminology.fhir.Dstu3FhirTerminologyProvider;
import org.opencds.cqf.common.evaluation.EvaluationProviderFactory;
import org.opencds.cqf.common.providers.Dstu3ApelonFhirTerminologyProvider;
import org.opencds.cqf.common.retrieve.JpaFhirRetrieveProvider;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.jpa.dao.DaoRegistry;
import ca.uhn.fhir.jpa.searchparam.registry.ISearchParamRegistry;

// This class is a relatively dumb factory for data providers. It supports only
// creating JPA providers for FHIR and only basic auth for terminology
public class ProviderFactory implements EvaluationProviderFactory {

    DaoRegistry registry;
    TerminologyProvider defaultTerminologyProvider;
    FhirContext fhirContext;
    ISearchParamRegistry searchParamRegistry;

    public ProviderFactory(FhirContext fhirContext, DaoRegistry registry,
            TerminologyProvider defaultTerminologyProvider) {
        this.defaultTerminologyProvider = defaultTerminologyProvider;
        this.registry = registry;
        this.fhirContext = fhirContext;
    }

    public DataProvider createDataProvider(String model, String version) {
        return this.createDataProvider(model, version, null, null, null);
    }

    public DataProvider createDataProvider(String model, String version, String url, String user, String pass) {
        TerminologyProvider terminologyProvider = this.createTerminologyProvider(model, version, url, user, pass);
        return this.createDataProvider(model, version, terminologyProvider);
    }

    public DataProvider createDataProvider(String model, String version, TerminologyProvider terminologyProvider) {
        if (model.equals("FHIR") && version.equals("3.0.0")) {
            Dstu3FhirModelResolver modelResolver = new Dstu3FhirModelResolver();
            JpaFhirRetrieveProvider retrieveProvider = new JpaFhirRetrieveProvider(this.registry, new SearchParameterResolver(this.fhirContext));
            retrieveProvider.setTerminologyProvider(terminologyProvider);
            retrieveProvider.setExpandValueSets(true);

            return new CompositeDataProvider(modelResolver, retrieveProvider);
        }

        throw new IllegalArgumentException(
                String.format("Can't construct a data provider for model %s version %s", model, version));
    }

    public TerminologyProvider createTerminologyProvider(String model, String version, String url, String user, String pass) {
        if (url != null && url.contains("apelon.com")) {
            return new Dstu3ApelonFhirTerminologyProvider(this.fhirContext)
            .withBasicAuth(user, pass).setEndpoint(url, false);
        }
        else if (url != null && !url.isEmpty()) {
            return new Dstu3FhirTerminologyProvider(this.fhirContext).withBasicAuth(user, pass).setEndpoint(url, false);
        } else
            return this.defaultTerminologyProvider;
    }
}