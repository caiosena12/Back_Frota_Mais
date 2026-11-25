package br.com.frotamais.auth;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import br.com.frotamais.comum.RegraNegocioException;

import br.com.frotamais.empresa.Empresa;
import br.com.frotamais.empresa.EmpresaRepository;
import br.com.frotamais.seguranca.LimitesTentativas;
import br.com.frotamais.seguranca.TokenService;
import br.com.frotamais.usuario.Perfil;
import br.com.frotamais.usuario.PoliticaSenha;
import br.com.frotamais.usuario.Usuario;
import br.com.frotamais.usuario.UsuarioRepository;

@Service
public class AuthService {

    private static final int BCRYPT_MAX_BYTES = 72;
    private static final String CREDENCIAIS_INVALIDAS = "E-mail ou senha inválidos.";

    private final UsuarioRepository usuarios;
    private final EmpresaRepository empresas;
    private final PasswordEncoder encoder;
    private final TokenService tokens;
    private final LimitesTentativas limites;

    /**
     * Hash BCrypt de uma senha aleatória, gerado ao subir com o mesmo encoder
     * (mesmo custo). O login confere a senha contra ele quando a conta não
     * existe ou ainda não tem senha, para que todos os casos de falha façam o
     * mesmo trabalho criptográfico e o tempo não revele quais e-mails existem.
     */
    private final String hashFicticio;

    public AuthService(
            UsuarioRepository usuarios,
            EmpresaRepository empresas,
            PasswordEncoder encoder,
            TokenService tokens,
            LimitesTentativas limites) {
        this.usuarios = usuarios;
        this.empresas = empresas;
        this.encoder = encoder;
        this.tokens = tokens;
        this.limites = limites;
        this.hashFicticio = encoder.encode(UUID.randomUUID().toString());
    }

    @Transactional
    public TokenResponse registrar(RegistroRequest request) {
        PoliticaSenha.validarNova(request.senha());

        String email = normalizarEmail(request.email());
        if (usuarios.existsByEmail(email)) {
            throw RegraNegocioException.conflito("E-mail já cadastrado.");
        }

        Empresa empresa = new Empresa();
        empresa.setNome(request.nomeEmpresa().trim());
        empresas.save(empresa);

        Usuario dono = new Usuario();
        dono.setNome(request.nome().trim());
        dono.setEmail(email);
        dono.setSenhaHash(encoder.encode(request.senha()));
        dono.setPerfil(Perfil.DONO);
        dono.setEmpresa(empresa);
        usuarios.save(dono);

        return resposta(dono);
    }

    /**
     * Mesma resposta (401, mesma mensagem) e uma única verificação BCrypt em
     * todos os casos de falha: e-mail inexistente, senha errada, conta inativa
     * ou convite ainda não aceito. Falhas contam por e-mail
     * (LimitesTentativas); acima do limite, 429 antes de conferir a senha,
     * exista a conta ou não.
     */
    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest request) {
        validarBytesSenha(request.senha(), HttpStatus.UNAUTHORIZED);
        String email = normalizarEmail(request.email());
        limites.exigirContaLiberada(email);

        Optional<Usuario> encontrado = usuarios.findByEmail(email);
        String hash = encontrado.map(Usuario::getSenhaHash).orElse(null);
        boolean senhaConfere = encoder.matches(request.senha(), hash != null ? hash : hashFicticio);

        if (hash == null || !senhaConfere || !encontrado.get().isAtivo()) {
            limites.registrarFalhaLogin(email);
            throw RegraNegocioException.naoAutenticado(CREDENCIAIS_INVALIDAS);
        }
        limites.registrarSucessoLogin(email);
        return resposta(encontrado.get());
    }

    /**
     * Troca a senha do próprio usuário logado, conferindo a atual. Os tokens já
     * emitidos continuam válidos até expirar (JWT sem estado).
     */
    @Transactional
    public void trocarSenha(Long usuarioId, TrocaSenhaRequest request) {
        Usuario usuario = usuarios.findById(usuarioId)
                .filter(Usuario::isAtivo)
                .orElseThrow(() -> RegraNegocioException.naoAutenticado("Usuário inativo ou inexistente."));
        validarBytesSenha(request.senhaAtual(), HttpStatus.BAD_REQUEST);
        if (!encoder.matches(request.senhaAtual(), usuario.getSenhaHash())) {
            throw RegraNegocioException.invalido("Senha atual incorreta.");
        }
        PoliticaSenha.validarNova(request.novaSenha());
        usuario.setSenhaHash(encoder.encode(request.novaSenha()));
    }

    private void validarBytesSenha(String senha, HttpStatus status) {
        if (senha.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_BYTES) {
            throw new RegraNegocioException(status, "A senha ultrapassa o limite permitido pelo BCrypt.");
        }
    }

    private String normalizarEmail(String email) {
        return Usuario.normalizarEmail(email);
    }

    private TokenResponse resposta(Usuario usuario) {
        return new TokenResponse(
                tokens.gerar(usuario),
                usuario.getNome(),
                usuario.getPerfil().name());
    }
}
