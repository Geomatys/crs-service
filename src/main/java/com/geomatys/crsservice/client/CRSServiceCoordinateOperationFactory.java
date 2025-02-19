/*
 * Planet CRS Registry - The coordinates reference system registry for solar bodies
 * Copyright (C) 2025 - CNES (for PDSSP)
 *
 * This file is part of CRS Service.
 *
 * CRS Service is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License v3  as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * CRS Service is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License v3  for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License v3
 * along with CRS Service.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.geomatys.crsservice.client;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import org.apache.sis.metadata.iso.citation.DefaultCitation;
import org.apache.sis.referencing.factory.InvalidGeodeticParameterException;
import org.apache.sis.referencing.operation.AbstractCoordinateOperation;
import org.apache.sis.referencing.operation.DefaultConversion;
import org.apache.sis.referencing.operation.transform.AbstractMathTransform;
import org.graalvm.polyglot.proxy.ProxyArray;
import org.opengis.metadata.citation.Citation;
import org.opengis.parameter.ParameterValueGroup;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.operation.Conversion;
import org.opengis.referencing.operation.CoordinateOperation;
import org.opengis.referencing.operation.CoordinateOperationFactory;
import org.opengis.referencing.operation.Matrix;
import org.opengis.referencing.operation.OperationMethod;
import org.opengis.referencing.operation.OperationNotFoundException;
import org.opengis.referencing.operation.TransformException;
import org.opengis.util.FactoryException;

/**
 * CoordinateOperationFactory backed by a distance server.
 *
 * @author Johann Sorel (Geomatys)
 */
public final class CRSServiceCoordinateOperationFactory implements CoordinateOperationFactory{

    private final URI serviceURL;

    public CRSServiceCoordinateOperationFactory(URI serviceURL) {
        this.serviceURL = serviceURL;
        System.setProperty("polyglot.engine.WarnInterpreterOnly", "false");
    }

    @Override
    public CoordinateOperation createOperation(CoordinateReferenceSystem crs1, CoordinateReferenceSystem crs2) throws OperationNotFoundException, FactoryException {

        final String crs1Txt = URLEncoder.encode(crs1.toWKT(), StandardCharsets.UTF_8);
        final String crs2Txt = URLEncoder.encode(crs2.toWKT(), StandardCharsets.UTF_8);
        final String query = serviceURL.toString() + "?source=" + crs1Txt + "&target=" + crs2Txt + "&format=text/javascript";

        try {
            final String code = getText(query);
            final ScriptEngineManager manager = new ScriptEngineManager();
            final ScriptEngine engine = manager.getEngineByName("js");
            Object res = engine.eval("const operationClass = ("+code+");\noperation = new operationClass();");
            final JSMathTransform trs = new JSMathTransform(crs1.getCoordinateSystem().getDimension(), crs2.getCoordinateSystem().getDimension(), (Invocable) engine);

            final Map properties = new HashMap();
            properties.put("name", "javascript operation");
            return new AbstractCoordinateOperation(properties, crs1, crs2, null, trs);
        } catch (Exception ex) {
            throw new FactoryException(ex.getMessage(), ex);
        }
    }

    @Override
    public CoordinateOperation createOperation(CoordinateReferenceSystem crs, CoordinateReferenceSystem crs1, OperationMethod om) throws OperationNotFoundException, FactoryException {
        throw new UnsupportedOperationException("Not supported.");
    }

    @Override
    public CoordinateOperation createConcatenatedOperation(Map<String, ?> map, CoordinateOperation... cos) throws FactoryException {
        throw new UnsupportedOperationException("Not supported.");
    }

    @Override
    public Conversion createDefiningConversion(Map<String, ?> map, OperationMethod om, ParameterValueGroup pvg) throws FactoryException {
        final Conversion conversion;
        try {
            conversion = new DefaultConversion(map, om, null, pvg);
        } catch (IllegalArgumentException exception) {
            throw new InvalidGeodeticParameterException(exception.getLocalizedMessage(), exception);
        }
        // We do no invoke unique(conversion) because defining conversions are usually short-lived objects.
        return conversion;
    }

    @Override
    public Citation getVendor() {
        return new DefaultCitation(serviceURL.toString());
    }

    private static String getText(String url) throws URISyntaxException, IOException, InterruptedException {

        final HttpRequest request = HttpRequest.newBuilder().uri(new URI(url)).GET().build();

        try (final HttpClient client = HttpClient.newBuilder().build()) {
            final HttpResponse<String> response = client.send(request, BodyHandlers.ofString());
            return response.body();
        }
    }

    private static final class JSMathTransform extends AbstractMathTransform {

        private final int sourceDim;
        private final int targetDim;
        private final Invocable engine;

        private JSMathTransform(int sourceDim, int targetDim, Invocable engine) {
            this.sourceDim = sourceDim;
            this.targetDim = targetDim;
            this.engine = engine;
        }

        @Override
        public int getSourceDimensions() {
            return sourceDim;
        }

        @Override
        public int getTargetDimensions() {
            return targetDim;
        }

        @Override
        public Matrix transform(double[] src, int so, double[] dst, int doffset, boolean derivate) throws TransformException {
            if (derivate) {
                throw new TransformException("Derivate not supported");
            }

            try {
                final Object[] array = new Object[sourceDim];
                for (int i = 0; i < sourceDim; i++) {
                    array[i] = src[so + i];
                }

                final Object jsOperation = ((ScriptEngine) engine).eval("operation");
                final List result = (List) engine.invokeMethod(jsOperation, "transform", ProxyArray.fromArray(array));
                for (int i = 0; i < targetDim; i++) {
                    dst[doffset + i] = ((Number)result.get(i)).doubleValue();
                }
            } catch (ScriptException | NoSuchMethodException ex) {
                throw new TransformException(ex.getMessage(), ex);
            }
            return null;
        }

    }
}
