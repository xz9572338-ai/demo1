package com.company.openplatform.identity.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class RegistrationContractTest {
    @Test
    void mobilePatternUsesYamlSingleEscapingAndAcceptsOnlySupportedExamples() throws Exception {
        String contract = Files.readString(Path.of("..", "..", "contracts", "openapi", "console-v1.yaml"));
        assertThat(contract).contains("pattern: '^[\\s\\u00A0\\u3000]*1[3-9](?:[\\s\\u00A0\\u3000-]*[0-9]){9}[\\s\\u00A0\\u3000]*$'")
                .doesNotContain("pattern: '^\\\\s*");
        Pattern pattern = Pattern.compile("^[\\s\\u00A0\\u3000]*1[3-9](?:[\\s\\u00A0\\u3000-]*[0-9]){9}[\\s\\u00A0\\u3000]*$");
        assertThat(pattern.matcher(" 138-1234 5678 ").matches()).isTrue();
        assertThat(pattern.matcher("\u00A0138\u30001234 5678\u00A0").matches()).isTrue();
        assertThat(pattern.matcher("12812345678").matches()).isFalse();
        assertThat(pattern.matcher("1381234567A").matches()).isFalse();
    }
}
