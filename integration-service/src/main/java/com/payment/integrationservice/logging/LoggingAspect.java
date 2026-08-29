package com.payment.integrationservice.logging;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.aspectj.lang.JoinPoint;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Aspect
@Component
@Slf4j
public class LoggingAspect {

    /** Pointcut: all classes inside com.payment.integrationService */
    @Pointcut("within(com.payment.integrationService..*)")
    public void integrationServiceScope() {}

    /** Around advice — logs entry, exit, timing, exceptions */
    @Around("integrationServiceScope()")
    public Object logAround(ProceedingJoinPoint pjp) throws Throwable {
        String clazz = pjp.getSignature().getDeclaringTypeName();
        String method = pjp.getSignature().getName();
        String args = Arrays.toString(pjp.getArgs());

        long start = System.currentTimeMillis();
        log.info("Entering: {}.{}() with arguments = {}", clazz, method, args);

        try {
            Object result = pjp.proceed();
            long took = System.currentTimeMillis() - start;
            log.info("Exiting: {}.{}() with result = {} ({} ms)",
                    clazz, method, safe(result), took);
            return result;
        } catch (Throwable ex) {
            long took = System.currentTimeMillis() - start;
            log.error("Exception in {}.{}() after {} ms: {}",
                    clazz, method, took, ex.toString(), ex);
            throw ex;
        }
    }

    /** Fallback logging for thrown exceptions */
    @AfterThrowing(pointcut = "integrationServiceScope()", throwing = "ex")
    public void logAfterThrowing(JoinPoint jp, Throwable ex) {
        log.error("Exception in {}.{}(): {}",
                jp.getSignature().getDeclaringTypeName(),
                jp.getSignature().getName(),
                ex.toString(), ex);
    }

    private Object safe(Object result) {
        if (result == null) return "null";
        String s = result.toString();
        return s.length() > 500 ? s.substring(0, 500) + "...(truncated)" : s;
    }
}
