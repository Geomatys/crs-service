
package com.geomatys.crsservice.client;

import com.geomatys.crsservice.AbstractIntegrationTest;
import java.net.URI;
import java.util.Arrays;
import org.apache.sis.referencing.CRS;
import org.apache.sis.referencing.crs.AbstractCRS;
import org.apache.sis.referencing.cs.AxesConvention;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.operation.CoordinateOperation;
import org.opengis.referencing.operation.MathTransform;
import org.opengis.referencing.operation.TransformException;
import org.opengis.util.FactoryException;

/**
 *
 * @author Johann Sorel (Geomatys)
 */
public class ClientTest extends AbstractIntegrationTest {

    @Test
    public void operationTest() throws Exception {

        final CRSServiceCoordinateOperationFactory factory = new CRSServiceCoordinateOperationFactory(new URI(getServerUrl()+"/crs/operation"));

        final double geogTolerance = 0.00000001;
        final double cartTolerance = 0.001;

        //Linear transform test
        testTransform(factory, "EPSG:4326", "CRS:84", new double[]{10, 20}, true, geogTolerance, geogTolerance);
        //Mercator
        testTransform(factory, "EPSG:4326", "EPSG:3395", new double[]{10, 20}, true, cartTolerance, geogTolerance);
        //EquidistantCylindrical
        testTransform(factory, "EPSG:4326", "EPSG:4087", new double[]{10, 20}, true, cartTolerance, geogTolerance);
        //PolarStereographic
        testTransform(factory, "EPSG:4326", "EPSG:3031", new double[]{60, 20}, true, geogTolerance, geogTolerance);
        //TransverseMercator
        testTransform(factory, "EPSG:4326", "EPSG:32231", new double[]{48, 2}, true, cartTolerance, geogTolerance);
        //Lambert Conic
        testTransform(factory, "EPSG:4326", "EPSG:2154", new double[]{48, 2}, true, cartTolerance, geogTolerance);
        //Geocentric
        testTransform(factory, "EPSG:4326", "EPSG:4978", new double[]{48, 2}, true, cartTolerance, geogTolerance);
        //Spherical to projection
        final String mars1 =
                "GEODCRS[\"Mars (2015) / Ocentric\",\n" +
                "  DATUM[\"Mars (2015)\",\n" +
                "    ELLIPSOID[\"Mars (2015)\", 3396190, 169.8944472236118,\n" +
                "      LENGTHUNIT[\"metre\", 1, ID[\"EPSG\", 9001]]],\n" +
                "    ANCHOR[\"Viking 1 lander : 47.95137 W\"]],\n" +
                "    PRIMEM[\"Reference Meridian\", 0,\n" +
                "      ANGLEUNIT[\"degree\", 0.0174532925199433, ID[\"EPSG\", 9122]]],\n" +
                "  CS[spherical, 2],\n" +
                "    AXIS[\"planetocentric latitude (U)\", north,\n" +
                "      ANGLEUNIT[\"degree\", 0.0174532925199433]],\n" +
                "    AXIS[\"planetocentric longitude (V)\", east,\n" +
                "      ANGLEUNIT[\"degree\", 0.0174532925199433]],\n" +
                "  ID[\"IAU\", 49902, 2015],\n" +
                "  REMARK[\"Source of IAU Coordinate systems: doi:10.1007/s10569-017-9805-5\"]]";
        final String mars2 =
                "PROJCRS[\"Mars (2015) / Ocentric / Equirectangular, clon = 0\",\n" +
                "  BASEGEODCRS[\"Mars (2015) / Ocentric\",\n" +
                "    DATUM[\"Mars (2015)\",\n" +
                "      ELLIPSOID[\"Mars (2015)\", 3396190, 169.8944472236118,\n" +
                "        LENGTHUNIT[\"metre\", 1, ID[\"EPSG\", 9001]]],\n" +
                "      ANCHOR[\"Viking 1 lander : 47.95137 W\"]],\n" +
                "      PRIMEM[\"Reference Meridian\", 0,\n" +
                "        ANGLEUNIT[\"degree\", 0.0174532925199433, ID[\"EPSG\", 9122]]],\n" +
                "    ID[\"IAU\", 49902, 2015]],\n" +
                "  CONVERSION[\"Equirectangular, clon = 0\",\n" +
                "    METHOD[\"Equidistant Cylindrical\", ID[\"EPSG\", 1028]]],\n" +
                "  CS[Cartesian, 2],\n" +
                "    AXIS[\"Easting (E)\", east,\n" +
                "      LENGTHUNIT[\"metre\", 1]],\n" +
                "    AXIS[\"Northing (N)\", north,\n" +
                "      LENGTHUNIT[\"metre\", 1]],\n" +
                "  ID[\"IAU\", 49912, 2015]]";
        testTransform(factory, mars1, mars2, new double[]{40, 120}, true, cartTolerance, geogTolerance);

        //test PassThroughTransform : Not working yet
        CoordinateReferenceSystem crs1 = CRS.compound(CRS.forCode("CRS:84"));
        CoordinateReferenceSystem crs2 = CRS.compound(CRS.forCode("CRS:84"), CRS.forCode("EPSG:5714"));
        CoordinateReferenceSystem crs3 = CRS.compound(CRS.forCode("EPSG:3395"), CRS.forCode("EPSG:5714"));

        testTransform(factory, crs2.toWKT(), crs1.toWKT(), new double[]{40,50,100}, false, geogTolerance, 0.0);
        testTransform(factory, crs2.toWKT(), crs3.toWKT(), new double[]{40,50,100}, true, cartTolerance, 0.0);
    }

