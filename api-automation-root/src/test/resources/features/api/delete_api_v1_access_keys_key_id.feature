Feature: revoke_access_key_api_v1_access_keys__key_id__delete

Scenario Outline: Revoking an access key
  Given the user is <authentication_status>
  And an access key exists with state: <key_state>
  When a DELETE request is sent for the key with id "<key_id>"
  Then the API response status code should be <status_code>
  And the response message should be "<message>"

  Examples:
    | description                    | authentication_status | key_state                             | key_id                               | status_code | message                                              |
    | Successfully revoke active key | authenticated         | active and owned by the user          | act-key-user-123                     | 200         | Access key revoked                                   |
    | Idempotent revoke              | authenticated         | already revoked and owned by the user | revoked-key-user-456                 | 200         | Access key revoked                                   |
    | No authentication              | not authenticated     | active                                | act-key-user-123                     | 401         | Not authenticated.                                   |
    | Revoke key of another user     | authenticated         | active and owned by another user      | key-owned-by-user-B-789              | 404         | Access key not found (or not owned by the caller).   |
    | Revoke non-existent key        | authenticated         | non-existent                          | 00000000-0000-0000-0000-000000000000 | 404         | Access key not found (or not owned by the caller).   |
    | Revoke with invalid key id     | authenticated         | any                                   | this-is-not-a-valid-key-id           | 400         | Invalid identifier.                                  |

Scenario: Verify that attempting to revoke an access key that has already been revoked is successful and idempotent.
  Given the user is "authenticated"
  And an access key exists with state: "revoked"
  When a DELETE request is sent for the key with id "key-a1b2c3d4-revoked"
  Then the API response status code should be 200
  And the response message should be "Access key revoked"

Scenario: Verify the request to revoke an access key fails when no authentication token is provided
  Given an access key exists with state: "active"
  And the user is "not authenticated"
  When a DELETE request is sent for the key with id "some-existing-key-id"
  Then the API response status code should be 401
  And the response message should be "Not authenticated."

Scenario Outline: Verify revoking an access key fails with invalid or expired authentication
  Given an access key exists with state: "active"
  And the 'Authorization' header is set with a "<token_type>" JWT token
  When a DELETE request is sent for the key with id "<key_id>"
  Then the API response status code should be <status_code>
  And the response message should be "<error_message>"

  Examples:
    | token_type | key_id                                     | status_code | error_message      |
    | invalid    | key-c1a2b3d4-e5f6-7890-1234-567890abcdef | 401         | Not authenticated. |
    | expired    | key-c1a2b3d4-e5f6-7890-1234-567890abcdef | 401         | Not authenticated. |

Scenario: Verify revoking a non-existent access key fails with a 404 error
  Given the user is "authenticated"
  When a DELETE request is sent for the key with id "non-existent-key-123"
  Then the API response status code should be 404
  And the response message should be "Access key not found (or not owned by the caller)."
  And the response body should contain a status code of 404

Scenario: Verify a user cannot revoke an access key belonging to another user
  Given the user is "authenticated"
  And an active access key exists for another user with id "key_owned_by_user_B_abc123"
  When a DELETE request is sent for the key with id "key_owned_by_user_B_abc123"
  Then the API response status code should be 404
  And the response message should be "Access key not found (or not owned by the caller)."
  And the response body should contain a status code of 404
  And the access key "key_owned_by_user_B_abc123" belonging to the other user remains active

Scenario Outline: Verify the request to revoke an access key fails with a validation error for invalid key ID formats
  Given the user is "authenticated"
  When a DELETE request is sent for the key with id "<key_id>"
  Then the API response status code should be 422
  And the response body should contain a validation error

  Examples:
    | key_id                      | description       |
    | invalid-key-id-format-123 | Non-UUID string   |
