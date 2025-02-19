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
package com.geomatys.crsservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.sis.referencing.CRS;
import org.apache.sis.referencing.crs.AbstractCRS;
import org.apache.sis.referencing.cs.AxesConvention;
import org.apache.sis.referencing.operation.transform.MathTransforms;
import org.apache.sis.referencing.ExportableTransform;
import org.opengis.geometry.Envelope;
import org.opengis.metadata.extent.GeographicBoundingBox;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.operation.CoordinateOperation;
import org.opengis.referencing.operation.MathTransform;
import org.opengis.referencing.operation.NoninvertibleTransformException;
import org.opengis.util.FactoryException;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

/**
 *
 * @author Johann Sorel (Geomatys)
 */
@Service
public class DefaultCrsOperationService implements CrsOperationService {

    private static final String FORMAT_JAVASCRIPT = "text/javascript";
    private static final String FORMAT_PYTHON = "text/x-python";
    private static final String FORMAT_JSON = "application/json";
    private static final String FORMAT_WKT ="application/wkt";

    @Override
    public SourceCode getCRS(CRSParameters request) throws IllegalArgumentException, UnsupportedOperationException {

        final CoordinateReferenceSystem crs;
        try {
            crs = parseCRS(request.source(), request.longitudeFirst());
        } catch (FactoryException ex) {
            throw new IllegalArgumentException("Source CRS unsupported : " + request.source(), ex);
        }

        final String format = request.format();
        if (FORMAT_JSON.equals(format)) {
            final Map<String,Object> map = new LinkedHashMap<>();
            map.put("code", request.source());

            final Envelope domainOfValidity = CRS.getDomainOfValidity(crs);
            if (domainOfValidity != null) {
                map.put("domainOfValidity", new double[]{domainOfValidity.getMinimum(0), domainOfValidity.getMinimum(1), domainOfValidity.getMaximum(0), domainOfValidity.getMaximum(1)});
            }
            map.put("units", crs.getCoordinateSystem().getAxis(0).getUnit().getSymbol());

            final List<String> axisDirection = new ArrayList();
            for (int i = 0; i < crs.getCoordinateSystem().getDimension(); i++) {
                axisDirection.add(crs.getCoordinateSystem().getAxis(i).getDirection().identifier());
            }
            map.put("axisDirection", axisDirection);

            final ObjectMapper mapper = new ObjectMapper();
            final String json;
            try {
                json = mapper.writeValueAsString(map);
            } catch (JsonProcessingException ex) {
                throw new RuntimeException(ex.getMessage(), ex);
            }

            return new SourceCode(
                MediaType.parseMediaType(FORMAT_JSON + "; charset=utf-8"),
                new ByteArrayResource(json.getBytes(StandardCharsets.UTF_8))
            );

        } else if (FORMAT_WKT.equals(format)) {
            throw new UnsupportedOperationException("TODO");
        } else {
            throw new IllegalArgumentException("Format not supported " + format);
        }

    }

