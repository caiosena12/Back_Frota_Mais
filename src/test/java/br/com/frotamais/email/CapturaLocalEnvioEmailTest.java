package br.com.frotamais.email;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import br.com.frotamais.comum.RegraNegocioException;

/**
 * Captura local de e-mails (só desenvolvimento): só é escolhida com o perfil
 * dev e a chave explícita; nunca convive com SMTP; grava só em pasta privada,
 * só para destinatários fictícios, e não registra o link no log.
 */
@ExtendWith(OutputCaptureExtension.class)
class CapturaLocalEnvioEmailTest {

    private static final String LINK = "https://app.exemplo.test/ativar?token=TOKENSECRETO123";
    private static final MensagemEmail CONVITE = new MensagemEmail("motorista@exemplo.test",
            "Convite para acessar o Frota+", "Para ativar, acesse:\n" + LINK + "\n");

    @TempDir
    Path tmp;

    private String privado() {
        return tmp.resolve("contexto-local").resolve("emails-dev").toString();
    }

    private static List<Path> arquivos(Path dir) throws Exception {
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> s = Files.list(dir)) {
            return s.toList();
        }
    }

    // --- escolha da implementação ---

    @Test
    void desligadaPorPadraoMantemComportamentoAtual() {
        assertInstanceOf(EmailNaoConfigurado.class, EmailConfig.escolher(null, "", false, privado(), true));
        assertInstanceOf(SmtpEnvioEmail.class,
                EmailConfig.escolher(new JavaMailSenderImpl(), "frota@empresa.com", false, privado(), true));
    }

    @Test
    void foraDoPerfilDevRecusaSubir() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> EmailConfig.escolher(null, "", true, privado(), false));
        assertTrue(e.getMessage().contains("perfil dev"));
    }

    @Test
    void comSmtpConfiguradoRecusaSubir() {
        assertThrows(IllegalStateException.class,
                () -> EmailConfig.escolher(new JavaMailSenderImpl(), "frota@empresa.com", true, privado(), true));
    }

    @Test
    void noPerfilDevComChaveUsaCaptura() {
        EnvioEmail envio = EmailConfig.escolher(null, "", true, privado(), true);
        assertInstanceOf(CapturaLocalEnvioEmail.class, envio);
        assertTrue(envio.disponivel());
    }

    @Test
    void diretorioForaDaPastaPrivadaOuVazioRecusaSubir() {
        assertThrows(IllegalStateException.class,
                () -> EmailConfig.escolher(null, "", true, tmp.resolve("emails").toString(), true));
        assertThrows(IllegalStateException.class,
                () -> EmailConfig.escolher(null, "", true, tmp.resolve("contexto-localx/emails").toString(), true));
        assertThrows(IllegalStateException.class, () -> EmailConfig.escolher(null, "", true, "  ", true));
        assertThrows(IllegalStateException.class, () -> EmailConfig.escolher(null, "", true, null, true));
    }

    // --- gravação ---

    @Test
    void gravaArquivoPrivadoComOLinkSemRegistrarNoLog(CapturedOutput saida) throws Exception {
        CapturaLocalEnvioEmail captura = new CapturaLocalEnvioEmail(Path.of(privado()));
        captura.enviar(CONVITE);

        List<Path> gravados = arquivos(Path.of(privado()));
        assertEquals(1, gravados.size());
        String conteudo = Files.readString(gravados.get(0));
        assertTrue(conteudo.contains(LINK));
        assertTrue(conteudo.contains("NÃO foi enviado"));
        assertFalse(gravados.get(0).getFileName().toString().contains("exemplo"), "nome do arquivo sem e-mail");

        if (FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
            assertEquals("rw-------", PosixFilePermissions.toString(Files.getPosixFilePermissions(gravados.get(0))));
            assertEquals("rwx------", PosixFilePermissions.toString(Files.getPosixFilePermissions(Path.of(privado()))));
        }

        assertTrue(saida.getAll().contains("capturado (não enviado)"));
        assertFalse(saida.getAll().contains("TOKENSECRETO123"), "o token não pode ir para o log");
        assertFalse(saida.getAll().contains("motorista@exemplo.test"), "o destinatário não vai para o log");
    }

    @Test
    void destinatarioRealResponde503ENaoGrava(CapturedOutput saida) throws Exception {
        CapturaLocalEnvioEmail captura = new CapturaLocalEnvioEmail(Path.of(privado()));
        for (String real : List.of("joao@gmail.com", "ana@empresa.com.br", "x@notexample.com", "x@test", "semarroba")) {
            RegraNegocioException e = assertThrows(RegraNegocioException.class,
                    () -> captura.enviar(new MensagemEmail(real, "Convite", LINK)), real);
            assertEquals(HttpStatus.SERVICE_UNAVAILABLE, e.getStatus());
        }
        assertTrue(arquivos(Path.of(privado())).isEmpty());
        assertFalse(saida.getAll().contains("TOKENSECRETO123"));
    }

    @Test
    void dominiosFicticiosAceitos() {
        for (String ok : List.of("a@exemplo.test", "A@B.EXAMPLE", "a@example.com", "a@sub.example.org",
                "a@x.invalid", "a@host.localhost")) {
            assertTrue(CapturaLocalEnvioEmail.destinatarioFicticio(ok), ok);
        }
    }

    @Test
    void falhaAoGravarResponde503() throws Exception {
        Path bloqueio = tmp.resolve("contexto-local");
        Files.writeString(bloqueio, "arquivo no lugar do diretório");
        CapturaLocalEnvioEmail captura = new CapturaLocalEnvioEmail(bloqueio.resolve("emails-dev"));
        RegraNegocioException e = assertThrows(RegraNegocioException.class, () -> captura.enviar(CONVITE));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, e.getStatus());
    }
}
