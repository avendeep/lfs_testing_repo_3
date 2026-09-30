Feature: delete_organization_api_v1_organizations__organization_id__delete

Scenario Outline: Archive an organization
  Given the user is authenticated as a "<user_role>"
  And an organization exists with id "<organization_id>" and status "<initial_org_state>"
  When a DELETE request is sent to the archive organization endpoint with organization id "<organization_id>"
  Then the response status code should be <expected_status_code>
  And the response body should contain the message "<expected_message>"
  And the organization with id "<organization_id>" should have the status "<final_org_state>"

  Examples:
    | user_role          | organization_id            | initial_org_state | final_org_state | expected_status_code | expected_message                                                     |
    | DSPL Super Admin   | org-active-123             | active            | archived        | 200                  | Organization archived                                                |
    | Organization Admin | org-active-123             | active            | active          | 403                  | DSPL Super Admin privileges required.                                |
    | unauthenticated    | org-active-123             | active            | active          | 401                  | Not authenticated.                                                   |
    | DSPL Super Admin   | org-nonexistent-999        | non-existent      | non-existent    | 404                  | Organization not found (including an already-archived organization). |
    | DSPL Super Admin   | org-already-archived-456   | archived          | archived        | 404                  | Organization not found (including an already-archived organization). |
    | DSPL Super Admin   | invalid-id-!@#             | non-existent      | non-existent    | 400                  | Invalid identifier.                                                  |

Scenario: Verify an unauthenticated user cannot archive an organization
  Given the user is authenticated as a "unauthenticated"
  And an organization exists with id "org_25a5e303-4927-432d-9b57-d62157c5bb6f" and status "active"
  When a DELETE request is sent to the archive organization endpoint with organization id "org_25a5e303-4927-432d-9b57-d62157c5bb6f"
  Then the response status code should be 401
  And the response body should contain the message "Not authenticated."

Scenario: Verify a user without DSPL Super Admin privileges cannot archive an organization
  Given the user is authenticated as a "Organization Admin"
  And an organization exists with id "org_b7c6d5e4f3a2" and status "active"
  When a DELETE request is sent to the archive organization endpoint with organization id "org_b7c6d5e4f3a2"
  Then the response status code should be 403
  And the response body should contain the message "DSPL Super Admin privileges required."
  And the organization with id "org_b7c6d5e4f3a2" should have the status "active"

Scenario Outline: Verify archiving an organization with various roles, IDs, and initial states
  Given the user is authenticated as a "<user_role>"
  And an organization exists with id "<setup_id>" and status "<initial_status>"
  When a DELETE request is sent to the archive organization endpoint with organization id "<request_id>"
  Then the response status code should be <expected_code>
  And the response body should contain the message "<expected_message>"
  And the organization with id "<verify_id>" should have the status "<final_status>"

  Examples:
    | description                        | user_role          | setup_id                                     | initial_status | request_id                           | expected_code | expected_message                                                     | verify_id                                    | final_status |
    | TC1: Success as Super Admin        | DSPL Super Admin   | a1b2c3d4-e5f6-7890-1234-567890abcdef         | active         | a1b2c3d4-e5f6-7890-1234-567890abcdef | 200           | Organization archived                                                | a1b2c3d4-e5f6-7890-1234-567890abcdef         | archived     |
    | TC2: Non-existent organization ID  | DSPL Super Admin   | 00000000-0000-0000-0000-000000000000         | non-existent   | 00000000-0000-0000-0000-000000000000 | 404           | Organization not found (including an already-archived organization). | 00000000-0000-0000-0000-000000000000         | non-existent |
    | TC3: Unauthenticated request       | unauthenticated    | a1b2c3d4-e5f6-7890-1234-567890abcdef         | active         | a1b2c3d4-e5f6-7890-1234-567890abcdef | 401           | Not authenticated.                                                   | a1b2c3d4-e5f6-7890-1234-567890abcdef         | active       |
    | TC4: Insufficient privileges       | Organization Admin | a1b2c3d4-e5f6-7890-1234-567890abcdef         | active         | a1b2c3d4-e5f6-7890-1234-567890abcdef | 403           | DSPL Super Admin privileges required.                                | a1b2c3d4-e5f6-7890-1234-567890abcdef         | active       |
    | TC5: Malformed organization ID     | DSPL Super Admin   | a1b2c3d4-e5f6-7890-1234-567890abcdef         | active         | invalid-id-format                    | 400           | Invalid identifier.                                                  | a1b2c3d4-e5f6-7890-1234-567890abcdef         | active       |
    | TC6: Already archived organization | DSPL Super Admin   | f0e9d8c7-b6a5-4321-fedc-ba9876543210         | archived       | f0e9d8c7-b6a5-4321-fedc-ba9876543210 | 404           | Organization not found (including an already-archived organization). | f0e9d8c7-b6a5-4321-fedc-ba9876543210         | archived     |

Scenario: Verify attempting to archive an already archived organization fails
  Given the user is authenticated as a "DSPL Super Admin"
  And an organization exists with id "org_a1b2c3d4-e5f6-7890-1234-567890abcdef" and status "archived"
  When a DELETE request is sent to the archive organization endpoint with organization id "org_a1b2c3d4-e5f6-7890-1234-567890abcdef"
  Then the response status code should be 404
  And the response body should contain the message "Organization not found (including an already-archived organization)."
  And the organization with id "org_a1b2c3d4-e5f6-7890-1234-567890abcdef" should have the status "archived"

Scenario: Verify attempting to archive an organization with an invalidly formatted identifier fails
  Given the user is authenticated as a "DSPL Super Admin"
  When a DELETE request is sent to the archive organization endpoint with organization id "not-a-valid-uuid-123!"
  Then the response status code should be 400
  And the response body should contain the message "Invalid identifier."

Scenario: Verify API rejects organization archival with a path traversal sequence in the identifier
  Given the user is authenticated as a "DSPL Super Admin"
  When a DELETE request is sent to the archive organization endpoint with organization id "../../etc/passwd"
  Then the response status code should be 400
  And the response body should contain the message "Invalid identifier."
