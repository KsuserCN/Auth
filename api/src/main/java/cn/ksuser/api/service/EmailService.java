package cn.ksuser.api.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import cn.ksuser.api.util.ClientLanguage;

import java.io.UnsupportedEncodingException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.text.MessageFormat;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

@Service
public class EmailService {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    private final UserAgentParserService userAgentParserService;

    @Value("${spring.mail.username}")
    private String fromEmail;

    public EmailService(JavaMailSender mailSender, TemplateEngine templateEngine, UserAgentParserService userAgentParserService) {
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
        this.userAgentParserService = userAgentParserService;
    }

    /**
     * 发送验证码邮件
     * @param toEmail 收件人邮箱
     * @param code 验证码
     * @param type 固定验证码用途（register/login/change-email/sensitive-verification）
     * @param language 发起请求的客户端当前语言
     * @throws MessagingException 邮件发送异常
     */
    public void sendVerificationCode(String toEmail, String code, String type, ClientLanguage language) throws MessagingException, UnsupportedEncodingException {
        ClientLanguage effectiveLanguage = language == null ? ClientLanguage.SIMPLIFIED_CHINESE : language;
        String actionKey = switch (type) {
            case "register", "login", "change-email", "sensitive-verification" -> "action." + type;
            default -> throw new IllegalArgumentException("Unsupported verification code type");
        };
        ResourceBundle bundle = ResourceBundle.getBundle("mail.verification", effectiveLanguage.locale(),
            ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES));
        Map<String, String> copy = bundle.keySet().stream()
            .collect(Collectors.toUnmodifiableMap(key -> key, bundle::getString));
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setFrom(fromEmail, "Ksuser CAS");
        helper.setTo(toEmail);
        helper.setSubject(copy.get("subject"));

        // 准备模板变量
        Context context = new Context(effectiveLanguage.locale());
        String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern(copy.get("timeFormat"), effectiveLanguage.locale()));
        context.setVariable("language", effectiveLanguage.tag());
        context.setVariable("copy", copy);
        context.setVariable("requestDescription", new MessageFormat(copy.get("request"), effectiveLanguage.locale())
            .format(new Object[]{time, copy.get(actionKey)}));
        context.setVariable("code", code);

        // 渲染HTML模板
        String htmlContent = templateEngine.process("verification-code-card", context);
        helper.setText(htmlContent, true);

        mailSender.send(message);
    }
        /**
         * 发送敏感操作提醒邮件
         * @param toEmail 收件人邮箱
         * @param operation 操作类型
         * @param log 敏感操作日志对象（包含IP、设备、浏览器等）
         * @throws MessagingException 邮件发送异常
         */
        public void sendSensitiveActionReminder(String toEmail, String operation, cn.ksuser.api.entity.UserSensitiveLog log) throws MessagingException, UnsupportedEncodingException {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail, "Ksuser CAS");
            helper.setTo(toEmail);
            helper.setSubject("敏感操作提醒 - Ksuser CAS");

            Context context = new Context();
            context.setVariable("operation", operation);
            context.setVariable("time", log.getCreatedAt() != null ? log.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy年MM月dd日 HH:mm:ss")) : LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy年MM月dd日 HH:mm:ss")));
            context.setVariable("ip", log.getIpAddress());
            context.setVariable("ipLocation", log.getIpLocation());
            context.setVariable("deviceType", log.getDeviceType());
            context.setVariable("browser", log.getBrowser());
            context.setVariable("clientSource", userAgentParserService.describeClientSource(log.getBrowser(), log.getDeviceType()));

            String htmlContent = templateEngine.process("sensitive-action-reminder", context);
            helper.setText(htmlContent, true);

            mailSender.send(message);
        }
}
