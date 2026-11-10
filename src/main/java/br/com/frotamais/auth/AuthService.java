package br.com.frotamais.auth;

import java.nio.charset.StandardCharsets;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import br.com.frotamais.comum.RegraNegocioException;

import br.com.frotamais.empresa.Empresa;
import br.com.frotamais.empresa.EmpresaRepository;
import br.com.frotamais.seguranca.TokenService;
import br.com.frotamais.usuario.Perfil;
import br.com.frotamais.usuario.PoliticaSenha;
import br.com.frotamais.usuario.Usuario;
import br.com.frotamais.usuario.UsuarioRepository;

@Service
public class AuthService {

    private static final int BCRYPT_MAX_BYTES = 72;

    private final UsuarioRepository usuarios;
    private final EmpresaRepository empresas;
    private final PasswordEncoder encoder;
    private final TokenService tokens;

    public AuthService(
            UsuarioRepository usuarios,
            EmpresaRepository empresas,
            PasswordEncoder encoder,
            TokenService tokens) {
        this.usuarios = usuarios;
        this.empresas = empresas;
        this.encoder = encoder;
        this.tokens = tokens;
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

    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest request) {
        validarBytesSenha(request.senha(), HttpStatus.UNAUTHORIZED);

        Usuario usuario = usuarios.findByEmail(normalizarEmail(request.email()))
                .filter(Usuario::isAtivo)
                .filter(u -> encoder.matches(request.senha(), u.getSenhaHash()))
                .orElseThrow(() -> RegraNegocioException.naoAutenticado("E-mail ou senha inválidos."));

        return resposta(usuario);
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
