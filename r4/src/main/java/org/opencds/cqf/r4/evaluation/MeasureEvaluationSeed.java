package org.opencds.cqf.r4.evaluation;

import java.util.Date;
import java.util.HashMap;
import java.util.List;

import org.apache.commons.lang3.tuple.Triple;
import org.cqframework.cql.elm.execution.Library;
import org.cqframework.cql.elm.execution.UsingDef;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Measure;
import org.opencds.cqf.cql.engine.data.DataProvider;
import org.opencds.cqf.cql.engine.execution.Context;
import org.opencds.cqf.common.evaluation.LibraryLoader;
import org.opencds.cqf.cql.engine.runtime.DateTime;
import org.opencds.cqf.cql.engine.runtime.Interval;
import org.opencds.cqf.cql.engine.terminology.TerminologyProvider;
import org.opencds.cqf.r4.helpers.LibraryHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.opencds.cqf.common.evaluation.EvaluationProviderFactory;
import org.opencds.cqf.common.helpers.DateHelper;
import org.opencds.cqf.common.helpers.UsingHelper;
import org.opencds.cqf.common.providers.LibraryResolutionProvider;
import org.opencds.cqf.common.retrieve.JpaFhirRetrieveProvider;

import lombok.Data;

@Data
public class MeasureEvaluationSeed {
    private Measure measure;
    private Context context;
    private Interval measurementPeriod;
    private LibraryLoader libraryLoader;
    private LibraryResolutionProvider<org.hl7.fhir.r4.model.Library> libraryResourceProvider;
    private EvaluationProviderFactory providerFactory;
    private DataProvider dataProvider;
    private static final Logger logger = LoggerFactory.getLogger(MeasureEvaluationSeed.class);
    public static HashMap<String, Library> libraryMap = new HashMap<>();
    private Library library = null;
    public static final String LOCAL_RETRIEVER = "local";
    public static final String REMOTE_RETRIEVER = "remoteFhir";
    public static final String INMEMORY_RETRIEVER = "inMemory";
    private String retrieverType = LOCAL_RETRIEVER;
    private Bundle valueSetsBundle; 
    

    public MeasureEvaluationSeed(EvaluationProviderFactory providerFactory, LibraryLoader libraryLoader,
            LibraryResolutionProvider<org.hl7.fhir.r4.model.Library> libraryResourceProvider) {
        this.providerFactory = providerFactory;
        this.libraryLoader = libraryLoader;
        this.libraryResourceProvider = libraryResourceProvider;
        this.valueSetsBundle = new Bundle();
    }

    public void setup(Measure measure, String periodStart, String periodEnd, String productLine, String source,
            String user, String pass) {
        this.measure = measure;

        if (!libraryMap.containsKey(measure.getId())) {
            LibraryHelper.loadLibraries(measure, this.libraryLoader, this.libraryResourceProvider);

            // resolve primary library

            this.library = LibraryHelper.resolvePrimaryLibrary(measure, libraryLoader, this.libraryResourceProvider);
            libraryMap.put(measure.getId(), this.library);
        } else {
            this.library = libraryMap.get(measure.getId());
        }
        setupLibrary(library, periodStart, periodEnd, productLine, source, user, pass);
    }

    public void setupLibrary(String libraryName, String periodStart, String periodEnd, String productLine,
            String source, String user, String pass) {
        this.library = LibraryHelper.resolveLibraryById(libraryName, libraryLoader, libraryResourceProvider);
        setupLibrary(this.library, periodStart, periodEnd, productLine, source, user, pass);
    }

    public Library getLibrary() {
        return this.library;
    }

    public void setupLibrary(Library library, String periodStart, String periodEnd, String productLine, String source,
            String user, String pass) {

        // resolve execution context
        this.library = library;
        context = new Context(library);

        context.registerLibraryLoader(libraryLoader);

        List<Triple<String, String, String>> usingDefs = UsingHelper.getUsingUrlAndVersion(library.getUsings());

        if (usingDefs.size() > 1) {
            throw new IllegalArgumentException(
                    "Evaluation of Measure using multiple Models is not supported at this time.");
        }

        // If there are no Usings, there is probably not any place the Terminology
        // actually used so I think the assumption that at least one provider exists is
        // ok.
        TerminologyProvider terminologyProvider = null;
        if (usingDefs.size() > 0) {
            // Creates a terminology provider based on the first using statement. This
            // assumes the terminology
            // server matches the FHIR version of the CQL.
            terminologyProvider = this.providerFactory.createTerminologyProvider(usingDefs.get(0).getLeft(),
                    usingDefs.get(0).getMiddle(), source, user, pass,this.valueSetsBundle);
                    
            context.registerTerminologyProvider(terminologyProvider);
        }

        for (Triple<String, String, String> def : usingDefs) {
            // logger.info("get dataprovider");
            
            this.dataProvider = this.providerFactory.createDataProvider(def.getLeft(), def.getMiddle(),
                    terminologyProvider,this.retrieverType);
            // logger.info("set context dataprovider");
            context.registerDataProvider(def.getRight(), dataProvider);
        }

        // resolve the measurement period
        // logger.info("set interval");
        // If period start is null don't set the parameter "Measurement
        // period"
        if ((periodStart != null) && (periodEnd != null)) {
            measurementPeriod = new Interval(DateHelper.resolveRequestDate(periodStart, true), true,
                    DateHelper.resolveRequestDate(periodEnd, false), true);
            // logger.info("set measurement period");
            context.setParameter(null, "Measurement Period",
                    new Interval(DateTime.fromJavaDate((Date) measurementPeriod.getStart()), true,
                            DateTime.fromJavaDate((Date) measurementPeriod.getEnd()), true));
        }
        if (productLine != null) {
            context.setParameter(null, "Product Line", productLine);
        }
        // logger.info("seed setup complete");
        context.setExpressionCaching(true);
    }

    public String getRetrieverType() {
        return retrieverType;
    }

    public void setRetrieverType(String retrieverType) {
        this.retrieverType = retrieverType;
    }

    public Bundle getValueSetsBundle() {
        return valueSetsBundle;
    }

    public void setValueSetsBundle(Bundle valueSetsBundle) {
        this.valueSetsBundle = valueSetsBundle;
    }

    public Context getContext() {
        return context;
    }

    public void setContext(Context context) {
        this.context = context;
    }

    public Interval getMeasurementPeriod() {
        return measurementPeriod;
    }

    public void setMeasurementPeriod(Interval measurementPeriod) {
        this.measurementPeriod = measurementPeriod;
    }

    public DataProvider getDataProvider() {
        return dataProvider;
    }

    public void setDataProvider(DataProvider dataProvider) {
        this.dataProvider = dataProvider;
    }
}
