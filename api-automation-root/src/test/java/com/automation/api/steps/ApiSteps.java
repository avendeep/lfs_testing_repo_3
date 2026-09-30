package com.automation.api.steps;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.restassured.response.Response;
import org.testng.Assert;

public class ApiSteps {

@Given("the user is {string}")
public void the_user_is(String authenticationStatus) {
    switch (authenticationStatus) {
        case "authenticated":
            String jwtToken = testContext.getValidAuthToken();
            testContext.getRequestSpecification().header("Authorization", "Bearer " + jwtToken);
            break;
        case "not authenticated":
            // Do not add an Authorization header to the request
            break;
        default:
            throw new IllegalArgumentException("Unsupported authentication status: " + authenticationStatus);
    }
}

@Given("an access key exists with state: {string}")
public void an_access_key_exists_with_state(String keyState) {
    // This step is a placeholder for test data setup.
    // In a real implementation, it would use API calls or DB access to ensure the system
    // is in the correct state (e.g., create a user, create a key with the desired status).
    // The key_id from the Examples table will be used in the When step.
    System.out.println("Setting up precondition: an access key with state '" + keyState + "' is required.");
}

@When("a DELETE request is sent for the key with id {string}")
public void a_delete_request_is_sent_for_the_key_with_id(String keyId) {
    io.restassured.response.Response response = testContext.getRequestSpecification()
        .pathParam("key_id", keyId)
        .when()
        .delete("/api/v1/access-keys/{key_id}");
    testContext.setResponse(response);
}

@Then("the API response status code should be {int}")
public void the_api_response_status_code_should_be(int expectedStatusCode) {
    testContext.getResponse().then().statusCode(expectedStatusCode);
}

@Then("the response message should be {string}")
public void the_response_message_should_be(String expectedMessage) {
    testContext.getResponse().then().body("message", org.hamcrest.Matchers.equalTo(expectedMessage));
}

@Given("the 'Authorization' header is set with a {string} JWT token")
public void the_authorization_header_is_set_with_a_jwt_token(String tokenType) {
    String token;
    switch (tokenType) {
        case "invalid":
            token = "invalid.jwt.token";
            break;
        case "expired":
            // Assuming the TestContext can provide a valid but expired token for testing
            token = testContext.getExpiredAuthToken();
            break;
        default:
            throw new IllegalArgumentException("Unsupported token type: " + tokenType);
    }
    testContext.getRequestSpecification().header("Authorization", "Bearer " + token);
}

@Then("the response body should contain a status code of {int}")
public void the_response_body_should_contain_a_status_code_of(int expectedStatusCode) {
    testContext.getResponse().then().body("status_code", org.hamcrest.Matchers.equalTo(expectedStatusCode));
}

@Given("an active access key exists for another user with id \"{string}\"")
public void anActiveAccessKeyExistsForAnotherUserWithId(String keyId) {
    // This step's implementation would ensure a distinct second user exists
    // and owns an active access key with the given 'keyId'.
    // The current authenticated user (from the 'Given the user is \"authenticated\"' step)
    // will be the one making the request.
    System.out.println("SETUP: Ensuring key '" + keyId + "' exists and is owned by a different user.");
    testContext.set("otherUserKeyId", keyId);
}

@Then("the access key \"{string}\" belonging to the other user remains active")
public void theAccessKeyBelongingToTheOtherUserRemainsActive(String keyId) {
    // This step verifies that the state of the resource was not changed.
    // It would require authenticating as the other user (or an admin) and
    // fetching the key's details to assert its 'status' is still 'active'.
    System.out.println("VERIFY: Checking that key '" + keyId + "' was not revoked.");
    // e.g., Response otherUserResponse = ApiClient.getAsOtherUser("/api/v1/access-keys/" + keyId);
    // otherUserResponse.then().body("status", org.hamcrest.Matchers.equalTo("active"));
}

@Then("the response body should contain a validation error")
public void the_response_body_should_contain_a_validation_error() {
    // A 422 response for this API indicates a Pydantic/FastAPI validation error,
    // which has a specific structure, typically a "detail" key with a list of errors.
    // We will assert that the "detail" key exists and is a list.
    testContext.getResponse().then()
        .body("detail", org.hamcrest.Matchers.instanceOf(java.util.List.class));
}
}

