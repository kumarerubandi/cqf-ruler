package org.opencds.cqf.common.retrieve;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.hl7.fhir.instance.model.api.IBaseResource;
import org.opencds.cqf.cql.retrieve.*;
import org.opencds.cqf.cql.searchparam.SearchParameterResolver;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.jpa.dao.DaoRegistry;
import ca.uhn.fhir.jpa.dao.IFhirResourceDao;
import ca.uhn.fhir.jpa.searchparam.SearchParameterMap;
import ca.uhn.fhir.rest.api.server.IBundleProvider;

public class JpaFhirRetrieveProvider extends SearchParamFhirRetrieveProvider {
    org.slf4j.Logger ourLog = org.slf4j.LoggerFactory.getLogger("searchparam");
    DaoRegistry registry;

    public JpaFhirRetrieveProvider(DaoRegistry registry, SearchParameterResolver searchParameterResolver) {
        super(searchParameterResolver);
        this.registry = registry;

    }

    @Override
    protected Iterable<Object> executeQueries(String dataType, List<SearchParameterMap> queries) {
        if (queries == null || queries.isEmpty()) {
            return Collections.emptyList();
        }

        List<Object> objects = new ArrayList<>();
        for (SearchParameterMap map : queries) {
            objects.addAll(executeQuery(dataType, map));
        }

        return objects;
    }

    protected Collection<Object> executeQuery(String dataType, SearchParameterMap map) {
        IFhirResourceDao<?> dao = this.registry.getResourceDao(dataType);
        IBundleProvider bundleProvider = dao.search(map);

        ourLog.info("running Mapstring:" + dataType + map.toNormalizedQueryString(FhirContext.forR4()));
        if (bundleProvider.size() == null) {
            return resolveResourceList(bundleProvider.getResources(0, 10000));
        }
        if (bundleProvider.size() == 0) {
            return new ArrayList<>();
        }
        List<IBaseResource> resourceList = bundleProvider.getResources(0, bundleProvider.size());
        return resolveResourceList(resourceList);
    }

    public synchronized Collection<Object> resolveResourceList(List<IBaseResource> resourceList) {
        List<Object> ret = new ArrayList<>();
        ourLog.info("size is: " + resourceList.size());
        for (IBaseResource res : resourceList) {
            Class<?> clazz = res.getClass();
            ret.add(clazz.cast(res));
        }
        // ret.addAll(resourceList);
        return ret;
    }
}