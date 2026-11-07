package kr.co.marketry.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicBoolean;
import kr.co.marketry.domain.auth.properties.AuthProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class OriginCheckFilterTest {

    @Test
    void allows_state_changes_from_the_configured_frontend_origin() throws Exception {
        OriginCheckFilter filter = new OriginCheckFilter(properties());
        MockHttpServletRequest request = writeRequest("https://frontend.example");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean continued = new AtomicBoolean();

        filter.doFilter(request, response, (servletRequest, servletResponse) -> continued.set(true));

        assertThat(continued).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void rejects_state_changes_from_an_untrusted_origin() throws Exception {
        OriginCheckFilter filter = new OriginCheckFilter(properties());
        MockHttpServletRequest request = writeRequest("https://attacker.example");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean continued = new AtomicBoolean();

        filter.doFilter(request, response, (servletRequest, servletResponse) -> continued.set(true));

        assertThat(continued).isFalse();
        assertThat(response.getStatus()).isEqualTo(403);
    }

    private AuthProperties properties() {
        AuthProperties properties = new AuthProperties();
        properties.setFrontendUrl("https://frontend.example");
        return properties;
    }

    private MockHttpServletRequest writeRequest(String origin) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/custom/sectors");
        request.setScheme("https");
        request.setServerName("api.example");
        request.setServerPort(443);
        request.addHeader("Origin", origin);
        return request;
    }
}
