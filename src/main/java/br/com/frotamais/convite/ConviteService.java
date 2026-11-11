package br.com.frotamais.convite;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HexFormat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.frotamais.auth.TokenResponse;
import br.com.frotamais.comum.Datas;
import br.com.frotamais.comum.RegraNegocioException;
import br.com.frotamais.email.EnvioEmail;
import br.com.frotamais.email.MensagemEmail;
import br.com.frotamais.seguranca.TokenService;
import br.com.frotamais.usuario.PoliticaSenha;
import br.com.frotamais.usuario.Usuario;

/**
 * Convites de ativação de conta.
 *
 * Token: 32 bytes de SecureRandom (256 bits) em Base64 URL. Vai só no link do
 * e-mail; o banco guarda o SHA-256 dele. Não aparece em log nem em resposta
 * da API. Uso único, com validade configurável, e invalidado quando um novo
 * convite é enviado ao mesmo usuário.
 *
 * O e-mail é enviado dentro da transação: se o envio falhar (ou não houver
 * SMTP configurado), nada é gravado e a resposta é 503.
 */
@Service
@Transactional
public class ConviteService {

    private static final Logger log = LoggerFactory.getLogger(ConviteService.class);
    private static final SecureRandom ALEATORIO = new SecureRandom();
    private static final String INVALIDO = "Convite inválido, já utilizado ou expirado. Peça um novo convite ao responsável.";

    private final ConviteRepository convites;
    private final ConfigConvites config;
    private final EnvioEmail email;
    private final PasswordEncoder encoder;
    private final TokenService tokens;

    public ConviteService(ConviteRepository convites, ConfigConvites config, EnvioEmail email,
            PasswordEncoder encoder, TokenService tokens) {
        this.convites = convites;
        this.config = config;
        this.email = email;
        this.encoder = encoder;
        this.tokens = tokens;
    }

    /** Falha antes de gravar qualquer coisa se o e-mail ou o link de ativação não estiverem configurados. */
    public void exigirConfiguracao() {
        if (!email.disponivel()) {
            throw RegraNegocioException.indisponivel(
                    "O envio de e-mail não está configurado no servidor. Nada foi enviado nem gravado.");
        }
        if (!config.urlConfigurada()) {
            throw RegraNegocioException.indisponivel(
                    "O endereço de ativação dos convites não está configurado no servidor. Nada foi gravado.");
        }
    }

    /**
     * Cria e envia um convite ao usuário pendente, invalidando os anteriores
     * ainda utilizáveis. Devolve o instante de expiração.
     */
    public Instant enviar(Usuario convidado, Usuario autor) {
        exigirConfiguracao();
        if (!convidado.isAtivacaoPendente()) {
            throw RegraNegocioException.conflito("O motorista já ativou a conta; não há convite a enviar.");
        }
        Instant agora = Datas.agora();
        Long empresaId = convidado.getEmpresa().getId();
        convites.pendentes(empresaId, convidado.getId(), agora).forEach(c -> c.invalidar(agora));

        String token = gerarToken();
        Instant expira = agora.plus(config.validade());
        convites.save(new Convite(convidado, convidado.getEmpresa(), hash(token), autor, agora, expira));
        convites.flush();

        email.enviar(new MensagemEmail(convidado.getEmail(), "Convite para acessar o Frota+",
                texto(convidado, autor, token, expira)));
        log.info("Convite de ativação enviado ao usuário {}.", convidado.getId());
        return expira;
    }

    /**
     * Cancela o convite pendente do usuário: o link deixa de valer na hora. O
     * registro fica (invalidado), e a conta continua aguardando ativação; o
     * dono pode enviar um novo convite depois. Não envia e-mail.
     */
    public void cancelar(Usuario convidado) {
        if (!convidado.isAtivacaoPendente()) {
            throw RegraNegocioException.conflito("O motorista já ativou a conta; não há convite a cancelar.");
        }
        Instant agora = Datas.agora();
        var pendentes = convites.travarPendentes(convidado.getEmpresa().getId(), convidado.getId(), agora);
        if (pendentes.isEmpty()) {
            throw RegraNegocioException.conflito(
                    "Não há convite pendente para cancelar: ele já foi usado, cancelado ou expirou.");
        }
        pendentes.forEach(c -> c.invalidar(agora));
        log.info("Convite de ativação do usuário {} cancelado.", convidado.getId());
    }

    /** Ativa a conta com a senha escolhida pelo próprio convidado e já devolve o login. */
    public TokenResponse ativar(String token, String senha) {
        Convite convite = convites.travarPorHash(hash(token))
                .orElseThrow(() -> RegraNegocioException.invalido(INVALIDO));
        Instant agora = Datas.agora();
        Usuario usuario = convite.getUsuario();
        if (!convite.valido(agora) || !usuario.isAtivacaoPendente()) {
            throw RegraNegocioException.invalido(INVALIDO);
        }
        PoliticaSenha.validarNova(senha);
        usuario.setSenhaHash(encoder.encode(senha));
        usuario.setAtivo(true);
        convite.marcarUsado(agora);
        convites.pendentes(usuario.getEmpresa().getId(), usuario.getId(), agora).stream()
                .filter(c -> !c.getId().equals(convite.getId()))
                .forEach(c -> c.invalidar(agora));
        log.info("Conta do usuário {} ativada por convite.", usuario.getId());
        return new TokenResponse(tokens.gerar(usuario), usuario.getNome(), usuario.getPerfil().name());
    }

    /** Expiração do convite mais recente do usuário, se ainda utilizável. */
    @Transactional(readOnly = true)
    public Instant expiracaoPendente(Usuario usuario) {
        return convites.findFirstByUsuarioIdAndEmpresaIdOrderByCriadoEmDescIdDesc(
                        usuario.getId(), usuario.getEmpresa().getId())
                .filter(c -> c.valido(Datas.agora()))
                .map(Convite::getExpiraEm)
                .orElse(null);
    }

    private String texto(Usuario convidado, Usuario autor, String token, Instant expira) {
        String link = config.urlAtivacao() + (config.urlAtivacao().contains("?") ? "&" : "?") + "token=" + token;
        String validade = DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm").withZone(Datas.FUSO).format(expira);
        return "Olá, " + convidado.getNome() + ".\n\n"
                + autor.getNome() + " convidou você para acessar o Frota+ como motorista da empresa "
                + convidado.getEmpresa().getNome() + ".\n\n"
                + "Para ativar sua conta e criar sua senha, acesse:\n" + link + "\n\n"
                + "O link é pessoal, vale uma única vez e expira em " + validade + " (horário de Brasília).\n"
                + "Se você não esperava este convite, ignore esta mensagem.\n";
    }

    static String gerarToken() {
        byte[] bytes = new byte[32];
        ALEATORIO.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível.", e);
        }
    }
}
