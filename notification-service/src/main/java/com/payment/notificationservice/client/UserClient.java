package com.payment.notificationservice.client;

import com.payment.notificationservice.model.UserResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
@RequiredArgsConstructor
public class UserClient {
    private static final Logger log = LoggerFactory.getLogger(UserClient.class);

    @Value("${user.service.base-url}")
    private String userServiceBaseUrl;

    @Value("${user.service.token:}")
    private String userServiceToken;

    private final RestTemplate restTemplate = new RestTemplate();

    public UserResponse.UserData getUserById(String userId) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (userServiceToken != null && !userServiceToken.isBlank()) {
                headers.setBearerAuth(userServiceToken);
            }

            HttpEntity<Void> request = new HttpEntity<>(headers);
            ResponseEntity<UserResponse> response = restTemplate.exchange(
                    userServiceBaseUrl + "/api/v1/users/" + userId,
                    HttpMethod.GET,
                    request,
                    UserResponse.class
            );

            UserResponse body = response.getBody();
            if (body != null && body.getResultInfo() != null && body.getResultInfo().isSuccess() && body.getData() != null) {
                return body.getData();
            } else {
                log.warn("User not found for userId={}", userId);
                return null;
            }
        } catch (Exception e) {
            log.error("Error calling user service for userId={}", userId, e);
            return null;
        }
    }
}