package br.com.frotamais.auth;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import br.com.frotamais.empresa.Empresa;
import br.com.frotamais.empresa.EmpresaRepository;
import br.com.frotamais.seguranca.TokenService;
import br.com.frotamais.usuario.Perfil;
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
        validarBytesSenha(request.senha(), HttpStatus.BAD_REQUEST);

        String email = normalizarEmail(request.email());
        if (usuarios.existsByEmail(email)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "E-mail já cadastrado.");
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
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "E-mail ou senha inválidos."));

        return resposta(usuario);
    }

    private void validarBytesSenha(String senha, HttpStatus status) {
        if (senha.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_BYTES) {
            throw new ResponseStatusException(
                    status, "A senha ultrapassa o limite permitido pelo BCrypt.");
        }
    }

    private String normalizarEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private TokenResponse resposta(Usuario usuario) {
        return new TokenResponse(
                tokens.gerar(usuario),
                usuario.getNome(),
                usuario.getPerfil().name());
    }
}
