package com.company.openplatform.shared.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.web.MockHttpServletRequest;

class RegistrationRateLimiterAddressTest {
    @Test
    void canonicalizesMappedIpv6ToSameBucketAsIpv4() {
        var limiter = new RegistrationRateLimiter(mock(StringRedisTemplate.class), "127.0.0.1", 30, 10);
        var ipv4 = new MockHttpServletRequest();
        ipv4.setRemoteAddr("192.0.2.44");
        var mapped = new MockHttpServletRequest();
        mapped.setRemoteAddr("::ffff:192.0.2.44");
        assertThat(limiter.clientAddress(mapped)).isEqualTo(limiter.clientAddress(ipv4));
    }
}
