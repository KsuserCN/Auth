package cn.ksuser.api.entity;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UserSystemRoleTest {
    @Test void legacyCertificationCannotGrantSystemPrivileges() {
        User user = new User();
        user.setVerificationType("admin");
        assertFalse(user.isSystemAdmin());
        assertEquals("USER", user.getSystemRole());
        assertEquals("none", user.getVerificationType());
    }

    @Test void changingRolePreservesDeveloperCertificationAndLegacyProjection() {
        User user = new User();
        user.setVerificationType("enterprise");
        user.setSystemRole("ADMIN");
        assertTrue(user.isSystemAdmin());
        assertEquals("admin", user.getVerificationType());
        assertEquals("enterprise", user.getDeveloperVerificationType());
        user.setSystemRole("USER");
        assertFalse(user.isSystemAdmin());
        assertEquals("enterprise", user.getVerificationType());
    }
}
