package org.opencds.cqf.dstu3.builders;

import org.opencds.cqf.common.builders.BaseBuilder;
import org.opencds.cqf.cql.engine.runtime.DateTime;
import java.util.Date;

public class JavaDateBuilder extends BaseBuilder<Date> {

    public JavaDateBuilder() {
        super(new Date());
    }

    public JavaDateBuilder buildFromDateTime(DateTime dateTime) {
        complexProperty = Date.from(dateTime.getDateTime().toInstant());
        return this;
    }
}