    static void testTransform(ScriptingCoordinateOperationFactory factory, String source, String target, double[] coords, boolean testInverse, final double forwardTolerance, final double inverseTolerance) throws FactoryException, TransformException {
        final CoordinateReferenceSystem crsSource = parseCRS(source, false);
        final CoordinateReferenceSystem crsTarget = parseCRS(target, false);

        // Convert using JavaScript code
        final CoordinateOperation distopJS = factory.createOperation(crsSource, crsTarget);
        final MathTransform distTrsJS = distopJS.getMathTransform();
        final double[] distResJS = new double[distTrsJS.getTargetDimensions()];
        distTrsJS.transform(coords, 0, distResJS, 0, 1);

        // Convert using Python code
        final CoordinateOperation distopPy = factory.createOperation(crsSource, crsTarget, "text/x-python");
        final MathTransform distTrsPy = distopPy.getMathTransform();
        final double[] distResPy = new double[distTrsPy.getTargetDimensions()];
        distTrsPy.transform(coords, 0, distResPy, 0, 1);

        //compare with SIS
        final CoordinateOperation localOp = CRS.findOperation(crsSource, crsTarget, null);
        final MathTransform localTrs = localOp.getMathTransform();
        final double[] localRes = new double[localTrs.getTargetDimensions()];
        localTrs.transform(coords, 0, localRes, 0, 1);

        System.out.println("FROM : " + source + "\nTO   : " + target);
        System.out.println("SRC  : " + Arrays.toString(coords));
        System.out.println("SIS  : " + Arrays.toString(localRes));
        System.out.println("JS   : " + Arrays.toString(distResJS));
        System.out.println("PY   : " + Arrays.toString(distResPy) + "\n");

        Assertions.assertArrayEquals(localRes, distResJS, forwardTolerance);
        Assertions.assertArrayEquals(localRes, distResPy, forwardTolerance);

        if (testInverse) {//test inverse
            coords = localRes;

            // Convert using JavaScript code
            final CoordinateOperation rdistopJS = factory.createOperation(crsTarget, crsSource);
            final MathTransform rdistTrsJS = rdistopJS.getMathTransform();
            final double[] rdistResJS = new double[rdistTrsJS.getTargetDimensions()];
            rdistTrsJS.transform(coords, 0, rdistResJS, 0, 1);

            // Convert using Python code
            final CoordinateOperation rdistopPy = factory.createOperation(crsTarget, crsSource, "text/x-python");
            final MathTransform rdistTrsPy = rdistopPy.getMathTransform();
            final double[] rdistResPy = new double[rdistTrsPy.getTargetDimensions()];
            rdistTrsPy.transform(coords, 0, rdistResPy, 0, 1);

            //compare with SIS
            final CoordinateOperation rlocalOp = CRS.findOperation(crsTarget, crsSource, null);
            final MathTransform rlocalTrs = rlocalOp.getMathTransform();
            final double[] rlocalRes = new double[rlocalTrs.getTargetDimensions()];
            rlocalTrs.transform(coords, 0, rlocalRes, 0, 1);

            System.out.println("FROM : " + target + "\nTO   : " + source);
            System.out.println("SRC  : " + Arrays.toString(coords));
            System.out.println("SIS  : " + Arrays.toString(rlocalRes));
            System.out.println("JS   : " + Arrays.toString(rdistResJS));
            System.out.println("PY   : " + Arrays.toString(rdistResPy) + "\n");

            Assertions.assertArrayEquals(rlocalRes, rdistResJS, inverseTolerance);
            Assertions.assertArrayEquals(rlocalRes, rdistResPy, inverseTolerance);
        }

    }

    private static CoordinateReferenceSystem parseCRS(String text, boolean longFirst) throws FactoryException {
        CoordinateReferenceSystem crs;
        try {
            crs = CRS.fromWKT(text);
        } catch (FactoryException ex) {
            try {
                return CRS.forCode(text);
            } catch (FactoryException ex1) {
                ex.addSuppressed(ex1);
                throw ex;
            }
        }
        if (longFirst) {
            crs = AbstractCRS.castOrCopy(crs).forConvention(AxesConvention.DISPLAY_ORIENTED);
        }
        return crs;
    }

}
