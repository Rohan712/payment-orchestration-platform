package com.payment.paymentservice.dto;

import lombok.Builder;

@Builder
public record ResultInfo(
        String resultCode,
        String resultCodeId,
        String resultStatus,
        String resultMsg,
        boolean success
) {}
