package com.company.openplatform.gateway.security;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doReturn;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

class OpenApiRedisGuardTest {
    @Test void nonceDependencyFailureClosesSecurityPath(){
        StringRedisTemplate template=mock(StringRedisTemplate.class);when(template.opsForValue()).thenThrow(new DataAccessResourceFailureException("down"));
        assertThatThrownBy(()->new OpenApiRedisGuard(template).claimNonce("SANDBOX",1,"nonce_1234567890abcdef",300)).isInstanceOf(OpenApiFailure.class).extracting("code").isEqualTo("SERVICE_UNAVAILABLE");
    }
    @Test void rateDependencyFailureClosesSecurityPath(){
        StringRedisTemplate template=mock(StringRedisTemplate.class);
        doThrow(new DataAccessResourceFailureException("down")).when(template).execute(any(RedisScript.class),anyList());
        assertThatThrownBy(()->new OpenApiRedisGuard(template).consume("SANDBOX",1,"orders",0)).isInstanceOf(OpenApiFailure.class).extracting("code").isEqualTo("SERVICE_UNAVAILABLE");
    }
    @Test void malformedRateScriptResultClosesSecurityPath(){
        StringRedisTemplate template=mock(StringRedisTemplate.class);doReturn("broken").when(template).execute(any(RedisScript.class),anyList());
        assertThatThrownBy(()->new OpenApiRedisGuard(template).consume("SANDBOX",1,"orders",0)).isInstanceOf(OpenApiFailure.class).extracting("code").isEqualTo("SERVICE_UNAVAILABLE");
    }
}
