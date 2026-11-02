package br.com.frotamais.seguranca;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

import br.com.frotamais.usuario.Perfil;
import br.com.frotamais.usuario.Usuario;
import br.com.frotamais.usuario.UsuarioRepository;
import tools.jackson.databind.ObjectMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercita o filtro real com tokens reais, sem o jwt() do spring-security-test,
 * que contorna o conversor() de SecurityConfig.
 *
 * O @Transactional desfaz os dados de cada teste: o MockMvc roda na mesma
 * thread do teste, então as gravações entram na transação que sofre rollback.
 */
@SpringBootTest(properties = {
    "app.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
})
@AutoConfigureMockMvc
@Transactional
class AutenticacaoFluxoRealTest {

    private static final String SENHA = "SenhaTeste123!";
    private static final String CAMINHOES = "/api/caminhoes";
    private static final String CHAVE_DIFERENTE =
            "chave-de-teste-diferente-com-32-bytes-ou-mais";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtEncoder encoder;

    @Autowired
    private UsuarioRepository usuarios;

    @Test
    void loginValidoEmiteTokenAceitoNaRotaProtegida() throws Exception {
        String email = registrar();

        String resposta = mockMvc.perform(login(email, SENHA))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String token = objectMapper.readTree(resposta).get("token").asString();

        listarCom(token, 200);
    }

    @Test
    void loginRecusaSenhaIncorretaEmailInexistente() throws Exception {
        String email = registrar();

        mockMvc.perform(login(email, "SenhaErrada123!"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(login("inexistente-" + email, SENHA))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenAssinadoComOutraChaveERecusado() throws Exception {
        Usuario usuario = usuarios.findByEmail(registrar()).orElseThrow();
        JwtEncoder outroEncoder = new NimbusJwtEncoder(new ImmutableSecret<>(
                new SecretKeySpec(
                        CHAVE_DIFERENTE.getBytes(StandardCharsets.UTF_8),
                        "HmacSHA256")));

        listarCom(assinar(outroEncoder, claims(usuario).build()), 401);
    }

    @Test
    void tokenExpiradoERecusado() throws Exception {
        Usuario usuario = usuarios.findByEmail(registrar()).orElseThrow();
        Instant passado = Instant.now().minus(2, ChronoUnit.HOURS);

        listarCom(assinar(encoder, claims(usuario)
                .issuedAt(passado)
                .expiresAt(passado.plus(1, ChronoUnit.HOURS))
                .build()), 401);
    }

    @Test
    void tokenDeOutroEmissorERecusado() throws Exception {
        Usuario usuario = usuarios.findByEmail(registrar()).orElseThrow();

        listarCom(assinar(encoder, claims(usuario)
                .issuer("outro-emissor")
                .build()), 401);
    }

    @Test
    void tokenComEmpresaDivergenteERecusado() throws Exception {
        Usuario usuario = usuarios.findByEmail(registrar()).orElseThrow();
        long outraEmpresa = usuario.getEmpresa().getId() + 1;

        listarCom(assinar(encoder, claims(usuario)
                .claim("empresaId", outraEmpresa)
                .build()), 401);
    }

    @Test
    void tokenDeUsuarioInexistenteERecusado() throws Exception {
        Usuario usuario = usuarios.findByEmail(registrar()).orElseThrow();

        listarCom(assinar(encoder, claims(usuario)
                .subject(String.valueOf(Long.MAX_VALUE))
                .build()), 401);
    }

    @Test
    void desativarUsuarioInvalidaTokenJaEmitidoEBloqueiaLogin() throws Exception {
        String email = registrar();
        Usuario usuario = usuarios.findByEmail(email).orElseThrow();
        String token = assinar(encoder, claims(usuario).build());
        listarCom(token, 200);

        usuario.setAtivo(false);
        usuarios.saveAndFlush(usuario);

        listarCom(token, 401);
        mockMvc.perform(login(email, SENHA))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void perfilVemDoBancoEMotoristaNaoCadastraCaminhao() throws Exception {
        Usuario usuario = usuarios.findByEmail(registrar()).orElseThrow();
        // O token ainda diz DONO, mas a autorização deve seguir o banco.
        String token = assinar(encoder, claims(usuario).build());

        usuario.setPerfil(Perfil.MOTORISTA);
        usuarios.saveAndFlush(usuario);

        listarCom(token, 200);
        mockMvc.perform(post(CAMINHOES)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "placa": "ABC1D23",
                        "modelo": "Caminhao de teste",
                        "ano": 2024,
                        "status": "ATIVO",
                        "capacidadeTanqueLitros": 300
                    }
                    """))
                .andExpect(status().isForbidden());
    }

    private String registrar() throws Exception {
        String identificador = UUID.randomUUID().toString();
        String email = "fluxo-real-" + identificador + "@teste.com";

        mockMvc.perform(post("/api/auth/registro")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new RegistroTeste(
                        "Empresa " + identificador.substring(0, 8),
                        "Dono de teste",
                        email,
                        SENHA))))
                .andExpect(status().isCreated());
        return email;
    }

    private RequestBuilder login(String email, String senha) throws Exception {
        return post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new LoginTeste(email, senha)));
    }

    private void listarCom(String token, int statusEsperado) throws Exception {
        mockMvc.perform(get(CAMINHOES)
                .header("Authorization", "Bearer " + token))
                .andExpect(status().is(statusEsperado));
    }

    /** Parte de um token válido; cada teste muda só o que quer provar. */
    private JwtClaimsSet.Builder claims(Usuario usuario) {
        Instant agora = Instant.now();
        return JwtClaimsSet.builder()
                .issuer("frota-mais")
                .issuedAt(agora)
                .expiresAt(agora.plus(1, ChronoUnit.HOURS))
                .subject(usuario.getId().toString())
                .claim("empresaId", usuario.getEmpresa().getId())
                .claim("perfil", usuario.getPerfil().name());
    }

    private String assinar(JwtEncoder assinador, JwtClaimsSet claims) {
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return assinador.encode(JwtEncoderParameters.from(header, claims))
                .getTokenValue();
    }

    private record RegistroTeste(
            String nomeEmpresa,
            String nome,
            String email,
            String senha) {
    }

    private record LoginTeste(String email, String senha) {
    }
}
