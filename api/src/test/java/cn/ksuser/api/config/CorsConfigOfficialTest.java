package cn.ksuser.api.config;

import cn.ksuser.api.entity.OfficialApplication;
import cn.ksuser.api.repository.OfficialApplicationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.filter.CorsFilter;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CorsConfigOfficialTest {
    private OfficialApplicationRepository applications;
    private CorsFilter filter;

    @BeforeEach
    void setup() {
        AppProperties properties = new AppProperties();
        properties.setDebug(false);
        applications = mock(OfficialApplicationRepository.class);
        OfficialApplication app = new OfficialApplication();
        app.setRedirectUris("https://official.example/auth/callback");
        when(applications.findAllByIsActiveTrue()).thenReturn(List.of(app));
        filter = new CorsConfig(properties, applications).corsFilter();
    }

    @Test
    void registeredApplicationOriginCanOnlyReadOfficialProtocol() throws Exception {
        for (String path : List.of("/official/token", "/official/userinfo")) {
            MockHttpServletResponse response = request(path, "https://official.example");
            assertEquals(200, response.getStatus());
            assertEquals("https://official.example", response.getHeader("Access-Control-Allow-Origin"));
        }
        for (String path : List.of("/auth/info", "/console/session", "/console/csrf-token", "/official/authorize/approve")) {
            assertEquals(403, request(path, "https://official.example").getStatus(), path);
        }
        verify(applications, times(2)).findAllByIsActiveTrue();
    }

    @Test
    void unregisteredOriginIsDeniedEvenAtOfficialProtocol() throws Exception {
        assertEquals(403, request("/official/token", "https://unknown.example").getStatus());
        when(applications.findAllByIsActiveTrue()).thenReturn(List.of());
        assertEquals(403, request("/official/userinfo", "https://official.example").getStatus());
    }

    @Test
    void consoleAndPassportHaveExactProductionOrigins() throws Exception {
        assertEquals(200, request("/console/session", "https://console.ksuser.cn").getStatus());
        assertEquals(200, request("/auth/info", "https://auth.ksuser.cn").getStatus());
        assertEquals(403, request("/console/session", "https://console.ksuser.cn.attacker.example").getStatus());
        assertEquals(403, request("/console/session", "http://localhost:5174").getStatus());
        verifyNoInteractions(applications);
    }

    private MockHttpServletResponse request(String path, String origin) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.setScheme("https"); request.setServerName("api.ksuser.cn"); request.setServerPort(443);
        request.addHeader("Origin", origin);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean reachedEndpoint = new AtomicBoolean();
        filter.doFilter(request, response, (req, res) -> reachedEndpoint.set(true));
        assertEquals(response.getStatus() != 403, reachedEndpoint.get());
        return response;
    }
}
