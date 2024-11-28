package com.geomatys.crsservice.rest;

import com.geomatys.crsservice.service.CrsOperationService;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/crs")
public class CRSOperationController {

    private final CrsOperationService service;

    public CRSOperationController(CrsOperationService service) {
        this.service = service;
    }

    /**
     * Get Coordinate Reference System definition.
     *
     * @param source EPSG/CRS/IAU/IGNF code, WKT, or URN.
     * @param longitudeFirst Set to true to force longitude first.
     * @param format Output format, only application/json is supported.
     * @return CRS definition
     */
    @CrossOrigin
    @RequestMapping(path = "define", method = RequestMethod.GET)
    @Parameter(name = "source", example = "EPSG:3395")
    @Parameter(name = "format", schema = @Schema(type = "string", allowableValues = {"application/json"}))
    public ResponseEntity<Resource> getCRS(
                                 @RequestParam String source,
                                 @RequestParam(required = false, defaultValue = "false") boolean longitudeFirst,
                                 @RequestParam String format) {
        return getCRS(new CrsOperationService.CRSParameters(source, longitudeFirst, format));
    }

    /**
     * See the end point with request method GET.<br>
     * Parameters should be passed in json.
     */
    @CrossOrigin
    @RequestMapping(path = "define", method = RequestMethod.POST)
    public ResponseEntity<Resource> getCRS(@RequestBody CrsOperationService.CRSParameters parameters) {
        var result = service.getCRS(parameters);
        return ResponseEntity.ok()
                .contentType(result.contentType())
                .body(result.sourceCode());
    }

    /**
     * Get coordinate operation between two coordinate reference systems.
     *
     * @param source EPSG/CRS/IAU/IGNF code, WKT, or URN.
     * @param sourceLongitudeFirst Set to true to force longitude first.
     * @param target EPSG/CRS/IAU/IGNF code, WKT, or URN.
     * @param targetLongitudeFirst Set to true to force longitude first.
     * @param format Output format, only text/javascript is supported.
     * @param aoi Optional bounding box [west,south,east,north]
     * @param time Optional time
     * @return operation between the two coordinate reference systems.
     */
    @CrossOrigin
    @RequestMapping(path = "operation", method = RequestMethod.GET)
    @Parameter(name = "source", example = "EPSG:4326")
    @Parameter(name = "target", example = "EPSG:3395")
    @Parameter(name = "format", schema = @Schema(type = "string", allowableValues = {"text/javascript"}))
    public ResponseEntity<Resource> getOperation(
                                 @RequestParam String source,
                                 @RequestParam(required = false, defaultValue = "false") boolean sourceLongitudeFirst,
                                 @RequestParam String target,
                                 @RequestParam(required = false, defaultValue = "false") boolean targetLongitudeFirst,
                                 @RequestParam String format,
                                 @RequestParam(required = false) double[] aoi,
                                 @RequestParam(required = false) OffsetDateTime time) {
        return getOperation(new CrsOperationService.OperationParameters(source, sourceLongitudeFirst, target, targetLongitudeFirst, format, aoi, time));
    }

    /**
     * See the end point with request method GET.<br>
     * Parameters should be passed in json.
     */
    @CrossOrigin
    @RequestMapping(path = "operation", method = RequestMethod.POST)
    public ResponseEntity<Resource> getOperation(@RequestBody CrsOperationService.OperationParameters parameters) {
        var result = service.getOperation(parameters);
        return ResponseEntity.ok()
                .contentType(result.contentType())
                .body(result.sourceCode());
    }
}
