/*
 * GIGS - Geospatial Integrity of Geoscience Software
 * https://gigs.iogp.org/
 *
 * Copyright (C) 2022-2023 International Association of Oil and Gas Producers.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a
 * copy of this software and associated documentation files (the "Software"),
 * to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included
 * in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS
 * OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL
 * THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
 * DEALINGS IN THE SOFTWARE.
 */
package com.geomatys.crsservice.gigs;

import com.geomatys.crsservice.client.CRSServiceCoordinateOperationFactory;
import org.apache.sis.referencing.factory.GeodeticObjectFactory;
import org.apache.sis.referencing.operation.DefaultCoordinateOperationFactory;
import org.apache.sis.referencing.operation.transform.DefaultMathTransformFactory;
import org.iogp.gigs.Factories;
import org.iogp.gigs.internal.geoapi.PseudoEpsgFactory;
import org.iogp.gigs.internal.geoapi.Units;
import org.iogp.gigs.internal.geoapi.ValidatorContainer;

import java.net.URI;
import java.net.URISyntaxException;


/**
 * A container for various factory implementations.
 * This is used as a replacement for long list of arguments in constructors when a test may require
 * many factories for different kinds of objects (datum, coordinate system, operations, <i>etc</i>).
 *
 * <p>Implementations can create a {@code Factories} subclass and initialize all fields in their constructor.</p>
 *
 * @author  Martin Desruisseaux (Geomatys)
 * @version 1.0
 * @since   1.0
 */
public class CRSFactories extends Factories {

    public CRSFactories() throws URISyntaxException {
      copFactory = new DefaultCoordinateOperationFactory();
//        copFactory = new CRSServiceCoordinateOperationFactory(new URI("http://localhost:8080/crs/operation"));
        mtFactory = new DefaultMathTransformFactory();
        final GeodeticObjectFactory geoObjectFactory = new GeodeticObjectFactory();
        datumFactory = geoObjectFactory;
        csFactory = geoObjectFactory;
        crsFactory = geoObjectFactory;
        datumAuthorityFactory = new PseudoEpsgFactory(Units.getInstance(), datumFactory, csFactory, crsFactory, null, null, ValidatorContainer.DEFAULT);
    }

}
