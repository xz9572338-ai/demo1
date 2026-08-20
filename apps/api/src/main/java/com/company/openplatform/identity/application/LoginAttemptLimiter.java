package com.company.openplatform.identity.application;

import com.company.openplatform.identity.domain.AuthenticationServiceUnavailableException;
import com.company.openplatform.identity.domain.LoginLockedException;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import com.company.openplatform.shared.security.RegistrationRateLimiter;

@Component
public final class LoginAttemptLimiter {
    private static final DefaultRedisScript<String> ACQUIRE = new DefaultRedisScript<>("""
            local function seconds(pttl)
              if pttl < 0 then return tonumber(ARGV[5]) end
              return math.max(1, math.ceil(pttl / 1000))
            end
            if redis.call('EXISTS', KEYS[1]) == 1 then return 'ACCOUNT:' .. seconds(redis.call('PTTL', KEYS[1])) end
            if redis.call('EXISTS', KEYS[2]) == 1 then return 'IP:' .. seconds(redis.call('PTTL', KEYS[2])) end
            if not redis.call('SET', KEYS[3], ARGV[1], 'NX', 'PX', ARGV[2]) then
              return 'BUSY:' .. seconds(redis.call('PTTL', KEYS[3]))
            end
            local now = redis.call('TIME')
            local millis = tonumber(now[1]) * 1000 + math.floor(tonumber(now[2]) / 1000)
            redis.call('ZREMRANGEBYSCORE', KEYS[4], 0, millis - tonumber(ARGV[3]))
            redis.call('ZADD', KEYS[4], millis, ARGV[1])
            redis.call('PEXPIRE', KEYS[4], ARGV[3])
            if redis.call('ZCARD', KEYS[4]) > tonumber(ARGV[4]) then
              redis.call('ZREM', KEYS[4], ARGV[1])
              redis.call('DEL', KEYS[3])
              return 'IP:1'
            end
            return 'OK'
            """, String.class);
    private static final DefaultRedisScript<Long> FAILURE = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[5]) ~= ARGV[1] then return 0 end
            local now = redis.call('TIME')
            local millis = tonumber(now[1]) * 1000 + math.floor(tonumber(now[2]) / 1000)
            redis.call('ZREMRANGEBYSCORE', KEYS[1], 0, millis - tonumber(ARGV[2]))
            redis.call('ZADD', KEYS[1], millis, ARGV[1])
            redis.call('PEXPIRE', KEYS[1], ARGV[2])
            local count = redis.call('ZCARD', KEYS[1])
            if count >= tonumber(ARGV[3]) then
              redis.call('SET', KEYS[2], '1', 'EX', ARGV[5])
              redis.call('DEL', KEYS[1])
            end
            redis.call('ZREMRANGEBYSCORE', KEYS[3], 0, millis - tonumber(ARGV[2]))
            redis.call('ZADD', KEYS[3], millis, ARGV[1])
            redis.call('PEXPIRE', KEYS[3], ARGV[2])
            count = redis.call('ZCARD', KEYS[3])
            if count >= tonumber(ARGV[4]) then
              redis.call('SET', KEYS[4], '1', 'EX', ARGV[5])
              redis.call('DEL', KEYS[3])
            end
            redis.call('DEL', KEYS[5])
            return 1
            """, Long.class);
    private static final DefaultRedisScript<Long> SUCCESS = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[3]) ~= ARGV[1] then return 0 end
            redis.call('ZREM', KEYS[4], ARGV[1])
            redis.call('DEL', KEYS[1], KEYS[2], KEYS[3])
            return 1
            """, Long.class);
    private static final DefaultRedisScript<Long> ABORT = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[1]) ~= ARGV[1] then return 0 end
            redis.call('ZREM', KEYS[2], ARGV[1])
            redis.call('DEL', KEYS[1])
            return 1
            """, Long.class);
    private final StringRedisTemplate redis;
    private final RegistrationRateLimiter addressResolver;
    private final int accountLimit;
    private final int ipLimit;
    private final int windowSeconds;
    private final int lockSeconds;
    private final byte[] identifierHmacKey;

    public LoginAttemptLimiter(StringRedisTemplate redis, RegistrationRateLimiter addressResolver,
            @Value("${open-platform.security.login.identifier-hmac-key}") String identifierHmacKey,
            @Value("${open-platform.security.login.account-limit:5}") int accountLimit,
            @Value("${open-platform.security.login.ip-limit:20}") int ipLimit,
            @Value("${open-platform.security.login.window-seconds:900}") int windowSeconds,
            @Value("${open-platform.security.login.lock-seconds:1800}") int lockSeconds) {
        this.redis = redis; this.addressResolver = addressResolver; this.accountLimit = accountLimit;
        this.ipLimit = ipLimit; this.windowSeconds = windowSeconds; this.lockSeconds = lockSeconds;
        try { this.identifierHmacKey = Base64.getDecoder().decode(identifierHmacKey); }
        catch (IllegalArgumentException exception) { throw new IllegalArgumentException("Login identifier HMAC key must be Base64", exception); }
        if (this.identifierHmacKey.length < 32) throw new IllegalArgumentException("Login identifier HMAC key must contain at least 256 bits");
        if (accountLimit < 1 || accountLimit > 10_000 || ipLimit < 1 || ipLimit > 10_000
                || windowSeconds < 1 || windowSeconds > 86_400 || lockSeconds < 1 || lockSeconds > 604_800)
            throw new IllegalArgumentException("Login protection values are outside supported bounds");
        Math.multiplyExact((long) windowSeconds, 1000L);
    }

    public AttemptKeys keys(String normalizedLogin, HttpServletRequest request) {
        return new AttemptKeys(hash(normalizedLogin), addressResolver.clientAddress(request), java.util.UUID.randomUUID().toString());
    }

    public void assertAllowed(AttemptKeys keys) {
        try {
            String result = redis.execute(ACQUIRE, List.of("auth:lock:account:" + keys.account(),
                    "auth:lock:ip:" + keys.ip(), "auth:lease:account:" + keys.account(),
                    "auth:fail:ip:" + keys.ip()), keys.token(), "30000",
                    Long.toString(Math.multiplyExact((long) windowSeconds, 1000L)), Integer.toString(ipLimit),
                    Integer.toString(lockSeconds));
            if (result == null) throw new AuthenticationServiceUnavailableException(new IllegalStateException("Redis result absent"));
            if (result.startsWith("ACCOUNT:")) throw new LoginLockedException(ttl(result), false);
            if (result.startsWith("IP:")) throw new LoginLockedException(ttl(result), true);
            if (result.startsWith("BUSY:")) throw new LoginLockedException(ttl(result), false);
            if (!"OK".equals(result)) throw new AuthenticationServiceUnavailableException(new IllegalStateException("Unexpected Redis result"));
        } catch (DataAccessException exception) { throw new AuthenticationServiceUnavailableException(exception); }
    }

    public void failed(AttemptKeys keys) {
        try {
            Long result = redis.execute(FAILURE, List.of("auth:fail:account:" + keys.account(),
                            "auth:lock:account:" + keys.account(), "auth:fail:ip:" + keys.ip(),
                            "auth:lock:ip:" + keys.ip(), "auth:lease:account:" + keys.account()),
                    keys.token(), Long.toString(Math.multiplyExact((long) windowSeconds, 1000L)),
                    Integer.toString(accountLimit), Integer.toString(ipLimit), Integer.toString(lockSeconds));
            if (!Long.valueOf(1).equals(result)) throw new AuthenticationServiceUnavailableException(new IllegalStateException("Login lease lost"));
        } catch (DataAccessException exception) { throw new AuthenticationServiceUnavailableException(exception); }
    }

    public void succeeded(AttemptKeys keys) {
        try {
            Long result = redis.execute(SUCCESS, List.of("auth:fail:account:" + keys.account(),
                    "auth:lock:account:" + keys.account(), "auth:lease:account:" + keys.account(),
                    "auth:fail:ip:" + keys.ip()), keys.token());
            if (!Long.valueOf(1).equals(result)) throw new AuthenticationServiceUnavailableException(new IllegalStateException("Login lease lost"));
        }
        catch (DataAccessException exception) { throw new AuthenticationServiceUnavailableException(exception); }
    }

    public void abort(AttemptKeys keys) {
        try {
            Long result = redis.execute(ABORT, List.of("auth:lease:account:" + keys.account(),
                    "auth:fail:ip:" + keys.ip()), keys.token());
            if (result == null) throw new AuthenticationServiceUnavailableException(new IllegalStateException("Redis result absent"));
        } catch (DataAccessException exception) { throw new AuthenticationServiceUnavailableException(exception); }
    }

    private String hash(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(identifierHmacKey, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException exception) { throw new IllegalStateException(exception); }
    }

    private static long ttl(String result) { return Math.max(1, Long.parseLong(result.substring(result.indexOf(':') + 1))); }

    public record AttemptKeys(String account, String ip, String token) {}
}
