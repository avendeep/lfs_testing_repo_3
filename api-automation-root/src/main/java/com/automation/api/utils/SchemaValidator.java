package com.automation.api.utils;

import io.restassured.response.Response;
import io.restassured.module.jsv.JsonSchemaValidator;

public class SchemaValidator {
    public static void validate(Response response, String schemaPath) {
        response.then().body(JsonSchemaValidator.matchesJsonSchemaInClasspath("schemas/" + schemaPath));
    }
}
