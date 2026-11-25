package br.com.frotamais.seguranca;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * CORS restrito à origem configurada e health check público sem detalhes,
 * pela cadeia de filtros real (sem jwt() do spring-security-test).
 */
@SpringBootTest(properties = {
    "app.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
    "app.cors.origens-permitidas=https://app.frotamais.test"
})
@AutoConfigureMockMvc
class CorsEHealthTest {

    private static final String FRONT = "https://app.frotamais.test";

    @Autowired
    private MockMvc mvc;

    @Test
    void preflightDaOrigemDoFrontEhAceito() throws Exception {
        mvc.perform(options("/api/auth/login")
                .header("Origin", FRONT)
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "content-type,authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", FRONT))
                .andExpect(header().string("Access-Control-Allow-Methods", containsString("POST")))
                .andExpect(header().string("Access-Control-Allow-Headers", containsString("authorization")))
                .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
    }

    @Test
    void preflightDeOutraOrigemEhRecusado() throws Exception {
        mvc.perform(options("/api/auth/login")
                .header("Origin", "https://malicioso.exemplo.test")
                .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    void metodoNaoUsadoPelaApiNaoEhLiberado() throws Exception {
        mvc.perform(options("/api/caminhoes/1")
                .header("Origin", FRONT)
                .header("Access-Control-Request-Method", "PATCH"))
                .andExpect(status().isForbidden());
    }

    @Test
    void respostaDeErroTambemLevaCabecalhoCorsParaOFront() throws Exception {
        // Sem isso o navegador esconderia o 401 do front-end.
        mvc.perform(get("/api/caminhoes").header("Origin", FRONT))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Access-Control-Allow-Origin", FRONT))
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void requisicaoDeOutraOrigemNaoRecebeCabecalhoCors() throws Exception {
        mvc.perform(post("/api/auth/login").header("Origin", "https://malicioso.exemplo.test")
                .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"a@b.test\",\"senha\":\"x\"}"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    void healthEhPublicoESoInformaOStatus() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist())
                .andExpect(jsonPath("$.details").doesNotExist())
                .andExpect(content().string(not(containsString("postgres"))));
        mvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
        mvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void demaisEndpointsDoActuatorNaoFicamExpostos() throws Exception {
        for (String rota : new String[] {"/actuator", "/actuator/env", "/actuator/beans", "/actuator/configprops",
                "/actuator/heapdump", "/actuator/loggers", "/actuator/mappings"}) {
            mvc.perform(get(rota)).andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/actuator/health")).andExpect(status().isUnauthorized());
    }
}
