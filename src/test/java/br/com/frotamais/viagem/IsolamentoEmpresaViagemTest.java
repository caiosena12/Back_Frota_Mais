package br.com.frotamais.viagem;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;

/**
 * Duas empresas com tokens reais. A empresa B tenta ler, alterar e lançar dados
 * nos fretes da empresa A por todas as rotas; tudo responde 404 ou não aparece,
 * e os dados de A ficam intactos.
 */
class IsolamentoEmpresaViagemTest extends ApiViagemTeste {

    @Test
    void umaEmpresaNaoAcessaFretesDeOutra() throws Exception {
        String tokenA = registrar().token();
        String tokenB = registrar().token();
        long caminhaoA = criarCaminhao(tokenA);
        long caminhaoB = criarCaminhao(tokenB);

        long viagemA = criarViagem(tokenA, corpo("caminhaoId", caminhaoA, "dataInicio", "2025-06-01",
                "origem", "A", "destino", "B", "valorFrete", "2000.00", "kmInicialCarregado", 0));
        long dieselA = diesel(tokenA, viagemA, "2025-06-01", "100", "600.00").get("id").asLong();
        long pedagioA = pedagio(tokenA, viagemA, "2025-06-01", "10.00").get("id").asLong();
        long extraA = despesaExtra(tokenA, viagemA, "Lavagem", "2025-06-01", "50.00").get("id").asLong();
        String urlA = VIAGENS + "/" + viagemA;

        // Leitura.
        chamar(GET(urlA), tokenB, null, 404);
        assertEquals(0, chamar(GET(VIAGENS), tokenB, null, 200).size());
        assertEquals(0, chamar(GET(VIAGENS + "?caminhaoId=" + caminhaoA), tokenB, null, 200).size());
        assertEquals(0, chamar(GET(VIAGENS + "/indicadores"), tokenB, null, 200).get("viagensFinalizadas").asInt());
        chamar(GET(VIAGENS + "/indicadores?caminhaoId=" + caminhaoA), tokenB, null, 404);

        // Abrir viagem com o caminhão de outra empresa.
        chamar(POST(VIAGENS), tokenB, corpo("caminhaoId", caminhaoA, "dataInicio", "2025-06-01",
                "kmInicialVazio", 0), 404);

        // Alterar e finalizar.
        chamar(PUT(urlA), tokenB, corpo("dataInicio", "2025-06-01", "origem", "X", "destino", "Y",
                "valorFrete", "1.00", "kmInicialCarregado", 0), 404);
        finalizar(tokenB, viagemA, corpo("dataFim", "2025-06-02", "kmFinalCarregado", 400,
                "consumoMedioCarregado", "2.50"), 404);

        // Lançar, corrigir e excluir lançamentos na viagem de A.
        chamar(POST(urlA + "/abastecimentos"), tokenB, corpo("tipo", "DIESEL", "data", "2025-06-01",
                "litros", "1", "valorTotal", "1.00"), 404);
        chamar(POST(urlA + "/pedagios"), tokenB, corpo("data", "2025-06-01", "valor", "1.00"), 404);
        chamar(POST(urlA + "/despesas-extras"), tokenB, corpo("descricao", "x", "data", "2025-06-01",
                "valor", "1.00"), 404);
        chamar(PUT(urlA + "/abastecimentos/" + dieselA), tokenB, corpo("tipo", "DIESEL",
                "data", "2025-06-01", "litros", "1", "valorTotal", "1.00"), 404);
        chamar(PUT(urlA + "/pedagios/" + pedagioA), tokenB, corpo("data", "2025-06-01", "valor", "1.00"), 404);
        chamar(PUT(urlA + "/despesas-extras/" + extraA), tokenB, corpo("descricao", "x",
                "data", "2025-06-01", "valor", "1.00"), 404);
        chamar(DELETE(urlA + "/abastecimentos/" + dieselA), tokenB, null, 404);
        chamar(DELETE(urlA + "/pedagios/" + pedagioA), tokenB, null, 404);
        chamar(DELETE(urlA + "/despesas-extras/" + extraA), tokenB, null, 404);

        // Ids de lançamentos de A usados na viagem da própria B também não funcionam.
        long viagemB = criarViagem(tokenB, corpo("caminhaoId", caminhaoB, "dataInicio", "2025-06-01",
                "kmInicialVazio", 0));
        String urlB = VIAGENS + "/" + viagemB;
        chamar(PUT(urlB + "/abastecimentos/" + dieselA), tokenB, corpo("tipo", "DIESEL",
                "data", "2025-06-01", "litros", "1", "valorTotal", "1.00"), 404);
        chamar(PUT(urlB + "/pedagios/" + pedagioA), tokenB, corpo("data", "2025-06-01", "valor", "1.00"), 404);
        chamar(DELETE(urlB + "/despesas-extras/" + extraA), tokenB, null, 404);

        // A viagem de B não enxerga o diesel de A como "último preço".
        chamar(PUT(urlB), tokenB, corpo("dataInicio", "2025-06-01", "origem", "X", "destino", "Y",
                "valorFrete", "100.00", "kmInicialVazio", 0, "kmInicialCarregado", 10), 200);
        JsonNode rB = finalizar(tokenB, viagemB, corpo("dataFim", "2025-06-02", "kmFinalCarregado", 50,
                "consumoMedioVazio", "3.00", "consumoMedioCarregado", "2.50"), 200).get("rentabilidade");
        assertEquals("SEM_REFERENCIA", rB.get("origemPrecoDiesel").asString());

        // Os dados de A continuam intactos.
        JsonNode a = buscarViagem(tokenA, viagemA);
        assertEquals("EM_ANDAMENTO", a.get("status").asString());
        assertEquals("A", a.get("origem").asString());
        dinheiro("2000.00", a.get("valorFrete"));
        assertEquals(1, a.get("abastecimentosDiesel").size());
        dinheiro("600.00", a.get("abastecimentosDiesel").get(0).get("valorTotal"));
        assertEquals(1, a.get("pedagios").size());
        assertEquals(1, a.get("despesasExtras").size());
        dinheiro("10.00", a.get("pedagios").get(0).get("valor"));
        assertEquals(1, chamar(GET(VIAGENS), tokenA, null, 200).size());
    }
}
