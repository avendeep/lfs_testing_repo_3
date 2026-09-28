package com.automation.api.utils;

import io.restassured.response.Response;
import org.testng.Assert;

public class StatusCodeValidator {
    public static void validate(Response response, int expectedStatusCode) {
        Assert.assertEquals(response.getStatusCode(), expectedStatusCode, "Status code mismatch!");
    }
}
