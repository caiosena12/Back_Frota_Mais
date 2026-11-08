package br.com.frotamais.viagem;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * Fotografia financeira de um frete finalizado: o resultado do cálculo e a
 * política (versão das fórmulas e limites) com que ele foi feito.
 *
 * Gravada na finalização e regravada só por uma correção explícita do próprio
 * frete. Enquanto existir, a consulta do frete e os indicadores leem estes
 * valores e não recalculam nada: mudar os limites globais ou outro frete não
 * altera o resultado. Pendências (cálculo incompleto) também ficam gravadas.
 */
@Embeddable
public class ResultadoConsolidado {

    private static final String SEPARADOR = "\n";

    @Column(name = "resultado_consolidado_em")
    private Instant consolidadoEm;

    @Column(name = "politica_calculo_versao", length = 40)
    private String versaoPolitica;

    @Column(name = "limite_preco_diesel_minimo", precision = 12, scale = 4)
    private BigDecimal limitePrecoDieselMinimo;

    @Column(name = "limite_preco_diesel_maximo", precision = 12, scale = 4)
    private BigDecimal limitePrecoDieselMaximo;

    @Column(name = "limite_consumo_minimo", precision = 12, scale = 4)
    private BigDecimal limiteConsumoMinimo;

    @Column(name = "limite_consumo_maximo", precision = 12, scale = 4)
    private BigDecimal limiteConsumoMaximo;

    @Column(name = "res_receita", precision = 15, scale = 2)
    private BigDecimal receita;

    @Column(name = "res_km_vazio")
    private Integer kmVazio;

    @Column(name = "res_km_carregado")
    private Integer kmCarregado;

    @Column(name = "res_litros_vazio", precision = 14, scale = 3)
    private BigDecimal litrosVazio;

    @Column(name = "res_litros_carregado", precision = 14, scale = 3)
    private BigDecimal litrosCarregado;

    @Column(name = "res_custo_diesel_vazio", precision = 17, scale = 2)
    private BigDecimal custoDieselVazio;

    @Column(name = "res_custo_diesel_carregado", precision = 17, scale = 2)
    private BigDecimal custoDieselCarregado;

    @Column(name = "res_custo_arla", precision = 17, scale = 2)
    private BigDecimal custoArla;

    @Column(name = "res_pedagios", precision = 17, scale = 2)
    private BigDecimal pedagios;

    @Column(name = "res_despesas_extras", precision = 17, scale = 2)
    private BigDecimal despesasExtras;

    @Column(name = "res_custo_total", precision = 17, scale = 2)
    private BigDecimal custoOperacionalTotal;

    @Column(name = "res_lucro", precision = 17, scale = 2)
    private BigDecimal lucro;

    @Column(name = "res_margem", precision = 20, scale = 2)
    private BigDecimal margemPercentual;

    @Column(name = "res_calculo_completo")
    private Boolean calculoCompleto;

    /** Motivos de cálculo incompleto, um por linha. */
    @Column(name = "res_pendencias", length = 4000)
    private String pendencias;

    protected ResultadoConsolidado() {
    }

    static ResultadoConsolidado de(RentabilidadeResponse r, Instant em) {
        ResultadoConsolidado c = new ResultadoConsolidado();
        PoliticaCalculoAplicada p = r.politicaCalculo();
        c.consolidadoEm = em;
        c.versaoPolitica = p.versao();
        // Mesma escala da coluna, para a resposta não mudar depois de recarregar do banco.
        c.limitePrecoDieselMinimo = limite(p.precoDieselMinimo());
        c.limitePrecoDieselMaximo = limite(p.precoDieselMaximo());
        c.limiteConsumoMinimo = limite(p.consumoMinimo());
        c.limiteConsumoMaximo = limite(p.consumoMaximo());
        c.receita = r.receita();
        c.kmVazio = r.kmVazio();
        c.kmCarregado = r.kmCarregado();
        c.litrosVazio = r.litrosEstimadosVazio();
        c.litrosCarregado = r.litrosEstimadosCarregado();
        c.custoDieselVazio = r.custoDieselVazio();
        c.custoDieselCarregado = r.custoDieselCarregado();
        c.custoArla = r.custoArla();
        c.pedagios = r.pedagios();
        c.despesasExtras = r.despesasExtras();
        c.custoOperacionalTotal = r.custoOperacionalTotal();
        c.lucro = r.lucro();
        c.margemPercentual = r.margemPercentual();
        c.calculoCompleto = r.calculoCompleto();
        c.pendencias = r.pendencias().isEmpty() ? null : String.join(SEPARADOR, r.pendencias());
        return c;
    }

    boolean existe() {
        return consolidadoEm != null;
    }

    /** O resultado gravado, com a referência de preço também gravada no frete. */
    RentabilidadeResponse resposta(PrecoDiesel referencia) {
        return new RentabilidadeResponse(
                receita,
                kmVazio,
                kmCarregado,
                litrosVazio,
                litrosCarregado,
                referencia.precoMedio(),
                referencia.origem(),
                referencia.viagemReferenciaId(),
                referencia.consolidadaEm(),
                custoDieselVazio,
                custoDieselCarregado,
                custoArla,
                pedagios,
                despesasExtras,
                custoOperacionalTotal,
                lucro,
                margemPercentual,
                Boolean.TRUE.equals(calculoCompleto),
                pendencias == null ? List.of() : List.of(pendencias.split(SEPARADOR)),
                new PoliticaCalculoAplicada(versaoPolitica, limitePrecoDieselMinimo, limitePrecoDieselMaximo,
                        limiteConsumoMinimo, limiteConsumoMaximo),
                consolidadoEm);
    }

    private static BigDecimal limite(BigDecimal valor) {
        return valor.setScale(4, RoundingMode.HALF_UP);
    }
}
