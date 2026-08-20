import java.net.URLEncoder;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class SignedRequest {
  static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20").replace("%7E", "~").replace("*", "%2A"); }
  static String query(List<Map.Entry<String,String>> entries) { return entries.stream().map(e -> Map.entry(encode(e.getKey()), encode(e.getValue())))
      .sorted(Map.Entry.<String,String>comparingByKey().thenComparing(Map.Entry.comparingByValue()))
      .map(e -> e.getKey()+"="+e.getValue()).reduce((a,b)->a+"&"+b).orElse(""); }
  static String sign(String secret, String canonical) throws Exception { Mac mac=Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),"HmacSHA256")); return HexFormat.of().formatHex(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8))); }
  public static void main(String[] args) throws Exception {
    String appId=System.getenv("OPEN_PLATFORM_APP_ID"), secret=System.getenv("OPEN_PLATFORM_APP_SECRET");
    if(appId==null||secret==null) throw new IllegalStateException("请设置 OPEN_PLATFORM_APP_ID 和 OPEN_PLATFORM_APP_SECRET");
    String timestamp=Long.toString(Instant.now().getEpochSecond()), nonce=UUID.randomUUID().toString();
    String q=query(List.of(Map.entry("startTime","2026-08-18T01:00:00Z"),Map.entry("endTime","2026-08-18T02:00:00Z"),Map.entry("page","1"),Map.entry("pageSize","20")));
    String bodyHash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(new byte[0]));
    String canonical=String.join("\n","GET","/orders",q,bodyHash,appId,timestamp,nonce);
    String signature=sign(secret,canonical), base=System.getenv().getOrDefault("OPEN_PLATFORM_BASE_URL","https://sandbox.example.invalid/sandbox/v1").replaceFirst("/+$","");
    HttpRequest request=HttpRequest.newBuilder(URI.create(base+"/orders?"+q)).timeout(Duration.ofSeconds(30)).header("X-App-ID",appId).header("X-Timestamp",timestamp).header("X-Nonce",nonce).header("X-Signature",signature).GET().build();
    if(Boolean.parseBoolean(System.getenv().getOrDefault("OPEN_PLATFORM_DRY_RUN","false"))){System.out.printf("GET %s headers=X-App-ID,X-Timestamp,X-Nonce,X-Signature%n",request.uri());return;}
    HttpResponse<String> response=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build().send(request,HttpResponse.BodyHandlers.ofString());
    System.out.printf("HTTP %d%n%s%n",response.statusCode(),response.body());
  }
}
