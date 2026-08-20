package com.company.openplatform.shared.security;

import jakarta.servlet.http.HttpServletRequest;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
public final class RegistrationRateLimiter {
    private static final DefaultRedisScript<Long> INCREMENT = new DefaultRedisScript<>("""
            local now = redis.call('TIME')
            local window = math.floor(tonumber(now[1]) / tonumber(ARGV[2]))
            local key = KEYS[1] .. ':' .. window
            local value = redis.call('INCR', key)
            if value == 1 then redis.call('EXPIRE', key, ARGV[1]) end
            return value
            """, Long.class);
    private static final int WINDOW_SECONDS = 60;

    private final StringRedisTemplate redis;
    private final Set<String> trustedProxies;
    private final int abuseLimit;
    private final int businessLimit;

    public RegistrationRateLimiter(StringRedisTemplate redis,
            @Value("${open-platform.security.trusted-proxies:127.0.0.1,::1}") String trustedProxies,
            @Value("${open-platform.security.registration-rate.abuse-per-minute:30}") int abuseLimit,
            @Value("${open-platform.security.registration-rate.business-per-minute:10}") int businessLimit) {
        if (abuseLimit < 1 || businessLimit < 1) throw new IllegalArgumentException("Rate limits must be positive");
        this.redis = redis;
        this.trustedProxies = Arrays.stream(trustedProxies.split(","))
                .map(String::trim).filter(value -> !value.isEmpty()).map(RegistrationRateLimiter::normalizeLiteral)
                .collect(Collectors.toUnmodifiableSet());
        this.abuseLimit = abuseLimit;
        this.businessLimit = businessLimit;
    }

    public boolean consumeAbuse(HttpServletRequest request) {
        return consume("registration:abuse", clientAddress(request), abuseLimit);
    }

    public void consumeBusiness(HttpServletRequest request) {
        if (!consume("registration:business", clientAddress(request), businessLimit)) {
            throw new RegistrationRateLimitException();
        }
    }

    public String clientAddress(HttpServletRequest request) {
        String remote = normalizeLiteral(request.getRemoteAddr());
        if (!trustedProxies.contains(remote)) return remote;
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded == null || forwarded.isBlank()) return remote;
        String[] chain = forwarded.split(",");
        for (int index = chain.length - 1; index >= 0; index--) {
            String hop;
            try {
                hop = normalizeLiteral(chain[index].trim());
            } catch (IllegalArgumentException exception) {
                return remote;
            }
            if (!trustedProxies.contains(hop)) return hop;
        }
        return remote;
    }

    private boolean consume(String namespace, String client, int limit) {
        String keyPrefix = "rate:" + namespace + ":" + client;
        try {
            Long count = redis.execute(INCREMENT, List.of(keyPrefix), Integer.toString(WINDOW_SECONDS + 5),
                    Integer.toString(WINDOW_SECONDS));
            return count != null && count <= limit;
        } catch (DataAccessException exception) {
            throw new RegistrationRateLimitUnavailableException(exception);
        }
    }

    private static String normalizeLiteral(String value) {
        if (value == null || value.isBlank() || value.length() > 64
                || !value.matches("[0-9A-Fa-f:.]+")) {
            throw new IllegalArgumentException("Invalid IP literal");
        }
        if (value.indexOf(':') < 0) {
            String[] parts = value.split("\\.", -1);
            if (parts.length != 4) throw new IllegalArgumentException("Invalid IPv4 literal");
            return Arrays.stream(parts).map(part -> {
                int octet;
                try { octet = Integer.parseInt(part); }
                catch (NumberFormatException exception) { throw new IllegalArgumentException("Invalid IPv4 literal"); }
                if (octet < 0 || octet > 255) throw new IllegalArgumentException("Invalid IPv4 literal");
                return Integer.toString(octet);
            }).collect(Collectors.joining("."));
        }
        try {
            InetAddress address = InetAddress.getByName(value);
            if (!(address instanceof Inet6Address)) {
                if (value.indexOf(':') >= 0 && address.getAddress().length == 4) return address.getHostAddress();
                throw new IllegalArgumentException("Invalid IPv6 literal");
            }
            byte[] bytes = address.getAddress();
            boolean mappedIpv4 = true;
            for (int index = 0; index < 10; index++) mappedIpv4 &= bytes[index] == 0;
            mappedIpv4 &= bytes[10] == (byte) 0xff && bytes[11] == (byte) 0xff;
            if (mappedIpv4) {
                return Byte.toUnsignedInt(bytes[12]) + "." + Byte.toUnsignedInt(bytes[13]) + "."
                        + Byte.toUnsignedInt(bytes[14]) + "." + Byte.toUnsignedInt(bytes[15]);
            }
            return address.getHostAddress().split("%", 2)[0];
        } catch (UnknownHostException exception) {
            throw new IllegalArgumentException("Invalid IP literal", exception);
        }
    }
}
