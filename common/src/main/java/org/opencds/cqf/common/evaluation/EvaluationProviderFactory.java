package org.opencds.cqf.common.evaluation;

import org.opencds.cqf.cql.data.DataProvider;
import org.opencds.cqf.cql.terminology.TerminologyProvider;

// TODO: This interface is a partial duplicate of the provider factory interface
// in the cql service layer. We need another round of refactoring to consolidate that.
public interface EvaluationProviderFactory {
    public DataProvider createDataProvider(String model, String version);

    public DataProvider createDataProvider(String model, String version, String url, String user, String pass);

    public DataProvider createDataProvider(String model, String version, TerminologyProvider terminologyProvider);
    
    public DataProvider createDataProvider(String model, String version, TerminologyProvider terminologyProvider, String retrieveType);


    public TerminologyProvider createTerminologyProvider(String model, String version, String url, String user, String pass);
}