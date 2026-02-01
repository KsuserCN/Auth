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
}
