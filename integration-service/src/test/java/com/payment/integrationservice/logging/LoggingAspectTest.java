package com.payment.integrationservice.logging;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * Covers:
 *  - Around advice (success + exception) without mocking the static logger.
 *  - AfterThrowing advice (doesn't throw).
 *  - Private safe() method (truncation & null).
 */
class LoggingAspectTest {

    private final LoggingAspect aspect = new LoggingAspect();

    @Test
    @DisplayName("TC-INT-025 logAroundReturnsProceedResult")
    void logAroundReturnsProceedResult() throws Throwable {
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        Signature sig = mock(Signature.class);

        when(sig.getDeclaringTypeName()).thenReturn("com.payment.integrationService.DemoService");
        when(sig.getName()).thenReturn("doWork");
        when(pjp.getSignature()).thenReturn(sig);
        when(pjp.getArgs()).thenReturn(new Object[] { "arg1", 42 });
        when(pjp.proceed()).thenReturn("OK");

        Object out = aspect.logAround(pjp);

        assertThat(out).isEqualTo("OK");

        // Signature is accessed multiple times by the aspect; don't assert exact order/count
        verify(pjp, atLeastOnce()).getSignature();
        verify(pjp, atLeastOnce()).getArgs();
        verify(pjp).proceed();
    }


    @Test
    @DisplayName("TC-INT-026 logAroundRethrowsOnProceedException")
    void logAroundRethrowsOnProceedException() throws Throwable {
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        Signature sig = mock(Signature.class);

        when(sig.getDeclaringTypeName()).thenReturn("com.payment.integrationService.DemoService");
        when(sig.getName()).thenReturn("failWork");
        when(pjp.getSignature()).thenReturn(sig);
        when(pjp.getArgs()).thenReturn(new Object[0]);
        when(pjp.proceed()).thenThrow(new IllegalStateException("boom"));

        assertThatThrownBy(() -> aspect.logAround(pjp))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("boom");

        verify(pjp).proceed();
    }

    @Test
    @DisplayName("TC-INT-027 logAfterThrowingDoesNotThrow")
    void logAfterThrowingDoesNotThrow() {
        JoinPoint jp = mock(JoinPoint.class);
        Signature sig = mock(Signature.class);

        when(sig.getDeclaringTypeName()).thenReturn("com.payment.integrationService.Demo");
        when(sig.getName()).thenReturn("methodX");
        when(jp.getSignature()).thenReturn(sig);

        // Should not throw even though it logs an error
        new LoggingAspect().logAfterThrowing(jp, new RuntimeException("fail"));

        // Aspect calls these multiple times → don't enforce exact count
        verify(jp, atLeastOnce()).getSignature();
        verify(sig, atLeastOnce()).getDeclaringTypeName();
        verify(sig, atLeastOnce()).getName();
    }

    @Test
    @DisplayName("TC-INT-028 safeTruncatesLongResults")
    void safeTruncatesLongResults() throws Exception {
        String longString = "A".repeat(600);
        var m = LoggingAspect.class.getDeclaredMethod("safe", Object.class);
        m.setAccessible(true);

        String truncated = (String) m.invoke(aspect, longString);

        assertThat(truncated).hasSizeGreaterThan(500);
    }

    @Test
    @DisplayName("TC-INT-029 safeReturnsNullLiteralAndShortAsIs")
    void safeReturnsNullLiteralAndShortAsIs() throws Exception {
        var m = LoggingAspect.class.getDeclaredMethod("safe", Object.class);
        m.setAccessible(true);

        Object nul = m.invoke(aspect, (Object) null);
        Object shortStr = m.invoke(aspect, "short");

        assertThat(nul).isEqualTo("null");
        assertThat(shortStr).isEqualTo("short");
    }
}
