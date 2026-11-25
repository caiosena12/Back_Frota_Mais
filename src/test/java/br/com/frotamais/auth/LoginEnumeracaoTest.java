package br.com.frotamais.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import br.com.frotamais.comum.RegraNegocioException;
import br.com.frotamais.empresa.EmpresaRepository;
import br.com.frotamais.seguranca.LimitesTentativas;
import br.com.frotamais.seguranca.TokenService;
import br.com.frotamais.usuario.Perfil;
import br.com.frotamais.usuario.Usuario;
import br.com.frotamais.usuario.UsuarioRepository;
import br.com.frotamais.viagem.ApiViagemTeste;
import tools.jackson.databind.JsonNode;

/**
 * O login não revela se um e-mail existe: mesma resposta para e-mail
 * inexistente, senha errada, conta inativa e convite ainda não aceito, e o
 * mesmo trabalho criptográfico (uma verificação BCrypt) em todos os casos.
 */
class LoginEnumeracaoTest extends ApiViagemTeste {

    private static final String RESPOSTA_401 =
            "{\"status\":401,\"erro\":\"Não autenticado\",\"detalhes\":[\"E-mail ou senha inválidos.\"]}";

    private String login(String email, String senha, int status) throws Exception {
        return mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(corpo("email", email, "senha", senha))))
                .andReturn().getResponse().getContentAsString();
    }

    // --- pela API, com contas reais ---

    @Test
    void todasAsFalhasRespondemExatamenteIgual() throws Exception {
        Conta dono = registrar();
        long caminhao = criarCaminhao(dono.token());
        Motorista inativo = criarMotorista(dono.token(), caminhao);
        chamar(put("/api/motoristas/" + inativo.id()), dono.token(), corpo("nome", "Inativo", "ativo", false), 200);
        String pendente = "pendente-" + UUID.randomUUID() + "@teste.com";
        chamar(post("/api/motoristas"), dono.token(), corpo("nome", "Pendente", "email", pendente, "caminhaoIds", List.of()), 201);

        List<String> respostas = List.of(
                login("nao-existe-" + UUID.randomUUID() + "@teste.com", "SenhaTeste123!", 401),
                login(dono.email(), "SenhaErrada123", 401),
                login(inativo.email(), "Motorista123", 401),
                login(inativo.email(), "SenhaErrada123", 401),
                login(pendente, "Qualquer123", 401));
        for (String r : respostas) {
            assertEquals(RESPOSTA_401, r);
        }
    }

    @Test
    void loginValidoMantemOContrato() throws Exception {
        Conta dono = registrar();
        JsonNode r = objectMapper.readTree(login(dono.email(), "SenhaTeste123!", 200));
        assertEquals(Set.of("token", "nome", "perfil"), Set.copyOf(r.propertyNames()));
        assertEquals("DONO", r.get("perfil").asString());
        Conta outro = registrar();
        long caminhao = criarCaminhao(outro.token());
        Motorista m = criarMotorista(outro.token(), caminhao);
        assertEquals("MOTORISTA", objectMapper.readTree(login(m.email(), "Motorista123", 200)).get("perfil").asString());
    }

    @Test
    void tempoDoEmailInexistenteEComparavelAoDaSenhaErrada() throws Exception {
        Conta dono = registrar();
        for (int i = 0; i < 3; i++) {
            login(dono.email(), "Aquecimento1", 401);
            login("aquecimento-" + i + "@teste.com", "Aquecimento1", 401);
        }
        List<Long> inexistente = new ArrayList<>();
        List<Long> senhaErrada = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            long t0 = System.nanoTime();
            login("nao-existe-" + UUID.randomUUID() + "@teste.com", "SenhaErrada123", 401);
            inexistente.add(System.nanoTime() - t0);
            t0 = System.nanoTime();
            login(dono.email(), "SenhaErrada123", 401);
            senhaErrada.add(System.nanoTime() - t0);
        }
        long a = mediana(inexistente);
        long b = mediana(senhaErrada);
        // Antes da correção o e-mail inexistente levava ~2% do tempo (sem BCrypt).
        // Limite folgado para não oscilar em máquinas lentas: ambos fazem um BCrypt.
        assertTrue(a > b * 0.4 && b > a * 0.4, () -> "inexistente=" + a / 1_000_000 + "ms, senha errada=" + b / 1_000_000 + "ms");
    }

    private static long mediana(List<Long> valores) {
        List<Long> v = new ArrayList<>(valores);
        Collections.sort(v);
        return v.get(v.size() / 2);
    }

    // --- unidade: exatamente uma verificação BCrypt em cada caso ---

    private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    private final PasswordEncoder encoder = spy(new BCryptPasswordEncoder(4));
    private final TokenService tokens = mock(TokenService.class);

    private AuthService servico() {
        AuthService s = new AuthService(usuarios, mock(EmpresaRepository.class), encoder, tokens,
                new LimitesTentativas(false, 100, 1, 1, 1, 1, 1, 1, 1, 1));
        clearInvocations(encoder);
        return s;
    }

    private Usuario usuario(String senha, boolean ativo) {
        Usuario u = new Usuario();
        u.setNome("Teste");
        u.setEmail("u@teste.com");
        u.setPerfil(Perfil.MOTORISTA);
        u.setAtivo(ativo);
        u.setSenhaHash(senha == null ? null : new BCryptPasswordEncoder(4).encode(senha));
        return u;
    }

    private void falhaComUmaVerificacao(AuthService s, String senha) {
        RegraNegocioException e = assertThrows(RegraNegocioException.class,
                () -> s.login(new LoginRequest("u@teste.com", senha)));
        assertEquals(401, e.getStatus().value());
        assertEquals("E-mail ou senha inválidos.", e.getMessage());
        verify(encoder, times(1)).matches(eq(senha), anyString());
    }

    @Test
    void emailInexistenteConfereContraHashFicticio() {
        when(usuarios.findByEmail("u@teste.com")).thenReturn(Optional.empty());
        falhaComUmaVerificacao(servico(), "Qualquer123");
    }

    @Test
    void contaSemSenhaConfereContraHashFicticio() {
        when(usuarios.findByEmail("u@teste.com")).thenReturn(Optional.of(usuario(null, false)));
        falhaComUmaVerificacao(servico(), "Qualquer123");
    }

    @Test
    void contaInativaTambemFazAVerificacao() {
        when(usuarios.findByEmail("u@teste.com")).thenReturn(Optional.of(usuario("Correta123", false)));
        falhaComUmaVerificacao(servico(), "Correta123");
    }

    @Test
    void senhaErradaFazUmaVerificacao() {
        when(usuarios.findByEmail("u@teste.com")).thenReturn(Optional.of(usuario("Correta123", true)));
        falhaComUmaVerificacao(servico(), "Errada1234");
    }

    @Test
    void loginCorretoFazUmaVerificacao() {
        Usuario u = usuario("Correta123", true);
        when(usuarios.findByEmail("u@teste.com")).thenReturn(Optional.of(u));
        when(tokens.gerar(any())).thenReturn("jwt");
        AuthService s = servico();
        assertNotNull(s.login(new LoginRequest("u@teste.com", "Correta123")));
        verify(encoder, times(1)).matches(eq("Correta123"), anyString());
    }
}
