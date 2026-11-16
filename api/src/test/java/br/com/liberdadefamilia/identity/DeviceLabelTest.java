package br.com.liberdadefamilia.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class DeviceLabelTest {

    static Stream<Arguments> agents() {
        return Stream.of(
                Arguments.of(
                        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) "
                                + "Chrome/130.0.0.0 Mobile Safari/537.36",
                        "Chrome",
                        "Android"),
                Arguments.of(
                        "Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 "
                                + "(KHTML, like Gecko) Version/18.0 Mobile/15E148 Safari/604.1",
                        "Safari",
                        "iOS"),
                Arguments.of(
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) "
                                + "Chrome/130.0.0.0 Safari/537.36 Edg/130.0.0.0",
                        "Edge",
                        "Windows"),
                Arguments.of(
                        "Mozilla/5.0 (X11; Linux x86_64; rv:132.0) Gecko/20100101 Firefox/132.0", "Firefox", "Linux"),
                Arguments.of(
                        "Mozilla/5.0 (Linux; Android 14; SM-S918B) AppleWebKit/537.36 (KHTML, like Gecko) "
                                + "SamsungBrowser/25.0 Chrome/121.0.0.0 Mobile Safari/537.36",
                        "Samsung Internet",
                        "Android"),
                Arguments.of(
                        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 "
                                + "(KHTML, like Gecko) Version/18.0 Safari/605.1.15",
                        "Safari",
                        "macOS"),
                Arguments.of("curl/8.0", "other", "other"),
                Arguments.of("", "other", "other"),
                Arguments.of(null, "other", "other"));
    }

    @ParameterizedTest
    @MethodSource("agents")
    void parsesBrowserAndSystem(@Nullable String userAgent, String browser, String os) {
        assertThat(DeviceLabel.parse(userAgent)).isEqualTo(new DeviceLabel(browser, os));
    }
}
