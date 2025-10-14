package org.brm.apiserver.controller.rest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.brm.apiserver.model.SimpleResponse;
import org.brm.apiserver.misc.Utils;
import org.brm.apiserver.misc.BlockingSimulator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
// import org.springframework.web.client.RestTemplate;

@RestController
@RequestMapping(SimpleRestController.PATH)
public class SimpleRestController {

    // private final RestTemplate restTemplate;
    // instantiate slf4j logger
    private static final Logger log = LoggerFactory.getLogger(SimpleRestController.class);

    public static final String PATH_SIMPLE = "/simple";
    public static final String PATH_BLOCKING = "/blocking";
    public static final String PATH_PROXY = "/proxy";
    public static final String PATH = "/rest";
    public static final String SCHEME_HTTP = "http://";

    @Value("${nom.aob.sbbench.logstring:#{null}}")
    private String logString;

    private final BlockingSimulator blockingSimulator;

    public SimpleRestController(BlockingSimulator blockingSimulator) {
        this.blockingSimulator = blockingSimulator;
        // this.restTemplate = restTemplate;
    }

    @GetMapping(PATH_SIMPLE)
    public ResponseEntity<SimpleResponse> simpleResponse(
            @RequestHeader(value = "x-b3-traceid", required = false) String traceId) {

//        log.info("in simpleResponse: " + traceId);
        if (logString != null) {
            log.info("in simpleResponse. logString = {}.",logString);
        }

        SimpleResponse simpleResponse = Utils.newSimpleResponse(PATH_SIMPLE);
//                SimpleResponse.builder()
//                .hostString(Utils.getHostname())
//                .pathString(PATH_SIMPLE)
//                .timeString(Utils.getCurrentTimeString())
//                .randomInteger(Utils.newRandomInt())
//                .threadID(Thread.currentThread().getId())
//                .build();

        return new ResponseEntity<>(simpleResponse, HttpStatus.OK);
    }

    @GetMapping(PATH_BLOCKING)
    public ResponseEntity<SimpleResponse> blockingResponse(
            @RequestHeader(value = "x-b3-traceid", required = false) String traceId,
            @RequestParam(value = "operation-type", required = false) String operationType,
            @RequestParam(value = "min-block-period-ms", required = false) Integer minBlockPeriodMs,
            @RequestParam(value = "max-block-period-ms", required = false) Integer maxBlockPeriodMs) {

        if (logString != null) {
            log.info("in blockingResponse. logString = {}.", logString);
        }

        // Perform the blocking operation with optional parameters
        blockingSimulator.performBlockingOperation(operationType, minBlockPeriodMs, maxBlockPeriodMs);

        SimpleResponse simpleResponse = Utils.newSimpleResponse(PATH_BLOCKING);

        return new ResponseEntity<>(simpleResponse, HttpStatus.OK);
    }

//     @GetMapping(PATH_PROXY+"/{address}/{port}")
//     public ResponseEntity<SimpleResponse> simpleProxy(
//             @RequestHeader(value = "x-b3-traceid", required = false) String traceId,
//             @PathVariable("address") String address,
//             @PathVariable("port") Integer port) {

// //        log.info("in simpleProxy: " + traceId);
//         if (logString != null) {
//             log.info("in simpleProxy. logString = {}.", logString);
//         }

//         StringBuilder sb = new StringBuilder(SCHEME_HTTP);
//         sb.append(address).append(":").append(port).append(PATH).append(PATH_SIMPLE);
//         String resourceUrl = sb.toString();

//         ResponseEntity<SimpleResponse> response
//                 = restTemplate.getForEntity(resourceUrl, SimpleResponse.class);

//         if (response.getStatusCode().is2xxSuccessful()) {
//             SimpleResponse simpleResponse = response.getBody();
//             simpleResponse.setPathString(PATH_PROXY + "/" + address + "/" + port);
//             return new ResponseEntity<>(simpleResponse, response.getStatusCode());
//         }

//         return response;
//     }

}