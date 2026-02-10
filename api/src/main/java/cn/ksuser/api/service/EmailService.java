package cn.ksuser.api.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.UnsupportedEncodingException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class EmailService {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    @Value("${spring.mail.username}")
    private String fromEmail;

    public EmailService(JavaMailSender mailSender, TemplateEngine templateEngine) {
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
    }

    /**
     * 发送验证码邮件
     * @param toEmail 收件人邮箱
     * @param code 验证码
     * @param action 操作类型（注册、重置密码等）
     * @throws MessagingException 邮件发送异常
     */
    public void sendVerificationCode(String toEmail, String code, String action) throws MessagingException, UnsupportedEncodingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setFrom(fromEmail, "Ksuser CAS");
        helper.setTo(toEmail);
        helper.setSubject("验证码 - Ksuser CAS");

        // 准备模板变量
        Context context = new Context();
        context.setVariable("time", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy年MM月dd日 HH:mm:ss")));
        context.setVariable("action", action);
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

            String htmlContent = templateEngine.process("sensitive-action-reminder", context);
            helper.setText(htmlContent, true);

            mailSender.send(message);
        }
}
