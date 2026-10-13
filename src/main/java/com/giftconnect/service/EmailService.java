package com.giftconnect.service;

import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/**
 * Sends the OTP verification email via real SMTP (Gmail App Password by
 * default). Credentials come ONLY from the MAIL_USERNAME / MAIL_PASSWORD
 * environment variables (or a git-ignored .env file) — never hard-coded,
 * never logged, never committed.
 *
 * DEV-CONSOLE MODE: when MAIL_USERNAME is not configured, the email content
 * is printed to the server console instead of being sent. This is the ONLY
 * situation in which a raw OTP value is ever printed — it exists so the flow
 * can be tested before SMTP credentials exist. Once MAIL_USERNAME is set,
 * nothing OTP-related is ever logged anywhere.
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;

    /** Sender identity for the "From" header. */
    @Value("${app.mail.from:no-reply@giftconnect.local}")
    private String fromAddress;

    /** SMTP username from the environment — presence decides real-send vs dev console. */
    @Value("${spring.mail.username:}")
    private String smtpUsername;

    @Autowired
    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    /** True when real SMTP credentials are present in the environment. */
    public boolean smtpConfigured() {
        return smtpUsername != null && !smtpUsername.isBlank();
    }

    /**
     * Sends the verification email over real SMTP. Throws MailException on
     * transport failure — the controller decides how to surface that.
     */
    public void sendOtpEmail(String toEmail, String otp, String name) {
        String subject = "GiftConnect Email Verification";
        String html = buildOtpHtml(name, otp);

        MimeMessage message = mailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(html, true);
            mailSender.send(message);
            // Submission success only — the SMTP server accepting the message is
            // NOT proof of inbox delivery. The full Message-ID is never logged.
            log.info("OTP email submitted to SMTP (accepted); message-id present={}",
                    message.getMessageID() != null);
        } catch (jakarta.mail.MessagingException e) {
            throw new IllegalStateException("Failed to compose verification email", e);
        } catch (MailException e) {
            // Sanitized diagnostics: a coarse category + exception class names only.
            // The raw exception text is never logged (it can contain an address).
            log.error("OTP email send failed (category={}, exception={}, rootCause={})",
                    classify(e), e.getClass().getSimpleName(), rootCauseName(e));
            throw e;
        }
    }

    /** Coarse, sanitized failure category for logs — never derived from message text. */
    private static String classify(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            String n = t.getClass().getSimpleName();
            if (n.contains("Authentication")) return "authentication";
            if (n.contains("SSL") || n.contains("TLS")) return "tls";
            if (n.contains("Connect") || n.contains("Timeout") || n.contains("Socket")) return "connection";
        }
        return "send_error";
    }

    /** Deepest cause's class name — a hint, never a raw provider message. */
    private static String rootCauseName(Throwable e) {
        Throwable t = e;
        while (t.getCause() != null && t.getCause() != t) {
            t = t.getCause();
        }
        return t.getClass().getSimpleName();
    }

    /**
     * DEV fallback when SMTP is not configured. Clearly marked; the OTP is
     * printed to the server console only in this mode.
     */
    public void printOtpToConsole(String toEmail, String otp, String name) {
        System.out.println("==========================================================");
        System.out.println("DEV-ONLY OTP EMAIL (SMTP not configured — not really sent)");
        System.out.println("To:      " + toEmail);
        System.out.println("Subject: GiftConnect Email Verification");
        System.out.println("Code:    " + otp + "   (valid 10 minutes)");
        System.out.println("Name:    " + (name == null || name.isBlank() ? "(none)" : name));
        System.out.println("==========================================================");
    }

    /** Professional OTP email body, matching the required template. */
    private String buildOtpHtml(String name, String otp) {
        String greeting = (name == null || name.isBlank())
                ? "Hello"
                : "Hello " + escapeHtml(name);

        return """
                <div style="font-family:Arial,Helvetica,sans-serif;max-width:520px;margin:0 auto;
                            border:1px solid #e5e7eb;border-radius:8px;overflow:hidden;">
                  <div style="background:#7c3aed;padding:18px 24px;">
                    <h2 style="color:#ffffff;margin:0;font-size:20px;">GiftConnect Email Verification</h2>
                  </div>
                  <div style="padding:24px;color:#111827;">
                    <p style="margin:0 0 12px;">%s,</p>
                    <p style="margin:0 0 12px;">Your GiftConnect verification code is:</p>
                    <p style="margin:0 0 16px;font-size:30px;letter-spacing:8px;font-weight:700;color:#7c3aed;">%s</p>
                    <p style="margin:0 0 8px;">The OTP expires in <strong>10 minutes</strong>.</p>
                    <p style="margin:0 0 8px;">Do not share this code with anyone.</p>
                    <p style="margin:16px 0 0;color:#6b7280;font-size:13px;">
                      If you did not request this, you can safely ignore this email.
                    </p>
                  </div>
                </div>
                """.formatted(greeting, escapeHtml(otp));
    }

    private String escapeHtml(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}