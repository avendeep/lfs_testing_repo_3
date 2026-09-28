package com.automation.api.utils;

import io.restassured.RestAssured;
import io.restassured.specification.RequestSpecification;

public class ApiRequestBuilder {
    public static RequestSpecification buildRequestSpec() {
        // RestAssured.given() (not "new RequestSpecBuilder().build()") -- a spec built purely
        // via RequestSpecBuilder is a standalone RequestSpecificationImpl that isn't wired to
        // RestAssured's internal response-spec/assertion state, and throws a NullPointerException
        // ("Cannot get property 'assertionClosure' on null object") if a step later sends a
        // request straight off it. given() returns a spec that's already fully wired, so every
        // step in ApiSteps.java can keep mutating and finally send off this SAME object with no
        // copy/merge step in between (RestAssured.given(spec) elsewhere was silently dropping
        // the request body on that merge -- see ApiSteps.iSendARequestTo).
        RequestSpecification spec = RestAssured.given();
        spec.baseUri(ApiConfigManager.getBaseUri());

        String apiKey = System.getenv("API_KEY");
        if (apiKey != null) {
            spec.header("x-api-key", apiKey);
        }

        return spec;
    }
}
