package com.payment.auditservice.converter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.convert.converter.Converter;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for OffsetDateTimeWriteConverter.
 */
class OffsetDateTimeWriteConverterTest {

    private final Converter<OffsetDateTime, Date> converter = new OffsetDateTimeWriteConverter();

    @Test
    @DisplayName("TC-AUD-004 convertValidOffsetDateTimeReturnsEquivalentDate")
    void convertValidOffsetDateTimeReturnsEquivalentDate() {
        // given
        OffsetDateTime now = OffsetDateTime.of(2024, 5, 1, 12, 30, 45, 0, ZoneOffset.UTC);

        // when
        Date result = converter.convert(now);

        // then
        assertThat(result).isNotNull();
        assertThat(result.toInstant()).isEqualTo(now.toInstant());
    }

    @Test
    @DisplayName("TC-AUD-005 convertHandlesDifferentZoneOffsets")
    void convertHandlesDifferentZoneOffsets() {
        // given
        OffsetDateTime odt = OffsetDateTime.of(2024, 5, 1, 14, 0, 0, 0, ZoneOffset.ofHours(2));

        // when
        Date result = converter.convert(odt);

        // then
        assertThat(result).isNotNull();
        // should represent the same instant even with offset difference
        assertThat(result.toInstant()).isEqualTo(odt.toInstant());
    }

}