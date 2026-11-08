package br.com.frotamais.viagem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import br.com.frotamais.viagem.CalculadoraRentabilidade.Entrada;

/** Teste unitário puro: não sobe Spring nem acessa banco. */
class CalculadoraRentabilidadeTest {

    private static BigDecimal d(String valor) {
        return new BigDecimal(valor);
    }

    private static void igual(String esperado, BigDecimal atual) {
        assertEquals(0, d(esperado).compareTo(atual), () -> "esperado " + esperado + ", obtido " + atual);
    }

    /** 300 L por R$ 1.860,00: preço médio ponderado de R$ 6,20/L. */
    private static PrecoDiesel precoDaViagem() {
        return new PrecoDiesel(d("1860.00"), d("300.000"), OrigemPrecoDiesel.ABASTECIMENTOS_DA_VIAGEM, 1L);
    }

    /** Frete 2 do exemplo: 100 km vazio (Santos → São Paulo) e 500 km carregado (São Paulo → Palmital). */
    private static Entrada frete2(PrecoDiesel preco, BigDecimal arla) {
        return new Entrada(d("3500.00"), 1000, 1100, 1600, d("3.20"), d("2.50"),
                preco, arla, d("77.80"), d("50.00"));
    }

    @Test
    void calculaConsumoECustoSeparadosParaVazioECarregado() {
        RentabilidadeResponse r = CalculadoraRentabilidade.calcular(frete2(precoDaViagem(), d("80.00")));

        assertEquals(100, r.kmVazio());
        assertEquals(500, r.kmCarregado());
        igual("31.25", r.litrosEstimadosVazio());      // 100 km ÷ 3,20 km/L
        igual("200", r.litrosEstimadosCarregado());    // 500 km ÷ 2,50 km/L
        igual("6.2", r.precoMedioDiesel());
        igual("193.75", r.custoDieselVazio());         // 31,25 L × 6,20
        igual("1240.00", r.custoDieselCarregado());    // 200 L × 6,20
        igual("80.00", r.custoArla());
        igual("77.80", r.pedagios());
        igual("50.00", r.despesasExtras());
        igual("1641.55", r.custoOperacionalTotal());
        igual("3500.00", r.receita());
        igual("1858.45", r.lucro());
        igual("53.10", r.margemPercentual());          // 1858,45 ÷ 3500 = 53,098...%
        assertTrue(r.calculoCompleto());
        assertTrue(r.pendencias().isEmpty());
    }

    @Test
    void valorPagoNoDieselNaoEntraComoCustoDiretamente() {
        // Os abastecimentos somam R$ 1.860,00, mas o custo do diesel é o estimado pelo consumo.
        RentabilidadeResponse r = CalculadoraRentabilidade.calcular(frete2(precoDaViagem(), null));
        igual("1433.75", r.custoDieselVazio().add(r.custoDieselCarregado()));
        igual("1561.55", r.custoOperacionalTotal());
    }

    @Test
    void arlaEOpcionalENaoAlteraODiesel() {
        RentabilidadeResponse semArla = CalculadoraRentabilidade.calcular(frete2(precoDaViagem(), null));
        RentabilidadeResponse comArla = CalculadoraRentabilidade.calcular(frete2(precoDaViagem(), d("80.00")));

        igual("0.00", semArla.custoArla());
        assertTrue(semArla.calculoCompleto());
        assertEquals(semArla.custoDieselVazio(), comArla.custoDieselVazio());
        assertEquals(semArla.custoDieselCarregado(), comArla.custoDieselCarregado());
        igual("80.00", comArla.custoOperacionalTotal().subtract(semArla.custoOperacionalTotal()));
    }

    @Test
    void semDeslocamentoVazioNaoHaCustoVazioNemExigeConsumoVazio() {
        RentabilidadeResponse r = CalculadoraRentabilidade.calcular(new Entrada(
                d("2000.00"), null, 5000, 5400, null, d("2.50"), precoDaViagem(), null, null, null));

        assertEquals(0, r.kmVazio());
        igual("0.00", r.custoDieselVazio());
        igual("992.00", r.custoDieselCarregado());     // 400 ÷ 2,5 = 160 L × 6,20
        igual("1008.00", r.lucro());
        assertTrue(r.calculoCompleto());
    }

    @Test
    void semReferenciaDePrecoNaoInventaCusto() {
        RentabilidadeResponse r = CalculadoraRentabilidade.calcular(
                frete2(PrecoDiesel.semReferencia(), d("80.00")));

        assertEquals(OrigemPrecoDiesel.SEM_REFERENCIA, r.origemPrecoDiesel());
        assertNull(r.precoMedioDiesel());
        assertNull(r.custoDieselVazio());
        assertNull(r.custoDieselCarregado());
        assertNull(r.custoOperacionalTotal());
        assertNull(r.lucro());
        assertNull(r.margemPercentual());
        // Os litros estimados não dependem do preço e continuam disponíveis.
        igual("31.25", r.litrosEstimadosVazio());
        assertFalse(r.calculoCompleto());
        assertTrue(r.pendencias().stream().anyMatch(p -> p.contains("Sem referência de preço")));
    }

    @Test
    void viagemEmAndamentoTemCalculoIncompleto() {
        RentabilidadeResponse r = CalculadoraRentabilidade.calcular(new Entrada(
                null, 1000, null, null, null, null, precoDaViagem(), null, d("10.00"), null));

        assertNull(r.kmVazio());
        assertNull(r.kmCarregado());
        assertNull(r.custoOperacionalTotal());
        assertNull(r.lucro());
        assertFalse(r.calculoCompleto());
        assertTrue(r.pendencias().contains("Valor do frete não informado."));
    }

    @Test
    void consumoNaoInformadoDeixaCustoDoPercursoNulo() {
        RentabilidadeResponse r = CalculadoraRentabilidade.calcular(new Entrada(
                d("3500.00"), 1000, 1100, 1600, null, d("2.50"), precoDaViagem(), null, null, null));

        assertNull(r.custoDieselVazio());
        igual("1240.00", r.custoDieselCarregado());
        assertNull(r.custoOperacionalTotal());
        assertTrue(r.pendencias().contains("Consumo médio vazio não informado."));
    }

    @Test
    void arredondaSoNoFinal() {
        // Preço 1234,56 ÷ 201,7 L e 5000 km a 2,37 km/L. Arredondar o preço (4 casas) e os
        // litros (3 casas) antes de multiplicar daria R$ 12.913,08; o valor exato é 12.913,02.
        PrecoDiesel preco = new PrecoDiesel(d("1234.56"), d("201.700"),
                OrigemPrecoDiesel.ABASTECIMENTOS_DA_VIAGEM, 1L);
        RentabilidadeResponse r = CalculadoraRentabilidade.calcular(new Entrada(
                d("20000.00"), null, 10000, 15000, null, d("2.37"), preco, null, null, null));

        igual("12913.02", r.custoDieselCarregado());
        igual("6.1208", r.precoMedioDiesel());
    }

    @Test
    void prejuizoTemLucroEMargemNegativos() {
        RentabilidadeResponse r = CalculadoraRentabilidade.calcular(new Entrada(
                d("1000.00"), null, 0, 400, null, d("2.00"), precoDaViagem(), null, null, null));

        igual("1240.00", r.custoOperacionalTotal());   // 200 L × 6,20
        igual("-240.00", r.lucro());
        igual("-24.00", r.margemPercentual());
    }
}
