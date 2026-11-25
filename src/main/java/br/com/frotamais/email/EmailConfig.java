package br.com.frotamais.email;

import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * Escolhe a implementação de envio:
 *
 * 1. Captura local (CapturaLocalEnvioEmail): só com o perfil dev ativo E
 *    app.email.captura-local.ativa=true. Grava as mensagens num diretório
 *    privado e não envia nada. Fora do perfil dev, ou junto com SMTP, a
 *    aplicação se recusa a subir: a captura nunca substitui o envio real em
 *    silêncio.
 * 2. SMTP: quando o Spring criou o JavaMailSender (SPRING_MAIL_HOST e demais
 *    spring.mail.*) e há remetente (EMAIL_REMETENTE).
 * 3. EmailNaoConfigurado: as operações que dependem de e-mail respondem 503 e
 *    não gravam nada.
 */
@Configuration
public class EmailConfig {

    private static final Logger log = LoggerFactory.getLogger(EmailConfig.class);

    static final String PERFIL_DEV = "dev";

    @Bean
    EnvioEmail envioEmail(ObjectProvider<JavaMailSender> mailSender,
            @Value("${app.email.remetente:}") String remetente,
            @Value("${app.email.captura-local.ativa:false}") boolean capturaAtiva,
            @Value("${app.email.captura-local.diretorio:}") String capturaDiretorio,
            Environment ambiente) {
        return escolher(mailSender.getIfAvailable(), remetente, capturaAtiva, capturaDiretorio,
                ambiente.acceptsProfiles(Profiles.of(PERFIL_DEV)));
    }

    static EnvioEmail escolher(JavaMailSender mailSender, String remetente) {
        return escolher(mailSender, remetente, false, null, false);
    }

    static EnvioEmail escolher(JavaMailSender mailSender, String remetente,
            boolean capturaAtiva, String capturaDiretorio, boolean perfilDev) {
        if (capturaAtiva) {
            if (!perfilDev) {
                throw new IllegalStateException(
                        "app.email.captura-local.ativa só pode ser usada com o perfil dev.");
            }
            if (mailSender != null) {
                throw new IllegalStateException(
                        "SMTP e captura local de e-mails configurados ao mesmo tempo: escolha um.");
            }
            CapturaLocalEnvioEmail captura = new CapturaLocalEnvioEmail(
                    Path.of(capturaDiretorio == null ? "" : capturaDiretorio.trim()));
            log.warn("Captura local de e-mails ATIVA (perfil dev): nenhum e-mail é enviado; "
                    + "as mensagens ficam em {}.", captura.diretorio());
            return captura;
        }
        if (mailSender == null || remetente == null || remetente.isBlank()) {
            log.warn("Envio de e-mail não configurado (SPRING_MAIL_HOST e EMAIL_REMETENTE): convites ficam indisponíveis.");
            return new EmailNaoConfigurado();
        }
        return new SmtpEnvioEmail(mailSender, remetente.trim());
    }
}
