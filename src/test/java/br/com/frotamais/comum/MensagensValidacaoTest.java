package br.com.frotamais.comum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import br.com.frotamais.viagem.ApiViagemTeste;
import tools.jackson.databind.JsonNode;

/**
 * Mensagens de validação em português (ValidationMessages.properties), com o
 * campo identificado e o formato {"status","erro","detalhes"} preservado, em
 * vários módulos. As requisições do MockMvc usam locale inglês por padrão, e
 * dois testes forçam inglês no Accept-Language e na JVM: a tradução não pode
 * depender do idioma da máquina nem do cliente.
 */
class MensagensValidacaoTest extends ApiViagemTeste {

    private static final String HOJE = "2026-01-10";

    private static List<String> detalhes400(JsonNode erro) {
        assertEquals(400, erro.get("status").asInt(), erro::toString);
        assertEquals("Dados inválidos", erro.get("erro").asString(), erro::toString);
        assertEquals(3, erro.size(), () -> "campos inesperados: " + erro);
        List<String> lista = new ArrayList<>();
        erro.get("detalhes").forEach(d -> lista.add(d.asString()));
        assertFalse(lista.isEmpty(), erro::toString);
        for (String d : lista) {
            assertFalse(d.matches("(?i).*\\b(must|should|size must|well-formed|greater than|less than)\\b.*"),
                    () -> "mensagem em inglês: " + d);
        }
        return lista;
    }

    private static void contemExato(List<String> detalhes, String esperado) {
        assertTrue(detalhes.contains(esperado), () -> "esperado '" + esperado + "' em " + detalhes);
    }

    private Map<String, Object> caminhaoValido() {
        return corpo("placa", "MSG1A23", "marca", "Volvo", "modelo", "FH", "ano", 2022, "eixos", 6);
    }

    @Test
    void caminhaoObrigatoriosVaziosENulos() throws Exception {
        Map<String, Object> c = caminhaoValido();
        c.put("modelo", "   ");
        c.put("marca", null);
        c.put("ano", null);
        List<String> d = detalhes400(chamar(POST("/api/caminhoes"), registrar().token(), c, 400));
        contemExato(d, "modelo: não pode ficar em branco");
        contemExato(d, "marca: não pode ficar em branco");
        contemExato(d, "ano: é obrigatório");
    }

    @Test
    void caminhaoLimitesNumericosETamanho() throws Exception {
        String token = registrar().token();
        Map<String, Object> c = caminhaoValido();
        c.put("eixos", 0);
        c.put("ano", 1949);
        c.put("placa", "ABCDEFGHIJKLM");
        c.put("capacidadeTanqueLitros", -5);
        List<String> d = detalhes400(chamar(POST("/api/caminhoes"), token, c, 400));
        contemExato(d, "eixos: deve ser maior ou igual a 1");
        contemExato(d, "ano: deve ser maior ou igual a 1950");
        contemExato(d, "placa: deve ter no máximo 12 caracteres");
        contemExato(d, "capacidadeTanqueLitros: deve ser maior que zero");

        c = caminhaoValido();
        c.put("eixos", 21);
        c.put("ano", 2101);
        d = detalhes400(chamar(POST("/api/caminhoes"), token, c, 400));
        contemExato(d, "eixos: deve ser menor ou igual a 20");
        contemExato(d, "ano: deve ser menor ou igual a 2100");
    }

    @Test
    void limitesContinuamAceitandoOsMesmosValores() throws Exception {
        String token = registrar().token();
        Map<String, Object> c = caminhaoValido();
        c.put("eixos", 1);
        c.put("ano", 1950);
        chamar(POST("/api/caminhoes"), token, c, 201);
        c = caminhaoValido();
        c.put("placa", "MSG2B34");
        c.put("eixos", 20);
        c.put("ano", 2100);
        chamar(POST("/api/caminhoes"), token, c, 201);
    }

    @Test
    void registroTextoCurtoLongoEEmailInvalido() throws Exception {
        Map<String, Object> r = corpo("nomeEmpresa", "Empresa", "nome", "Dono", "email", "nao-e-email", "senha", "abc");
        List<String> d = detalhes400(chamar(POST("/api/auth/registro"), null, r, 400));
        contemExato(d, "senha: deve ter entre 8 e 72 caracteres");
        contemExato(d, "email: deve ser um endereço de e-mail válido");

        r = corpo("nomeEmpresa", "x".repeat(121), "nome", "Dono", "email", "a@exemplo.test", "senha", "Senha1234");
        d = detalhes400(chamar(POST("/api/auth/registro"), null, r, 400));
        contemExato(d, "nomeEmpresa: deve ter no máximo 120 caracteres");
    }

