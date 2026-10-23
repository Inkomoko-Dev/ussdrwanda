package com.inkomoko.mobilebanking.services.integration;

import com.inkomoko.mobilebanking.config.InkomokoProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;

import java.util.Properties;

/** Sends portal e-mail through SMTP. A failure is thrown so the caller can leave the password unchanged. */
@Slf4j
@Service
@ConditionalOnProperty(name = "inkomoko.mail.mode", havingValue = "smtp")
public class SmtpMailService implements MailService {

    private final JavaMailSenderImpl sender;
    private final String from;

    public SmtpMailService(InkomokoProperties props) {
        InkomokoProperties.Mail cfg = props.getMail();
        if (cfg.getHost() == null || cfg.getHost().isBlank()) {
            throw new IllegalStateException("inkomoko.mail.host is required when mail mode is smtp");
        }
        JavaMailSenderImpl mail = new JavaMailSenderImpl();
        mail.setHost(cfg.getHost().trim());
        mail.setPort(cfg.getPort());
        if (cfg.getUsername() != null && !cfg.getUsername().isBlank()) {
            mail.setUsername(cfg.getUsername());
            mail.setPassword(cfg.getPassword());
        }
        Properties javaMail = new Properties();
        javaMail.put("mail.smtp.auth", String.valueOf(mail.getUsername() != null));
        if (cfg.isStarttls()) javaMail.put("mail.smtp.starttls.enable", "true");
        mail.setJavaMailProperties(javaMail);
        this.sender = mail;
        this.from = cfg.getFrom();
    }

    @Override
    public void send(String to, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        try {
            sender.send(message);
        } catch (RuntimeException e) {
            log.error("E-mail send failed to={} subject={} error={}", to, subject, e.getMessage());
            throw e;
        }
        log.info("E-mail sent to={} subject={}", to, subject);
    }
}
