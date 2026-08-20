package com.company.openplatform.gateway.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
class OpenApiRedisGuard {
    private static final DefaultRedisScript<String> BUCKET = new DefaultRedisScript<>("""
            local capacity=20 local time=redis.call('TIME') local now=(tonumber(time[1])*1000)+math.floor(tonumber(time[2])/1000) local last=tonumber(redis.call('HGET',KEYS[1],'last') or now)
            local tokens=tonumber(redis.call('HGET',KEYS[1],'tokens') or capacity)
            tokens=math.min(capacity,tokens+((now-last)/1000))
            if tokens < 1 then local retry=math.ceil((1-tokens)*1000) redis.call('HSET',KEYS[1],'tokens',tokens,'last',now) redis.call('PEXPIRE',KEYS[1],60000) return '0:'..retry end
            tokens=tokens-1 redis.call('HSET',KEYS[1],'tokens',tokens,'last',now) redis.call('PEXPIRE',KEYS[1],60000) return '1:0'
            """, String.class);
    private final StringRedisTemplate redis;
    OpenApiRedisGuard(StringRedisTemplate redis){this.redis=redis;}
    boolean claimNonce(String environment,long applicationId,String nonce,long ttlSeconds){
        try{return Boolean.TRUE.equals(redis.opsForValue().setIfAbsent("openapi:nonce:"+environment+":"+applicationId+":"+hash(nonce),"1",Duration.ofSeconds(ttlSeconds)));}
        catch(DataAccessException failure){throw new OpenApiFailure(503,"SERVICE_UNAVAILABLE",true);}
    }
    void consume(String environment,long applicationId,String endpoint,long nowMillis){
        try{String result=redis.execute(BUCKET,List.of("openapi:rate:"+environment+":"+applicationId+":"+hash(endpoint)));
            if(result==null)throw new OpenApiFailure(503,"SERVICE_UNAVAILABLE",true);
            String[] parts=result.split(":",-1);if(parts.length!=2)throw new IllegalArgumentException("invalid rate result");
            if("0".equals(parts[0]))throw new OpenApiFailure(429,"RATE_LIMITED",true,Math.max(1,(int)Math.ceil(Long.parseLong(parts[1])/1000d)));
            if(!"1".equals(parts[0])||!"0".equals(parts[1]))throw new IllegalArgumentException("invalid rate result");
        }catch(OpenApiFailure failure){throw failure;}catch(DataAccessException|IllegalArgumentException|ArrayIndexOutOfBoundsException failure){throw new OpenApiFailure(503,"SERVICE_UNAVAILABLE",true);}
    }
    private String hash(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception impossible){throw new IllegalStateException(impossible);}}
}
