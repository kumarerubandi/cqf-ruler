package org.opencds.cqf.qdm.providers;

import lombok.Data;
import org.opencds.cqf.cql.data.CompositeDataProvider;
import org.opencds.cqf.cql.runtime.Code;
import org.opencds.cqf.cql.runtime.DateTime;
import org.opencds.cqf.cql.runtime.Interval;
import org.opencds.cqf.cql.runtime.TemporalHelper;
import org.opencds.cqf.cql.terminology.TerminologyProvider;
import org.opencds.cqf.cql.terminology.ValueSetInfo;
import org.opencds.cqf.qdm.fivepoint4.QdmContext;
import org.opencds.cqf.qdm.fivepoint4.model.*;
import org.opencds.cqf.qdm.fivepoint4.repository.BaseRepository;
import org.opencds.cqf.qdm.fivepoint4.repository.PatientRepository;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Data
public class Qdm54DataProvider extends CompositeDataProvider
{

	public Qdm54DataProvider() {
		super(new Qdm54ModelResolver(), new Qdm54RetrieveProvider());
	}

    private TerminologyProvider terminologyProvider;


    private String packageName = "org.opencds.cqf.qdm.fivepoint4.model";
    @Override
    public String getPackageName()
    {
        return packageName;
    }

    @Override
    public void setPackageName(String packageName)
    {
        this.packageName = packageName;
    }

    @Override
    public Object resolvePath(Object o, String s)
    {
        Method method;
        Object result;
        String methodName = String.format("get%s", s.substring(0, 1).toUpperCase() + s.substring(1));
        try
        {
            method = o.getClass().getMethod(methodName);
            result = method.invoke(o);
        }
        catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e)
        {
            e.printStackTrace();
            throw new RuntimeException(String.format("Unable to resolve method: %s on type: %s", methodName, o.getClass().getName()));
        }

        if (s.toLowerCase().endsWith("datetime")
                && result != null
                && result instanceof String
                && !((String)result).isEmpty())
        {
            return new DateTime((String) result, TemporalHelper.getDefaultZoneOffset());
        }

        if (result instanceof DateTimeInterval)
        {
            String start = ((DateTimeInterval) result).getStart();
            String end = ((DateTimeInterval) result).getEnd();
            return new Interval(
                    start != null && !start.isEmpty() ? new DateTime(start, TemporalHelper.getDefaultZoneOffset()) : null, true,
                    end != null && !end.isEmpty() ? new DateTime(end, TemporalHelper.getDefaultZoneOffset()) : null, true
            );
        }

        if (result instanceof org.opencds.cqf.qdm.fivepoint4.model.Code)
        {
            return new Code()
                    .withCode(((org.opencds.cqf.qdm.fivepoint4.model.Code) result).getCode())
                    .withSystem(((org.opencds.cqf.qdm.fivepoint4.model.Code) result).getSystem())
                    .withDisplay(((org.opencds.cqf.qdm.fivepoint4.model.Code) result).getDisplay())
                    .withVersion(((org.opencds.cqf.qdm.fivepoint4.model.Code) result).getVersion());
        }

        if (result instanceof Quantity)
        {
            return new org.opencds.cqf.cql.runtime.Quantity()
                    .withValue(((Quantity) result).getValue())
                    .withUnit(((Quantity) result).getUnit());
        }

        if (result instanceof QuantityInterval)
        {
            return new Interval(
                    new org.opencds.cqf.cql.runtime.Quantity()
                            .withValue(((QuantityInterval) result).getStart().getValue())
                            .withUnit(((QuantityInterval) result).getStart().getUnit()),
                    true,
                    new org.opencds.cqf.cql.runtime.Quantity()
                            .withValue(((QuantityInterval) result).getEnd().getValue())
                            .withUnit(((QuantityInterval) result).getEnd().getUnit()),
                    true
            );
        }

        return result;
    }

    @Override
    public Class resolveType(String s)
    {
        return null;
    }

    @Override
    public Class resolveType(Object o)
    {
        return null;
    }

    @Override
    public Object createInstance(String s)
    {
        return null;
    }

    @Override
    public void setValue(Object o, String s, Object o1)
    {

    }

    @Override
    public Boolean objectEqual(Object o, Object o1) {
        return o.equals(o1);
    }

    @Override
    public Boolean objectEquivalent(Object o, Object o1) {
        return o.equals(o1);
    }
}
