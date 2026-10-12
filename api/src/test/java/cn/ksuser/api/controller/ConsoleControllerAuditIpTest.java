package cn.ksuser.api.controller;

import cn.ksuser.api.service.ConsoleAdminService;
import cn.ksuser.api.service.ConsoleSessionService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.util.List;

import static org.mockito.Mockito.*;

class ConsoleControllerAuditIpTest {
    @Test
    void directClientCannotForgeAuditAddressWithForwardedHeaders() {
        ConsoleAdminService admin=mock(ConsoleAdminService.class);
        ConsoleController controller=new ConsoleController(mock(ConsoleSessionService.class),admin);
        ConsoleSessionService.ConsolePrincipal actor=new ConsoleSessionService.ConsolePrincipal("id","uuid",7L,Long.MAX_VALUE,Long.MAX_VALUE);
        var authentication=new UsernamePasswordAuthenticationToken(actor,null,List.of());
        MockHttpServletRequest request=new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.20");
        request.addHeader("X-Forwarded-For","198.51.100.99");
        request.addHeader("X-Real-IP","198.51.100.99");
        controller.role("target",new ConsoleController.RoleRequest("ADMIN"),authentication,request);
        verify(admin).changeRole(actor,"target","ADMIN","203.0.113.20");
    }

    @Test
    void trustedLocalReverseProxyCanSupplyClientAddress() {
        ConsoleAdminService admin=mock(ConsoleAdminService.class);
        ConsoleController controller=new ConsoleController(mock(ConsoleSessionService.class),admin);
        ConsoleSessionService.ConsolePrincipal actor=new ConsoleSessionService.ConsolePrincipal("id","uuid",7L,Long.MAX_VALUE,Long.MAX_VALUE);
        var authentication=new UsernamePasswordAuthenticationToken(actor,null,List.of());
        MockHttpServletRequest request=new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("X-Real-IP","203.0.113.20");
        controller.role("target",new ConsoleController.RoleRequest("ADMIN"),authentication,request);
        verify(admin).changeRole(actor,"target","ADMIN","203.0.113.20");
    }
}