    @Override
    public SourceCode getOperation(OperationParameters request) throws IllegalArgumentException, UnsupportedOperationException {

        final CoordinateReferenceSystem crs1;
        final CoordinateReferenceSystem crs2;
        try {
            crs1 = parseCRS(request.source(), request.sourceLongFirst());
        } catch (FactoryException ex) {
            throw new IllegalArgumentException("Source CRS unsupported : " + request.source(), ex);
        }
        try {
            crs2 = parseCRS(request.target(), request.targetLongFirst());
        } catch (FactoryException ex) {
            throw new IllegalArgumentException("Target CRS unsupported : " + request.target(), ex);
        }

        final double[] aoi = request.aoi();
        final GeographicBoundingBox ggb = (aoi == null) ? null : null;
        final CoordinateOperation operation;
        try {
            operation = CRS.findOperation(crs1, crs2, ggb);
        } catch (FactoryException ex) {
            throw new UnsupportedOperationException("No coordinate operation found between " + request.source() + " and " + request.target(), ex);
        }
        final MathTransform trs = operation.getMathTransform();
        MathTransform inverseTrs = null;
        try {
            inverseTrs = trs.inverse();
        } catch (NoninvertibleTransformException ex) {
            //no nothing
        }

        final double linearAccuracy = CRS.getLinearAccuracy(operation);
        final GeographicBoundingBox gbb = CRS.getGeographicBoundingBox(operation);
        final Envelope domainOfValidity = CRS.getDomainOfValidity(crs2);

        final String format = request.format();
        if (FORMAT_JAVASCRIPT.equals(format)) {
            final String code = toJavaScript(trs, inverseTrs, linearAccuracy, gbb, domainOfValidity);
            return new SourceCode(
                MediaType.parseMediaType(FORMAT_JAVASCRIPT + "; charset=utf-8"),
                new ByteArrayResource(code.getBytes(StandardCharsets.UTF_8))
            );
        } else if (FORMAT_PYTHON.equals(format)) {
            final String code = toPython(trs, inverseTrs, linearAccuracy, gbb, domainOfValidity);
            return new SourceCode(
                MediaType.parseMediaType(FORMAT_JAVASCRIPT + "; charset=utf-8"),
                new ByteArrayResource(code.getBytes(StandardCharsets.UTF_8))
            );
        } else {
            throw new IllegalArgumentException("Format not supported " + format);
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

    /**
     * Generate Javascript
     */
    private static String toJavaScript(MathTransform trs, MathTransform invtrs, double accuracy, GeographicBoundingBox operationGeographicBoundingBox, Envelope targetCrsDomainOfValidity) {
        final StringBuilder sb = new StringBuilder();

        sb.append(
                "/* Code generated by Apache SIS. https://sis.apache.org \n" +
                " *\n" +
                " * Licensed to the Apache Software Foundation (ASF) under one or more\n" +
                " * contributor license agreements.  See the NOTICE file distributed with\n" +
                " * this work for additional information regarding copyright ownership.\n" +
                " * The ASF licenses this file to You under the Apache License, Version 2.0\n" +
                " * (the \"License\"); you may not use this file except in compliance with\n" +
                " * the License.  You may obtain a copy of the License at\n" +
                " *\n" +
                " *     http://www.apache.org/licenses/LICENSE-2.0\n" +
                " *\n" +
                " * Unless required by applicable law or agreed to in writing, software\n" +
                " * distributed under the License is distributed on an \"AS IS\" BASIS,\n" +
                " * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.\n" +
                " * See the License for the specific language governing permissions and\n" +
                " * limitations under the License.\n" +
                " */\n");

        sb.append("class Operation {\n");

        sb.append("/*\n * The valid geographic area for the given coordinate operation (as an array [west, south, east, north]), or undefined\n */\n");
        if (operationGeographicBoundingBox != null) {
            sb.append("operationGeographicBoundingBox =");
            sb.append(" [")
                    .append(operationGeographicBoundingBox.getWestBoundLongitude()).append(", ")
                    .append(operationGeographicBoundingBox.getSouthBoundLatitude()).append(", ")
                    .append(operationGeographicBoundingBox.getEastBoundLongitude()).append(", ")
                    .append(operationGeographicBoundingBox.getNorthBoundLatitude())
                    .append("]");
            sb.append(";");
            sb.append("\n\n");
        } else {
            sb.append("operationGeographicBoundingBox = undefined;");
            sb.append("\n\n");
        }

        sb.append("/*\n * The target coordinate reference system domain of validity, (as an array [minX, minY, maxX, maxY]), or undefined\n */\n");
        if (targetCrsDomainOfValidity != null) {
            sb.append("domainOfValidity =");
            sb.append(" [")
                    .append(targetCrsDomainOfValidity.getMinimum(0)).append(", ")
                    .append(targetCrsDomainOfValidity.getMinimum(1)).append(", ")
                    .append(targetCrsDomainOfValidity.getMaximum(0)).append(", ")
                    .append(targetCrsDomainOfValidity.getMaximum(1))
                    .append("]");
            sb.append(";");
            sb.append("\n\n");
        } else {
            sb.append("domainOfValidity = undefined;");
            sb.append("\n\n");
        }

        sb.append("/*\n * Positional accuracy estimation in metres for the given operation, or NaN if unknown.\n */\n");
        sb.append("accuracy = ");
        sb.append(Double.toString(accuracy));
        sb.append(";");
        sb.append("\n\n");

        {
            sb.append("/*\n * The mathematical formula to transform coordinates\n */\n");
            final String jsobj = toJavaScriptObject(trs);
            sb.append("#forward = ").append(jsobj).append(";\n");
            sb.append("transform = (src) => {\n\treturn this.#forward.transform(src);\n\t};\n");

        }

        {
            sb.append("/*\n * The mathematical formula to inverse transform coordinates, can be undefined.\n */\n");
            if (invtrs != null) {
                final String jsobj = toJavaScriptObject(invtrs);
                sb.append("#inverse = ").append(jsobj).append(";\n");
                sb.append("inverseTransform = (src) => {\n\treturn this.#inverse.transform(src);\n\t};\n");
            } else {
                sb.append("const inverseTransform = undefined;");
                sb.append("\n\n");
            }
        }

        sb.append("}");

        return sb.toString();
    }

    private static String toJavaScriptObject(MathTransform trs) {

        final List<MathTransform> steps = decompose(trs);

        if (steps.size() == 1) {
            final MathTransform step = steps.get(0);
            if (step instanceof ExportableTransform exp) {
                final String code;
                try {
                    code = exp.toECMAScript();
                } catch (UnsupportedOperationException ex) {
                    return "TODO " + step.getClass().getName();
                }

                return code;
            } else {
                return "TODO " + step.getClass().getName();
            }
        } else {
            final StringBuilder sb = new StringBuilder();
            final StringBuilder trsSb = new StringBuilder();
            sb.append("{\n");
            trsSb.append("\ttransform : function(src) {\n");
            trsSb.append("\t\tlet dst;\n");

            for (int i = 0, n = steps.size(); i < n; i++) {
                final MathTransform step = steps.get(i);
                final String stepObj = toJavaScriptObject(step);
                sb.append("\t_step").append(i).append(" : ").append(stepObj.replaceAll("\n", "\n\t")).append(",\n");
                //dst becomes src for next step
                trsSb.append("\t\tdst = src = this._step").append(i).append(".transform(src);\n");
            }

            trsSb.append("\t\treturn dst;\n\t}\n");

            sb.append(trsSb.toString());
            sb.append("}");
            return sb.toString();
        }
    }

    /**
     * Generate Python
     */
    private static String toPython(MathTransform trs, MathTransform invtrs, double accuracy, GeographicBoundingBox operationGeographicBoundingBox, Envelope targetCrsDomainOfValidity) {
        final StringBuilder sb = new StringBuilder();

        sb.append(
                "# Code generated by Apache SIS. https://sis.apache.org \n" +
                "#\n" +
                "# Licensed to the Apache Software Foundation (ASF) under one or more\n" +
                "# contributor license agreements.  See the NOTICE file distributed with\n" +
                "# this work for additional information regarding copyright ownership.\n" +
                "# The ASF licenses this file to You under the Apache License, Version 2.0\n" +
                "# (the \"License\"); you may not use this file except in compliance with\n" +
                "# the License.  You may obtain a copy of the License at\n" +
                "#\n" +
                "#     http://www.apache.org/licenses/LICENSE-2.0\n" +
                "#\n" +
                "# Unless required by applicable law or agreed to in writing, software\n" +
                "# distributed under the License is distributed on an \"AS IS\" BASIS,\n" +
                "# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.\n" +
                "# See the License for the specific language governing permissions and\n" +
                "# limitations under the License.\n" +
                "#\n");

        sb.append("class Operation:\n");

        sb.append("  #\n  # The valid geographic area for the given coordinate operation (as an array [west, south, east, north]), or undefined\n  #\n");
        if (operationGeographicBoundingBox != null) {
            sb.append("  operationGeographicBoundingBox =");
            sb.append(" [")
                    .append(operationGeographicBoundingBox.getWestBoundLongitude()).append(", ")
                    .append(operationGeographicBoundingBox.getSouthBoundLatitude()).append(", ")
                    .append(operationGeographicBoundingBox.getEastBoundLongitude()).append(", ")
                    .append(operationGeographicBoundingBox.getNorthBoundLatitude())
                    .append("]");
            sb.append("\n\n");
        } else {
            sb.append("  operationGeographicBoundingBox = None");
            sb.append("\n\n");
        }

        sb.append("  #\n  # The target coordinate reference system domain of validity, (as an array [minX, minY, maxX, maxY]), or undefined\n  #\n");
        if (targetCrsDomainOfValidity != null) {
            sb.append("  domainOfValidity =");
            sb.append(" [")
                    .append(targetCrsDomainOfValidity.getMinimum(0)).append(", ")
                    .append(targetCrsDomainOfValidity.getMinimum(1)).append(", ")
                    .append(targetCrsDomainOfValidity.getMaximum(0)).append(", ")
                    .append(targetCrsDomainOfValidity.getMaximum(1))
                    .append("]");
            sb.append("\n\n");
        } else {
            sb.append("  domainOfValidity = None");
            sb.append("\n\n");
        }

        sb.append("  #\n  # Positional accuracy estimation in metres for the given operation, or NaN if unknown.\n  #\n");
        sb.append("  accuracy = ");
        sb.append(Double.toString(accuracy));
        sb.append("\n\n");

        {
            sb.append("  #\n  # The mathematical formula to transform coordinates\n  #\n");
            final String pyClass = toPythonClass(trs);
            sb.append("  class _Forward: ").append(pyClass).append("\n");
            sb.append("  def transform(self,src): \n    return self._Forward().transform(src)\n\n");

        }

        {
            sb.append("  #\n  # The mathematical formula to inverse transform coordinates, can be undefined.\n  #\n");
            if (invtrs != null) {
                final String pyclass = toPythonClass(invtrs);
                sb.append("  class _Inverse: ").append(pyclass).append("\n");
                sb.append("  def inverseTransform(self,src): \n    return self._Inverse().transform(src)\n\n");
            } else {
                sb.append("  inverseTransform = None");
                sb.append("\n\n");
            }
        }

        return sb.toString();
    }

    private static String toPythonClass(MathTransform trs) {

        String ecmaCode = toJavaScriptObject(trs);
        ecmaCode = ecmaCode.replace("\n", "\n\t");

        String pythonCode = ecmaCode;

        //======= elements whatever the depth =================
        //remove long comments
        pythonCode = pythonCode.replaceAll("\\/\\*[\\S\\s]*?\\*\\/", "");
        //remove object starting {
        pythonCode = pythonCode.replaceAll("\\A\\{", "");
        //remove object ending }
        pythonCode = pythonCode.replaceAll("\\}\\Z", "");
        //remove variable creation
        pythonCode = pythonCode.replaceAll("let ", "");
        pythonCode = pythonCode.replaceAll("const ", "");
        //replace tabs by 2 spaces
        pythonCode = pythonCode.replace("\t", "  ");
        //replace all this by self
        pythonCode = pythonCode.replace("this.", "self.");
        //replace short comments
        pythonCode = pythonCode.replaceAll("\\/\\/", "#");
        //replace ; endline
        pythonCode = pythonCode.replaceAll("; *\n", "\n");

        {//replace objects
            final Pattern objPattern = Pattern.compile("( +)(\\S+)( +: +)\\{");
            final Matcher m = objPattern.matcher(pythonCode);
            final StringBuilder sb = new StringBuilder();
            int groupEnd = 0;
            while (m.find()) {
                final int depth = m.group(1).length();
                final String objName = m.group(2);
                sb.append(pythonCode.substring(groupEnd, m.start()));

                for (int i = 0; i < depth; i++) sb.append(' ');
                sb.append(objName).append(" = ").append(objName).append("C()\n");

                for (int i = 0; i < depth; i++) sb.append(' ');
                sb.append("class ").append(objName).append("C :");
                groupEnd = m.end();
            }
            sb.append(pythonCode.substring(groupEnd));
            pythonCode = sb.toString();
        }

        //remove object,function,control flow { and }
        pythonCode = pythonCode.replaceAll("\\{|\\},?", "");
        {//replace functions
            final Pattern fctPattern = Pattern.compile("(?<=\\n)( +)(\\S+)( *: *)(function *)(\\()(.*)(\\))");
            final Matcher m = fctPattern.matcher(pythonCode);
            final StringBuilder sb = new StringBuilder();
            int groupEnd = 0;
            while (m.find()) {
                final String before = m.group(1);
                final String fctName = m.group(2);
                final String fctParams = m.group(6);
                sb.append(pythonCode.substring(groupEnd, m.start()));
                sb.append(before).append("def ").append(fctName).append("(self,").append(fctParams).append("):");
                groupEnd = m.end();
            }
            sb.append(pythonCode.substring(groupEnd));
            pythonCode = sb.toString();
        }

        {//replace object properties
            final Pattern propPattern = Pattern.compile("(?<=\\n)( +)(\\S+)( *: *)(\\S+)( *, *(?=\\n))");
            final Matcher m = propPattern.matcher(pythonCode);
            final StringBuilder sb = new StringBuilder();
            int groupEnd = 0;
            while (m.find()) {
                final String before = m.group(1);
                final String propName = m.group(2);
                final String propValue = m.group(4);
                sb.append(pythonCode.substring(groupEnd, m.start()));
                sb.append(before).append(propName).append(" = ").append(propValue);
                groupEnd = m.end();
            }
            sb.append(pythonCode.substring(groupEnd));
            pythonCode = sb.toString();
        }
        {//replace array creation
            final Pattern arrayPattern = Pattern.compile("(new Array\\()(.*)(\\))");
            final Matcher m = arrayPattern.matcher(pythonCode);
            final StringBuilder sb = new StringBuilder();
            int groupEnd = 0;
            while (m.find()) {
                final String arraySize = m.group(2);
                sb.append(pythonCode.substring(groupEnd, m.start()));
                sb.append("[None] * ").append(arraySize);
                groupEnd = m.end();
            }
            sb.append(pythonCode.substring(groupEnd));
            pythonCode = sb.toString();
        }
        {//replace if/else
//            pythonCode = pythonCode.replaceAll("else if", "elif");
//            pythonCode = pythonCode.replaceAll("else , "else:");
        }



        return pythonCode;
    }

    private static List<MathTransform> decompose(MathTransform trs) {
        final List<MathTransform> steps = MathTransforms.getSteps(trs);

        if (steps.size() == 1) {
            return steps;
        }

        final List<MathTransform> decompose = new ArrayList<>();
        for (MathTransform step : steps) {
            decompose.addAll(decompose(step));
        }
        return decompose;
    }

}
