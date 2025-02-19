package com.geomatys.crsservice.gigs.supported.series5100;

import com.geomatys.crsservice.gigs.CRSFactories;
import org.iogp.gigs.Test51022;
import org.junit.jupiter.api.Tag;
import org.opengis.referencing.operation.CoordinateOperationFactory;
import org.opengis.util.FactoryException;

import java.net.URISyntaxException;

@Tag("gigs")
public class GIGSTest51022 extends Test51022 {

    /**
     * Creates a new test using the given factories.
     * The factories needed by this class are {@link CoordinateOperationFactory}.
     * If a requested factory is {@code null}, then the tests which depend on it will be skipped.
     *
     * <h4>Authority factory usage</h4>
     * The authority factory is used only for some test cases where the components are fetched by EPSG codes
     * instead of being built by user. Those test cases are identified by the "definition source" line in Javadoc.
     *
     */
    public GIGSTest51022() throws FactoryException, URISyntaxException {
        super(new CRSFactories());
    }

}
