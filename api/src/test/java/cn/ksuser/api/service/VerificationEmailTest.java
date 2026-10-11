package cn.ksuser.api.service;

import cn.ksuser.api.util.ClientLanguage;
import jakarta.mail.Multipart;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.util.Properties;
import java.util.ResourceBundle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Capture MIME content locally; these tests never contact an SMTP server. */
class VerificationEmailTest {
    private EmailService emails;
    private JavaMailSender sender;
    private MimeMessage message;

    @BeforeEach void setUp() {
        sender = mock(JavaMailSender.class);
        message = new MimeMessage(Session.getInstance(new Properties()));
        when(sender.createMimeMessage()).thenReturn(message);
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/"); resolver.setSuffix(".html"); resolver.setCharacterEncoding("UTF-8");
        SpringTemplateEngine engine = new SpringTemplateEngine(); engine.setTemplateResolver(resolver);
        emails = new EmailService(sender, engine, mock(UserAgentParserService.class));
        ReflectionTestUtils.setField(emails, "fromEmail", "noreply@example.invalid");
    }

    @ParameterizedTest
    @CsvSource({
        "register,ENGLISH,create an account", "login,ENGLISH,sign in",
        "change-email,ENGLISH,change your email address", "sensitive-verification,ENGLISH,verify your identity",
        "register,SIMPLIFIED_CHINESE,注册账号", "login,SIMPLIFIED_CHINESE,登录账号",
        "change-email,SIMPLIFIED_CHINESE,更改邮箱", "sensitive-verification,SIMPLIFIED_CHINESE,敏感操作验证"
    })
    void rendersCompleteCopyForEveryPurpose(String type, ClientLanguage language, String action) throws Exception {
        emails.sendVerificationCode("recipient@example.invalid", "012345", type, language);
        message.saveChanges();
        String html = text(message.getContent());
        assertTrue(html.contains("012345")); assertTrue(html.contains(action));
        assertTrue(html.contains("lang=\"" + language.tag() + "\""));
        assertEquals("recipient@example.invalid", message.getAllRecipients()[0].toString());
        if (language == ClientLanguage.ENGLISH) {
            assertEquals("Verification code - Ksuser CAS", message.getSubject());
            assertTrue(html.contains("10 minutes")); assertTrue(html.contains("1 hour"));
            String visible = html.replaceAll("(?s)<style.*?</style>", "").replaceAll("(?s)<!--.*?-->", "");
            assertFalse(visible.matches("(?s).*\\p{IsHan}.*"), "English email must not contain Chinese visible text");
        } else {
            assertEquals("验证码 - Ksuser CAS", message.getSubject());
            assertTrue(html.contains("10 分钟")); assertTrue(html.contains("1小时"));
        }
        verify(sender).send(message);
    }

    @Test void templateEscapesValuesAndResourceKeysMatch() throws Exception {
        emails.sendVerificationCode("recipient@example.invalid", "<unsafe>", "login", ClientLanguage.ENGLISH);
        assertTrue(text(message.getContent()).contains("&lt;unsafe&gt;"));
        assertEquals(ResourceBundle.getBundle("mail.verification", ClientLanguage.SIMPLIFIED_CHINESE.locale()).keySet(),
            ResourceBundle.getBundle("mail.verification", ClientLanguage.ENGLISH.locale()).keySet());
    }

    private String text(Object content) throws Exception {
        if (content instanceof String string) return string;
        if (content instanceof Multipart multipart) {
            StringBuilder result = new StringBuilder();
            for (int i = 0; i < multipart.getCount(); i++) result.append(text(multipart.getBodyPart(i).getContent()));
            return result.toString();
        }
        return "";
    }
}
