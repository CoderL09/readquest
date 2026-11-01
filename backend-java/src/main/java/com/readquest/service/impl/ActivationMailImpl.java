package com.readquest.service.impl;

import com.readquest.exception.ApiException;
import com.readquest.service.ActivationMail;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Properties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;

@Service
public class ActivationMailImpl implements ActivationMail {
  private final String mode, from;
  private final Path root;
  private final JavaMailSenderImpl sender;
  private final boolean ready;

  public ActivationMailImpl(
      Environment env,
      @Value("${readquest.mail.mode:smtp}") String mode,
      @Value("${readquest.local-development:false}") boolean local,
      @Value("${readquest.mail.preview-root:./private_uploads/local-mail}") String root) {
    if (!mode.equals("smtp") && !mode.equals("local"))
      throw new IllegalArgumentException("Mail mode must be smtp or local");
    if (mode.equals("local") && !local)
      throw new IllegalArgumentException(
          "Local mail preview requires the local development profile");
    this.mode = mode;
    this.root = Path.of(root).toAbsolutePath().normalize();
    String host = env.getProperty("SMTP_HOST", ""),
        username = env.getProperty("SMTP_USERNAME", ""),
        password = env.getProperty("SMTP_PASSWORD", "");
    from = env.getProperty("SMTP_FROM", username);
    sender = new JavaMailSenderImpl();
    sender.setHost(host);
    sender.setPort(env.getProperty("SMTP_PORT", Integer.class, 465));
    sender.setUsername(username);
    sender.setPassword(password);
    sender.setDefaultEncoding("UTF-8");
    Properties props = sender.getJavaMailProperties();
    props.setProperty("mail.smtp.auth", "true");
    props.setProperty("mail.smtp.ssl.enable", env.getProperty("SMTP_SSL_ENABLED", "true"));
    props.setProperty(
        "mail.smtp.starttls.enable", env.getProperty("SMTP_STARTTLS_ENABLED", "false"));
    props.setProperty(
        "mail.smtp.starttls.required", env.getProperty("SMTP_STARTTLS_ENABLED", "false"));
    props.setProperty("mail.smtp.ssl.checkserveridentity", "true");
    for (String part : new String[] {"connectiontimeout", "timeout", "writetimeout"})
      props.setProperty("mail.smtp." + part, "5000");
    ready =
        mode.equals("local")
            || (!host.isBlank()
                && !username.isBlank()
                && !password.isBlank()
                && !from.isBlank()
                && (Boolean.parseBoolean(props.getProperty("mail.smtp.ssl.enable"))
                    || Boolean.parseBoolean(props.getProperty("mail.smtp.starttls.required"))));
  }

  public String mode() {
    return mode;
  }

  public boolean configured() {
    return ready;
  }

  private Path path(String id) {
    if (!id.matches("[a-f0-9-]{36}")) throw new IllegalArgumentException();
    Path result = root.resolve(id + ".eml");
    if (Files.isSymbolicLink(root) || Files.isSymbolicLink(result))
      throw new IllegalStateException("Invalid local mail directory");
    return result;
  }

  public void send(String id, String email, String code) {
    if (!ready)
      throw new ApiException(
          503, "MAIL_NOT_CONFIGURED", "Configure SMTP before sending activation emails");
    String subject = "ReadQuest 会员激活码（模拟购买）";
    String body =
        "这是一笔模拟购买，不涉及真实扣款。\n\n激活码：" + code + "\n\n请登录购买时的账号兑换。激活码 24 小时内有效，只能使用一次，兑换后获得 7 天体验会员。\n";
    if (mode.equals("local")) {
      try {
        Files.createDirectories(root);
        Files.writeString(
            path(id),
            "To: "
                + email
                + "\nSubject: "
                + subject
                + "\nContent-Type: text/plain; charset=UTF-8\n\n"
                + body,
            StandardCharsets.UTF_8,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING);
        return;
      } catch (java.io.IOException error) {
        throw new IllegalStateException("Could not save local email preview");
      }
    }
    var message = new SimpleMailMessage();
    message.setTo(email);
    message.setFrom(from);
    message.setSubject(subject);
    message.setText(body);
    sender.send(message);
  }

  public void removePreview(String id) {
    if (mode.equals("local"))
      try {
        Files.deleteIfExists(path(id));
      } catch (java.io.IOException error) {
        throw new IllegalStateException("Could not remove local email preview");
      }
  }
}
