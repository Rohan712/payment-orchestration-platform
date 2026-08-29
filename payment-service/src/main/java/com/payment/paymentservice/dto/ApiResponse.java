package com.payment.paymentservice.dto;

import lombok.Builder;

@Builder
public record ApiResponse<T>(
        ResultInfo resultInfo,
        T data
) {}
