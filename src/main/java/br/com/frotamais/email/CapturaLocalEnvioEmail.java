package br.com.frotamais.email;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import br.com.frotamais.comum.RegraNegocioException;

/**
 * Captura de e-mails para desenvolvimento: em vez de enviar, grava cada
 * mensagem num arquivo local. Só é escolhida pelo EmailConfig com o perfil
 * dev ativo e app.email.captura-local.ativa=true; nunca envia nada pela rede.
 *
 * Proteções:
 * - o diretório precisa estar dentro de uma pasta "contexto-local" (privada e
 *   ignorada pelo Git); diretório 700 e arquivos 600 onde houver POSIX;
 * - só aceita destinatários de domínios reservados para teste (RFC 2606/6761),
 *   para não gravar dados de pessoas reais; outro domínio responde 503 e a
 *   operação é desfeita, como uma falha do SMTP;
 * - o log registra só que houve captura e o nome do arquivo, nunca o
 *   destinatário, o texto ou o link de ativação.
 */
class CapturaLocalEnvioEmail implements EnvioEmail {

    private static final Logger log = LoggerFactory.getLogger(CapturaLocalEnvioEmail.class);

    static final String PASTA_PRIVADA = "contexto-local";

    /** Domínios reservados para teste e documentação (RFC 2606 e RFC 6761). */
    static final List<String> SUFIXOS_FICTICIOS = List.of(
            ".test", ".example", ".invalid", ".localhost",
            "@example.com", "@example.net", "@example.org",
            ".example.com", ".example.net", ".example.org");

    private static final DateTimeFormatter CARIMBO =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss.SSS'Z'").withZone(ZoneOffset.UTC);
    private static final SecureRandom ALEATORIO = new SecureRandom();
    private static final boolean POSIX = FileSystems.getDefault().supportedFileAttributeViews().contains("posix");

    private final Path diretorio;

    CapturaLocalEnvioEmail(Path diretorio) {
        this.diretorio = validarDiretorio(diretorio);
    }

    static Path validarDiretorio(Path diretorio) {
        if (diretorio == null || diretorio.toString().isBlank()) {
            throw new IllegalStateException("app.email.captura-local.diretorio não definido.");
        }
        Path absoluto = diretorio.toAbsolutePath().normalize();
        boolean dentroDaPastaPrivada = false;
        for (Path parte : absoluto) {
            if (parte.toString().equals(PASTA_PRIVADA)) {
                dentroDaPastaPrivada = true;
                break;
            }
        }
        if (!dentroDaPastaPrivada) {
            throw new IllegalStateException("A captura local de e-mails só grava dentro de uma pasta '"
                    + PASTA_PRIVADA + "' (privada e ignorada pelo Git).");
        }
        return absoluto;
    }

    static boolean destinatarioFicticio(String email) {
        if (email == null) {
            return false;
        }
        String e = email.trim().toLowerCase(Locale.ROOT);
        int arroba = e.lastIndexOf('@');
        if (arroba <= 0 || arroba == e.length() - 1) {
            return false;
        }
        return SUFIXOS_FICTICIOS.stream().anyMatch(e::endsWith);
    }

    Path diretorio() {
        return diretorio;
    }

    @Override
    public void enviar(MensagemEmail mensagem) {
        if (!destinatarioFicticio(mensagem.para())) {
            throw RegraNegocioException.indisponivel("A captura local de e-mails (desenvolvimento) só aceita "
                    + "destinatários fictícios, como nome@exemplo.test ou nome@example.com. Nada foi gravado.");
        }
        String nome = CARIMBO.format(Instant.now()) + "-" + HexFormat.of().formatHex(aleatorio()) + ".txt";
        try {
            criarDiretorio();
            Path arquivo = diretorio.resolve(nome);
            if (POSIX) {
                Files.createFile(arquivo, permissoes("rw-------"));
            } else {
                Files.createFile(arquivo);
            }
            Files.writeString(arquivo, conteudo(mensagem), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("Falha ao gravar e-mail capturado: {}", e.getClass().getSimpleName());
            throw RegraNegocioException.indisponivel(
                    "Não foi possível registrar o e-mail de desenvolvimento. Nada foi gravado; tente novamente.");
        }
        log.info("E-mail de desenvolvimento capturado (não enviado): {}", nome);
    }

    private void criarDiretorio() throws IOException {
        if (Files.isDirectory(diretorio)) {
            return;
        }
        if (POSIX) {
            Files.createDirectories(diretorio, permissoes("rwx------"));
        } else {
            Files.createDirectories(diretorio);
        }
    }

    private static FileAttribute<?> permissoes(String texto) {
        return PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString(texto));
    }

    private static byte[] aleatorio() {
        byte[] b = new byte[4];
        ALEATORIO.nextBytes(b);
        return b;
    }

    private static String conteudo(MensagemEmail m) {
        return "Capturado pelo Frota+ em desenvolvimento: NÃO foi enviado.\n"
                + "Para: " + m.para() + "\n"
                + "Assunto: " + m.assunto() + "\n\n"
                + m.texto();
    }
}
