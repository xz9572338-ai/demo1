import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HexFormat;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class SigningVectorVerifier {
  public static void main(String[] args) throws Exception {
    String json=Files.readString(Path.of("contracts/examples/signing-vector.json"));
    String secret=value(json,"appSecret"), canonical=value(json,"canonicalRequest").replace("\\n","\n"), expected=value(json,"expectedSignature");
    Mac mac=Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
    String actual=HexFormat.of().formatHex(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
    if(!actual.equals(expected)) throw new AssertionError("signing vector mismatch");
    if(!SignedRequest.encode("*").equals("%2A")) throw new AssertionError("RFC 3986 star encoding mismatch");
    System.out.println("Java signing vector: PASS");
  }
  static String value(String json,String name){var match=Pattern.compile("\\\""+name+"\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"])*)\\\"").matcher(json);if(!match.find())throw new IllegalArgumentException("missing "+name);return match.group(1);}
}
