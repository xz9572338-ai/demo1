package com.company.openplatform.gateway.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.mock.web.MockHttpServletRequest;
import tools.jackson.databind.ObjectMapper;

class OpenApiCanonicalRequestTest {
    @Test void canonicalizesRepeatedEmptyUnicodeAndStarValues(){
        assertThat(OpenApiCanonicalRequest.canonicalQuery("z=&q=%E4%B8%AD%E6%96%87&q=a*b&space=a%20b"))
                .isEqualTo("q=%E4%B8%AD%E6%96%87&q=a%2Ab&space=a%20b&z=");
    }
    @Test void rejectsMalformedPercentEncoding(){
        assertThatThrownBy(()->OpenApiCanonicalRequest.canonicalQuery("q=%ZZ")).isInstanceOf(OpenApiFailure.class);
        assertThatThrownBy(()->OpenApiCanonicalRequest.canonicalQuery("q=%FF")).isInstanceOf(OpenApiFailure.class);
    }
    @Test void consumesPublishedSigningVectorWithoutChangingItsCanonicalPath()throws Exception{
        var vector=new ObjectMapper().readTree(Files.readString(Path.of("..","..","contracts","examples","signing-vector.json")));
        MockHttpServletRequest request=new MockHttpServletRequest(vector.get("method").asText(),"/sandbox/v1"+vector.get("path").asText());request.setQueryString(vector.get("query").asText());
        String canonical=OpenApiCanonicalRequest.build(request,vector.get("appId").asText(),vector.get("timestamp").asText(),vector.get("nonce").asText());
        assertThat(canonical).isEqualTo(vector.get("canonicalRequest").asText());
        Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(vector.get("appSecret").asText().getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
        assertThat(HexFormat.of().formatHex(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)))).isEqualTo(vector.get("expectedSignature").asText());
    }
}
