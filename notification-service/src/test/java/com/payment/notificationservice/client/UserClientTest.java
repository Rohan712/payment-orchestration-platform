package com.payment.notificationservice.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;


import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class UserClientTest {

    private UserClient create(String baseUrl, String token) {
        UserClient client = new UserClient();
        ReflectionTestUtils.setField(client, "userServiceBaseUrl", baseUrl);
        ReflectionTestUtils.setField(client, "userServiceToken", token);
        return client;
    }

    private MockRestServiceServer bindServer(UserClient client) {
        RestTemplate rt = (RestTemplate) ReflectionTestUtils.getField(client, "restTemplate");
        assertNotNull(rt, "restTemplate should exist");
        return MockRestServiceServer.bindTo(rt).build();
    }

    private static String successBody(boolean success, boolean includeData) {
        String resultInfo = "\"resultInfo\":{\"success\":" + success + "}";
        String data = includeData
                ? ",\"data\":{\"userId\":\"user-123\",\"name\":\"Archit\",\"email\":\"test@example.com\"}"
                : "";
        return "{ " + resultInfo + data + " }";
    }

    // ✅ TC-NOT-007 validUserFetch
    @Test
    @DisplayName("TC-NOT-007 validUserFetch")
    void validUserFetch() {
        UserClient client = create("http://localhost:8081", "mock-token");
        MockRestServiceServer server = bindServer(client);

        server.expect(ExpectedCount.once(),
                        requestTo("http://localhost:8081/api/v1/users/user-123"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer mock-token"))
                .andExpect(header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE))
                .andRespond(withSuccess(successBody(true, true), MediaType.APPLICATION_JSON));

        var user = client.getUserById("user-123");

        server.verify();
        assertNotNull(user);
        assertEquals("user-123", user.getUserId());
        assertEquals("test@example.com", user.getEmail());
        assertEquals("Archit", user.getName());
    }

    // ✅ TC-NOT-008 userNotFound (success=false)
    @Test
    @DisplayName("TC-NOT-008 userNotFound")
    void userNotFound() {
        UserClient client = create("http://host", "t0k3n");
        MockRestServiceServer server = bindServer(client);

        server.expect(requestTo("http://host/api/v1/users/user-404"))
                .andRespond(withSuccess(successBody(false, true), MediaType.APPLICATION_JSON));

        var user = client.getUserById("user-404");

        server.verify();
        assertNull(user);
    }

    // ✅ TC-NOT-009 resultInfoNull (body present but missing resultInfo)
    @Test
    @DisplayName("TC-NOT-009 resultInfoNull")
    void resultInfoNull() {
        UserClient client = create("http://host", "abc");
        MockRestServiceServer server = bindServer(client);

        server.expect(requestTo("http://host/api/v1/users/user-x"))
                .andRespond(withSuccess("{\"data\":{\"userId\":\"user-x\"}}", MediaType.APPLICATION_JSON));

        var user = client.getUserById("user-x");

        server.verify();
        assertNull(user);
    }

    // ✅ TC-NOT-010 dataNull (success=true but no data)
    @Test
    @DisplayName("TC-NOT-010 dataNull")
    void dataNull() {
        UserClient client = create("http://host", "abc");
        MockRestServiceServer server = bindServer(client);

        server.expect(requestTo("http://host/api/v1/users/user-y"))
                .andRespond(withSuccess(successBody(true, false), MediaType.APPLICATION_JSON));

        var user = client.getUserById("user-y");

        server.verify();
        assertNull(user);
    }

    // ✅ TC-NOT-011 restTemplateThrows (HTTP 500)
    @Test
    @DisplayName("TC-NOT-011 restTemplateThrows")
    void restTemplateThrows() {
        UserClient client = create("http://host", "abc");
        MockRestServiceServer server = bindServer(client);

        server.expect(requestTo("http://host/api/v1/users/user-500"))
                .andRespond(withServerError()); // triggers catch

        var user = client.getUserById("user-500");

        server.verify();
        assertNull(user);
    }

    // ✅ TC-NOT-012 tokenOptional (no Authorization header)
    @Test
    @DisplayName("TC-NOT-012 tokenOptional")
    void tokenOptional() {
        UserClient client = create("http://host", ""); // no token
        MockRestServiceServer server = bindServer(client);

        server.expect(requestTo("http://host/api/v1/users/user-999"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(request -> {
                    // Ensure Authorization header is absent
                    assertFalse(request.getHeaders().containsKey(HttpHeaders.AUTHORIZATION),
                            "Authorization header should be absent when token is blank");
                })
                .andRespond(withSuccess(successBody(true, true), MediaType.APPLICATION_JSON));

        var user = client.getUserById("user-999");

        server.verify();
        assertNotNull(user);
        assertEquals("user-123", user.getUserId());
    }
}
