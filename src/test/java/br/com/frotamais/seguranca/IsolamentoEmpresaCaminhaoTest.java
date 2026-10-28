
package br.com.frotamais.seguranca;

import java.util.UUID;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "app.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
})
@AutoConfigureMockMvc
class IsolamentoEmpresaCaminhaoTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void umaEmpresaNaoPodeAcessarAlterarOuExcluirCaminhaoDeOutra()
            throws Exception {

        String identificador = UUID.randomUUID().toString();

        String tokenA = registrarEmpresa(
                "Empresa A " + identificador.substring(0, 8),
                "empresa-a-" + identificador + "@teste.com"
        );

        String tokenB = registrarEmpresa(
                "Empresa B " + identificador.substring(0, 8),
                "empresa-b-" + identificador + "@teste.com"
        );

        // Placa única para evitar conflitos com execuções anteriores.
        String placa = "T" + identificador
                .replace("-", "")
                .substring(0, 7)
                .toUpperCase();

        String corpoCaminhao = objectMapper.writeValueAsString(
                new CaminhaoTeste(
                        placa,
                        "Caminhao da Empresa B",
                        2024,
                        "ATIVO",
                        300
                )
        );

        // A Empresa B cadastra um caminhão.
        MvcResult cadastro = mockMvc.perform(post("/api/caminhoes")
                .header("Authorization", "Bearer " + tokenB)
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpoCaminhao))
                .andDo(resultado -> {
                    if (resultado.getResponse().getStatus() != 201) {
                        System.err.println(
                                "Falha no cadastro do caminhão. HTTP "
                                + resultado.getResponse().getStatus()
                                + " - "
                                + resultado.getResponse().getContentAsString()
                        );
                    }
                })
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode respostaCadastro = objectMapper.readTree(
                cadastro.getResponse().getContentAsString()
        );

        long caminhaoId = respostaCadastro.get("id").asLong();

        // A Empresa A não deve visualizar caminhões da Empresa B.
        mockMvc.perform(get("/api/caminhoes")
                .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        // A Empresa A não pode consultar o caminhão da Empresa B.
        mockMvc.perform(get("/api/caminhoes/{id}", caminhaoId)
                .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNotFound());

        // A Empresa A não pode alterar o caminhão da Empresa B.
        mockMvc.perform(put("/api/caminhoes/{id}", caminhaoId)
                .header("Authorization", "Bearer " + tokenA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new CaminhaoTeste(
                                "Z" + identificador
                                        .replace("-", "")
                                        .substring(0, 7)
                                        .toUpperCase(),
                                "Caminhao alterado indevidamente",
                                2025,
                                "INATIVO",
                                400
                        )
                )))
                .andExpect(status().isNotFound());

        // A Empresa A não pode excluir o caminhão da Empresa B.
        mockMvc.perform(delete("/api/caminhoes/{id}", caminhaoId)
                .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNotFound());

        // O caminhão continua acessível e inalterado para a Empresa B.
        mockMvc.perform(get("/api/caminhoes/{id}", caminhaoId)
                .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(caminhaoId))
                .andExpect(jsonPath("$.placa").value(placa))
                .andExpect(jsonPath("$.modelo")
                        .value("Caminhao da Empresa B"))
                .andExpect(jsonPath("$.status").value("ATIVO"));
    }

    private String registrarEmpresa(String nomeEmpresa, String email)
            throws Exception {

        MvcResult resultado = mockMvc.perform(post("/api/auth/registro")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new RegistroTeste(
                                nomeEmpresa,
                                nomeEmpresa + " Dono",
                                email,
                                "SenhaTeste123!"
                        )
                )))
                .andDo(resposta -> {
                    if (resposta.getResponse().getStatus() != 201) {
                        System.err.println(
                                "Falha no registro da empresa. HTTP "
                                + resposta.getResponse().getStatus()
                                + " - "
                                + resposta.getResponse().getContentAsString()
                        );
                    }
                })
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readTree(
                resultado.getResponse().getContentAsString()
        ).get("token").asText();
    }

    private record RegistroTeste(
            String nomeEmpresa,
            String nome,
            String email,
            String senha) {
    }

    private record CaminhaoTeste(
            String placa,
            String modelo,
            Integer ano,
            String status,
            Integer capacidadeTanqueLitros) {
    }
}