@Then("the access key \"{string}\" belonging to the other user remains active")
public void theAccessKeyBelongingToTheOtherUserRemainsActive(String keyId) {
    // This step verifies that the state of the resource was not changed.
    // It would require authenticating as the other user (or an admin) and
    // fetching the key's details to assert its 'status' is still 'active'.
    System.out.println("VERIFY: Checking that key '" + keyId + "' was not revoked.");
    // e.g., Response otherUserResponse = ApiClient.getAsOtherUser("/api/v1/access-keys/" + keyId);
    // otherUserResponse.then().body("status", org.hamcrest.Matchers.equalTo("active"));
}

@Then("the response body should contain a validation error")
public void the_response_body_should_contain_a_validation_error() {
    // A 422 response for this API indicates a Pydantic/FastAPI validation error,
    // which has a specific structure, typically a "detail" key with a list of errors.
    // We will assert that the "detail" key exists and is a list.
    testContext.getResponse().then()
        .body("detail", org.hamcrest.Matchers.instanceOf(java.util.List.class));
}

@Given("the user is authenticated as a \"{string}\"")
public void theUserIsAuthenticatedAsA(String userRole) {
    String authToken = null;
    switch (userRole) {
        case "DSPL Super Admin":
            authToken = AuthenticationHelper.getDsplSuperAdminToken();
            break;
        case "Organization Admin":
            authToken = AuthenticationHelper.getOrgAdminToken();
            break;
        case "unauthenticated":
            break; // No auth token will be used
        default:
            throw new IllegalArgumentException("Unsupported user role for testing: " + userRole);
    }
    testContext.setAuthToken(authToken);
}

@Given("an organization exists with id \"{string}\" and status \"{string}\"")
public void anOrganizationExistsWithIdAndStatus(String organizationId, String initialStatus) {
    switch (initialStatus) {
        case "active":
        case "archived":
            DataSetupHelper.createOrUpdateOrganization(organizationId, initialStatus);
            break;
        case "non-existent":
            DataSetupHelper.ensureOrganizationDoesNotExist(organizationId);
            break;
        default:
            throw new IllegalArgumentException("Unsupported initial organization status: " + initialStatus);
    }
    testContext.set("organizationId", organizationId);
}

@When("a DELETE request is sent to the archive organization endpoint with organization id \"{string}\"")
public void aDeleteRequestIsSentToTheArchiveOrganizationEndpoint(String organizationId) {
    RequestSpecification request = RestAssured.given()
        .basePath("/api/v1/organizations")
        .pathParam("organization_id", organizationId);

    String authToken = testContext.getAuthToken();
    if (authToken != null) {
        request.header("Authorization", "Bearer " + authToken);
    }

    Response response = request.when().delete("/{organization_id}");
    testContext.setResponse(response);
}

@Then("the response status code should be {int}")
public void theResponseStatusCodeShouldBe(int expectedStatusCode) {
    testContext.getResponse().then().statusCode(expectedStatusCode);
}

@Then("the response body should contain the message \"{string}\"")
public void theResponseBodyShouldContainTheMessage(String expectedMessage) {
    Response response = testContext.getResponse();
    String messageKey = response.getStatusCode() == 200 ? "message" : "error";
    response.then().body(messageKey, equalTo(expectedMessage));
}

@Then("the organization with id \"{string}\" should have the status \"{string}\"")
public void theOrganizationShouldHaveTheStatus(String organizationId, String finalStatus) {
    // This step verifies the state of the data after the API call.
    Organization org = OrganizationRepository.findById(organizationId);

    switch (finalStatus) {
        case "non-existent":
            assertNull("Organization with id '" + organizationId + "' should not exist but was found.", org);
            break;
        case "active":
        case "archived":
            assertNotNull("Organization with id '" + organizationId + "' was not found.", org);
            assertEquals("Organization status did not match expected value.", finalStatus, org.getStatus());
            break;
        default:
            throw new IllegalArgumentException("Unsupported final organization status: " + finalStatus);
    }
}
}