    @Test
    void viagemDecimaisDigitosEListas() throws Exception {
        String token = registrar().token();
        long caminhao = criarCaminhao(token);
        List<Map<String, Object>> muitos = new ArrayList<>(Collections.nCopies(201,
                corpo("tipo", "ARLA", "data", HOJE, "litros", "10", "valorTotal", "40")));
        Map<String, Object> v = corpo("caminhaoId", caminhao, "dataInicio", null,
                "valorFrete", "100.123", "kmInicialCarregado", -1, "consumoMedioCarregado", "0",
                "abastecimentos", muitos);
        List<String> d = detalhes400(chamar(POST(VIAGENS), token, v, 400));
        contemExato(d, "dataInicio: é obrigatório");
        contemExato(d, "valorFrete: deve ter no máximo 13 dígitos na parte inteira e 2 casas decimais");
        contemExato(d, "kmInicialCarregado: deve ser maior ou igual a zero");
        contemExato(d, "consumoMedioCarregado: deve ser maior que 0.0");
        contemExato(d, "abastecimentos: deve ter no máximo 200 itens");
    }

    @Test
    void itemDeListaIdentificaOIndiceDoCampo() throws Exception {
        String token = registrar().token();
        long caminhao = criarCaminhao(token);
        Map<String, Object> v = corpo("caminhaoId", caminhao, "dataInicio", HOJE, "kmInicialCarregado", 1000,
                "abastecimentos", List.of(corpo("tipo", "DIESEL", "data", HOJE, "litros", null, "valorTotal", "0")));
        List<String> d = detalhes400(chamar(POST(VIAGENS), token, v, 400));
        contemExato(d, "abastecimentos[0].litros: é obrigatório");
        contemExato(d, "abastecimentos[0].valorTotal: deve ser maior que zero");
    }

    @Test
    void lancamentoEManutencao() throws Exception {
        String token = registrar().token();
        long caminhao = criarCaminhao(token);
        long viagem = criarViagem(token, corpo("caminhaoId", caminhao, "dataInicio", HOJE, "kmInicialCarregado", 1000));

        List<String> d = detalhes400(chamar(POST(VIAGENS + "/" + viagem + "/despesas-extras"), token,
                corpo("descricao", "", "data", HOJE, "valor", "-1"), 400));
        contemExato(d, "descricao: não pode ficar em branco");
        contemExato(d, "valor: deve ser maior que zero");

        d = detalhes400(chamar(POST("/api/manutencoes"), token,
                corpo("caminhaoId", caminhao, "data", HOJE, "descricao", "x".repeat(501), "valor", "10"), 400));
        contemExato(d, "descricao: deve ter no máximo 500 caracteres");
    }

    @Test
    void motoristaListaComItemNulo() throws Exception {
        List<String> d = detalhes400(chamar(PUT("/api/motoristas/1/caminhoes"), registrar().token(),
                corpo("caminhaoIds", java.util.Arrays.asList(1L, null)), 400));
        contemExato(d, "caminhaoIds[1]: é obrigatório");
    }

    @Test
    void formatoInvalidoContinuaEmPortugues() throws Exception {
        String token = registrar().token();
        List<String> d = detalhes400(chamar(POST(VIAGENS), token,
                corpo("caminhaoId", 1, "dataInicio", "10/01/2026"), 400));
        contemExato(d, "Campo 'dataInicio' com valor inválido. Use o formato AAAA-MM-DD.");
    }

    @Test
    void clienteEmInglesRecebePortugues() throws Exception {
        Map<String, Object> c = caminhaoValido();
        c.put("modelo", "");
        List<String> d = detalhes400(chamar(POST("/api/caminhoes").header(HttpHeaders.ACCEPT_LANGUAGE, "en-US,en;q=0.9"),
                registrar().token(), c, 400));
        contemExato(d, "modelo: não pode ficar em branco");
    }

    @Test
    void jvmEmInglesRecebePortugues() throws Exception {
        Locale anterior = Locale.getDefault();
        try {
            Locale.setDefault(Locale.US);
            Map<String, Object> c = caminhaoValido();
            c.put("eixos", 0);
            List<String> d = detalhes400(chamar(POST("/api/caminhoes"), registrar().token(), c, 400));
            contemExato(d, "eixos: deve ser maior ou igual a 1");
        } finally {
            Locale.setDefault(anterior);
        }
    }
}
