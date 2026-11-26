package br.com.frotamais.email;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import br.com.frotamais.comum.RegraNegocioException;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;

/**
 * O SmtpEnvioEmail falando o protocolo SMTP de verdade (JavaMail), contra um
 * servidor mínimo dentro do teste. Não substitui o teste com um provedor real
 * (que exige credenciais): prova que a mensagem sai com remetente, destinatário,
 * assunto e link, e que falha de conexão, autenticação recusada e servidor mudo
 * (timeout) viram 503 sem detalhes internos, em tempo limitado.
 */
class SmtpProtocoloTest {

    // Tamanho real: URL do front + token de 43 caracteres (passa de 76, força quebra de linha no quoted-printable).
    private static final String LINK = "https://animated-exemplo-4200.app.github.dev/ativar-conta?token=Qx3vT9_aB-7kLmN0pR2sU4wY6zA8cE1gH3jK5mO7qS9";
    private static final MensagemEmail CONVITE = new MensagemEmail("motorista@exemplo.test",
            "Convite para acessar o Frota+", "Olá.\nPara ativar sua conta, acesse:\n" + LINK + "\n");

    private ServerSocket servidor;

    @AfterEach
    void fechar() throws IOException {
        if (servidor != null) {
            servidor.close();
        }
    }

    /** Servidor SMTP de uma conexão; responde aos comandos e devolve a transcrição recebida. */
    private CompletableFuture<String> servidorSmtp(boolean recusarAutenticacao) throws IOException {
        servidor = new ServerSocket(0);
        return CompletableFuture.supplyAsync(() -> {
            StringBuilder recebido = new StringBuilder();
            try (Socket s = servidor.accept();
                    BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
                    OutputStream out = s.getOutputStream()) {
                responder(out, "220 teste ESMTP");
                String linha;
                boolean dados = false;
                while ((linha = in.readLine()) != null) {
                    recebido.append(linha).append('\n');
                    if (dados) {
                        if (linha.equals(".")) {
                            dados = false;
                            responder(out, "250 OK enfileirada");
                        }
                        continue;
                    }
                    String cmd = linha.toUpperCase();
                    if (cmd.startsWith("EHLO")) {
                        responder(out, "250-teste\r\n250 AUTH PLAIN LOGIN");
                    } else if (cmd.startsWith("AUTH")) {
                        responder(out, recusarAutenticacao ? "535 5.7.8 Authentication failed" : "235 OK");
                    } else if (cmd.startsWith("DATA")) {
                        dados = true;
                        responder(out, "354 Envie");
                    } else if (cmd.startsWith("QUIT")) {
                        responder(out, "221 Tchau");
                        break;
                    } else {
                        responder(out, "250 OK");
                    }
                }
            } catch (IOException e) {
                recebido.append("ERRO ").append(e.getClass().getSimpleName());
            }
            return recebido.toString();
        });
    }

    private static void responder(OutputStream out, String texto) throws IOException {
        out.write((texto + "\r\n").getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    /** Corpo da mensagem decodificado como um cliente de e-mail faria (cabeçalhos ficam de fora). */
    private static String corpoDecodificado(String transcricao) throws Exception {
        String dados = transcricao.substring(transcricao.indexOf("DATA\n") + 5, transcricao.lastIndexOf("\n.\n"));
        MimeMessage m = new MimeMessage(Session.getInstance(new Properties()),
                new ByteArrayInputStream(dados.getBytes(StandardCharsets.UTF_8)));
        return m.getContent().toString();
    }

    private JavaMailSenderImpl remetente(int porta, boolean autenticar) {
        JavaMailSenderImpl m = new JavaMailSenderImpl();
        m.setHost("localhost");
        m.setPort(porta);
        Properties p = m.getJavaMailProperties();
        // Os mesmos timeouts do application.properties, menores para o teste.
        p.put("mail.smtp.connectiontimeout", "1000");
        p.put("mail.smtp.timeout", "1000");
        p.put("mail.smtp.writetimeout", "1000");
        if (autenticar) {
            m.setUsername("usuario-smtp");
            m.setPassword("chave-smtp-de-teste");
            p.put("mail.smtp.auth", "true");
        }
        return m;
    }

    @Test
    void enviaRemetenteDestinatarioAssuntoELinkPeloProtocoloSmtp() throws Exception {
        CompletableFuture<String> transcricao = servidorSmtp(false);
        EnvioEmail envio = EmailConfig.escolher(remetente(servidor.getLocalPort(), true), "nao-responda@exemplo.test");
        envio.enviar(CONVITE);
        String t = transcricao.get(5, TimeUnit.SECONDS);
        assertTrue(t.contains("AUTH"), t);
        assertTrue(t.contains("MAIL FROM:<nao-responda@exemplo.test>"), t);
        assertTrue(t.contains("RCPT TO:<motorista@exemplo.test>"), t);
        assertTrue(t.contains("Subject:"), t);
        assertTrue(corpoDecodificado(t).contains(LINK), "o link chega inteiro depois de decodificado: " + t);
    }

    @Test
    void autenticacaoRecusadaVira503SemDetalhesDoServidor() throws Exception {
        servidorSmtp(true);
        EnvioEmail envio = EmailConfig.escolher(remetente(servidor.getLocalPort(), true), "nao-responda@exemplo.test");
        RegraNegocioException e = assertThrows(RegraNegocioException.class, () -> envio.enviar(CONVITE));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, e.getStatus());
        assertFalse(e.getMessage().contains("535"), e.getMessage());
        assertTrue(e.getMessage().contains("Nada foi gravado"), e.getMessage());
    }

    @Test
    void servidorQueAceitaENaoRespondeEstouraOTimeoutEVira503() throws Exception {
        servidor = new ServerSocket(0); // aceita a conexão (backlog) e nunca manda o 220
        EnvioEmail envio = EmailConfig.escolher(remetente(servidor.getLocalPort(), false), "nao-responda@exemplo.test");
        long inicio = System.nanoTime();
        RegraNegocioException e = assertThrows(RegraNegocioException.class, () -> envio.enviar(CONVITE));
        long ms = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - inicio);
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, e.getStatus());
        assertTrue(ms < 5000, "timeout respeitado: " + ms + " ms");
    }

    @Test
    void servidorForaDoArVira503() throws Exception {
        int porta;
        try (ServerSocket livre = new ServerSocket(0)) {
            porta = livre.getLocalPort();
        }
        EnvioEmail envio = EmailConfig.escolher(remetente(porta, false), "nao-responda@exemplo.test");
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE,
                assertThrows(RegraNegocioException.class, () -> envio.enviar(CONVITE)).getStatus());
    }
}
