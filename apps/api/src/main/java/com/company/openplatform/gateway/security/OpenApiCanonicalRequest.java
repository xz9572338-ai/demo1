package com.company.openplatform.gateway.security;

import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

final class OpenApiCanonicalRequest {
    private OpenApiCanonicalRequest() {}
    static String build(HttpServletRequest request, String appId, String timestamp, String nonce) {
        String query=canonicalQuery(request.getQueryString());
        String body=HexFormat.of().formatHex(digest(new byte[0]));
        String path=request.getRequestURI();
        if(path.startsWith("/sandbox/v1"))path=path.substring("/sandbox/v1".length());
        return String.join("\n",request.getMethod().toUpperCase(),path.isEmpty()?"/":path,query,body,appId,timestamp,nonce);
    }
    static String canonicalQuery(String raw) {
        if(raw==null||raw.isEmpty())return "";
        List<Pair> values=new ArrayList<>();
        for(String part:raw.split("&",-1)){
            int split=part.indexOf('=');
            String key=split<0?part:part.substring(0,split), value=split<0?"":part.substring(split+1);
            values.add(new Pair(encode(decode(key)),encode(decode(value))));
        }
        values.sort(Comparator.comparing(Pair::key).thenComparing(Pair::value));
        return values.stream().map(v->v.key()+"="+v.value()).reduce((a,b)->a+"&"+b).orElse("");
    }
    private static String decode(String value){
        try{
            byte[] bytes=new byte[value.getBytes(StandardCharsets.UTF_8).length];int size=0;
            for(int i=0;i<value.length();){char c=value.charAt(i);if(c=='%'){if(i+2>=value.length())throw new IllegalArgumentException();bytes[size++]=(byte)Integer.parseInt(value.substring(i+1,i+3),16);i+=3;}else{byte[] raw=String.valueOf(c).getBytes(StandardCharsets.UTF_8);System.arraycopy(raw,0,bytes,size,raw.length);size+=raw.length;i++;}}
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes,0,size)).toString();
        }catch(Exception invalid){throw new OpenApiFailure(400,"VALIDATION_FAILED",false);}
    }
    private static String encode(String value){
        byte[] bytes=value.getBytes(StandardCharsets.UTF_8);StringBuilder result=new StringBuilder();
        for(byte item:bytes){int c=item&255;if((c>='A'&&c<='Z')||(c>='a'&&c<='z')||(c>='0'&&c<='9')||c=='-'||c=='.'||c=='_'||c=='~')result.append((char)c);else result.append('%').append(String.format("%02X",c));}
        return result.toString();
    }
    private static byte[] digest(byte[] value){try{return MessageDigest.getInstance("SHA-256").digest(value);}catch(Exception impossible){throw new IllegalStateException(impossible);}}
    private record Pair(String key,String value){}
}
