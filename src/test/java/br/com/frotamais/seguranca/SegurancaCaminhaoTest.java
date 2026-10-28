
package br.com.frotamais.seguranca;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "app.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
})
@AutoConfigureMockMvc
class SegurancaCaminhaoTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void deveBloquearListagemSemToken() throws Exception {
        mockMvc.perform(get("/api/caminhoes"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deveBloquearListagemComTokenInvalido() throws Exception {
        mockMvc.perform(
                get("/api/caminhoes")
                        .header("Authorization", "Bearer token-invalido")
        ).andExpect(status().isUnauthorized());
    }

    @Test
    void deveBloquearMotoristaAoCadastrarCaminhao() throws Exception {
        mockMvc.perform(post("/api/caminhoes")
                .with(SecurityMockMvcRequestPostProcessors.jwt()
                        .jwt(jwt -> jwt
                                .subject("999")
                                .claim("empresaId", 1L)
                        )
                        .authorities(
                                new org.springframework.security.core.authority.SimpleGrantedAuthority(
                                        "ROLE_MOTORISTA"
                                )
                        ))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "placa": "ABC1234",
                        "modelo": "Caminhao de teste",
                        "ano": 2024,
                        "status": "ATIVO",
                        "capacidadeTanqueLitros": 300
                    }
                    """))
                .andExpect(status().isForbidden());
    }
}
