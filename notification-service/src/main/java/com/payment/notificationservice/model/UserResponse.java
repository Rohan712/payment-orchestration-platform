package com.payment.notificationservice.model;

import lombok.Data;

@Data
public class UserResponse {
    private ResultInfo resultInfo;
    private UserData data;

    @Data
    public static class ResultInfo {
        private String resultCode;
        private String resultCodeId;
        private String resultStatus;
        private String resultMsg;
        private boolean success;
    }

    @Data
    public static class UserData {
        private String userId;
        private String name;
        private String email;
        private String role;
        private String status;
        private String createdAt;
    }
}
