package br.com.frotamais.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * Escolhe a implementação de envio. SMTP só quando o Spring criou o
 * JavaMailSender (SPRING_MAIL_HOST e demais spring.mail.* definidos) e há
 * remetente (EMAIL_REMETENTE). Sem isso, EmailNaoConfigurado: as operações
 * que dependem de e-mail respondem 503 e não gravam nada.
 */
@Configuration
public class EmailConfig {

    private static final Logger log = LoggerFactory.getLogger(EmailConfig.class);

    @Bean
    EnvioEmail envioEmail(ObjectProvider<JavaMailSender> mailSender,
            @Value("${app.email.remetente:}") String remetente) {
        return escolher(mailSender.getIfAvailable(), remetente);
    }

    static EnvioEmail escolher(JavaMailSender mailSender, String remetente) {
        if (mailSender == null || remetente == null || remetente.isBlank()) {
            log.warn("Envio de e-mail não configurado (SPRING_MAIL_HOST e EMAIL_REMETENTE): convites ficam indisponíveis.");
            return new EmailNaoConfigurado();
        }
        return new SmtpEnvioEmail(mailSender, remetente.trim());
    }
}
