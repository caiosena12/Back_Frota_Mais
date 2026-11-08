package br.com.frotamais.viagem;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Rentabilidade dos fretes.
 *
 * Frete com fotografia financeira (finalizado): devolve o resultado gravado,
 * sem recalcular. Demais (em andamento): reúne os dados em poucas consultas,
 * todas filtradas pela empresa, e aplica a CalculadoraRentabilidade com a
 * política atual.
 */
@Service
@Transactional(readOnly = true)
public class RentabilidadeService {

    /** Ordem cronológica das viagens de um caminhão: data de início, depois id. */
    private static final Comparator<TotaisDiesel> ORDEM =
            Comparator.comparing(TotaisDiesel::dataInicio).thenComparing(TotaisDiesel::viagemId);

    private final AbastecimentoRepository abastecimentos;
    private final DespesaRepository despesas;
    private final PoliticaCalculo politica;

    public RentabilidadeService(AbastecimentoRepository abastecimentos, DespesaRepository despesas,
            PoliticaCalculo politica) {
        this.abastecimentos = abastecimentos;
        this.despesas = despesas;
        this.politica = politica;
    }

    public RentabilidadeResponse calcular(Long empresaId, Viagem viagem) {
        return calcular(empresaId, List.of(viagem)).get(viagem.getId());
    }

    /** Todas as viagens precisam pertencer a empresaId; o serviço chamador garante isso. */
    public Map<Long, RentabilidadeResponse> calcular(Long empresaId, List<Viagem> viagens) {
        Map<Long, RentabilidadeResponse> consolidados = new HashMap<>();
        List<Viagem> aoVivo = new ArrayList<>();
        for (Viagem v : viagens) {
            RentabilidadeResponse r = v.resultadoConsolidado();
            if (r != null) {
                consolidados.put(v.getId(), r);
            } else {
                aoVivo.add(v);
            }
        }
        Map<Long, RentabilidadeResponse> calculados = calcularAgora(empresaId, aoVivo, politica.limites());

        Map<Long, RentabilidadeResponse> resultado = new LinkedHashMap<>();
        for (Viagem v : viagens) {
            resultado.put(v.getId(), consolidados.containsKey(v.getId())
                    ? consolidados.get(v.getId()) : calculados.get(v.getId()));
        }
        return resultado;
    }

    /**
     * Calcula o frete agora, ignorando a fotografia gravada, com os limites
     * informados. Usado para (re)gravar a fotografia na finalização ou numa
     * correção explícita deste frete.
     */
    public RentabilidadeResponse recalcular(Long empresaId, Viagem viagem, LimitesPlausibilidade limites) {
        return calcularAgora(empresaId, List.of(viagem), limites).get(viagem.getId());
    }

    private Map<Long, RentabilidadeResponse> calcularAgora(Long empresaId, List<Viagem> viagens,
            LimitesPlausibilidade limites) {
        Map<Long, RentabilidadeResponse> resultado = new HashMap<>();
        if (viagens.isEmpty()) {
            return resultado;
        }

        Set<Long> viagemIds = viagens.stream().map(Viagem::getId).collect(Collectors.toSet());

        // O histórico de outras viagens só é consultado para viagens sem referência consolidada.
        Set<Long> caminhoesSemReferencia = viagens.stream()
                .filter(v -> v.referenciaConsolidada() == null)
                .map(v -> v.getCaminhao().getId())
                .collect(Collectors.toSet());
        Map<Long, List<TotaisDiesel>> dieselPorCaminhao = caminhoesSemReferencia.isEmpty() ? Map.of()
                : abastecimentos.totaisDieselPorViagem(empresaId, caminhoesSemReferencia).stream()
                        .collect(Collectors.groupingBy(TotaisDiesel::caminhaoId));

        Map<Long, Map<Enum<?>, BigDecimal>> totais = new HashMap<>();
        abastecimentos.totaisPorViagem(empresaId, viagemIds).forEach(t -> acumular(totais, t));
        despesas.totaisPorViagem(empresaId, viagemIds).forEach(t -> acumular(totais, t));

        for (Viagem v : viagens) {
            Map<Enum<?>, BigDecimal> daViagem = totais.getOrDefault(v.getId(), Map.of());
            PrecoDiesel preco = v.referenciaConsolidada() != null ? v.referenciaConsolidada()
                    : resolverPreco(v, dieselPorCaminhao.getOrDefault(v.getCaminhao().getId(), List.of()), limites);
            resultado.put(v.getId(), CalculadoraRentabilidade.calcular(new CalculadoraRentabilidade.Entrada(
                    v.getValorFrete(),
                    v.getKmInicialVazio(),
                    v.getKmInicialCarregado(),
                    v.getKmFinalCarregado(),
                    v.getConsumoMedioVazio(),
                    v.getConsumoMedioCarregado(),
                    preco,
                    daViagem.get(TipoCombustivel.ARLA),
                    daViagem.get(TipoDespesa.PEDAGIO),
                    daViagem.get(TipoDespesa.EXTRA)), limites));
        }
        return resultado;
    }

    /**
     * Preço do diesel calculado agora, a partir do histórico atual. Usado para
     * consolidar a referência na finalização ou numa correção explícita do frete.
     */
    public PrecoDiesel precoAtual(Long empresaId, Viagem viagem, LimitesPlausibilidade limites) {
        Long caminhaoId = viagem.getCaminhao().getId();
        return resolverPreco(viagem, abastecimentos.totaisDieselPorViagem(empresaId, Set.of(caminhaoId)), limites);
    }

    /**
     * Preço do diesel da viagem:
     * 1. média ponderada dos abastecimentos de diesel da própria viagem;
     * 2. senão, a da viagem mais recente do mesmo caminhão que COMEÇOU ANTES do
     *    início desta e teve diesel com preço médio dentro dos limites de
     *    plausibilidade. Viagens que começaram depois nunca são usadas, mesmo que
     *    já estejam cadastradas (caso de uma viagem histórica cadastrada depois).
     *    Referências implausíveis são puladas; o preço da própria viagem nunca é
     *    trocado: se implausível, o cálculo fica incompleto;
     * 3. senão, sem referência.
     *
     * Duas viagens do mesmo caminhão não começam no mesmo dia (regra de
     * sobreposição), então a data de início define a ordem sem ambiguidade.
     */
    static PrecoDiesel resolverPreco(Viagem viagem, List<TotaisDiesel> historicoDoCaminhao,
            LimitesPlausibilidade limites) {
        for (TotaisDiesel t : historicoDoCaminhao) {
            if (t.viagemId().equals(viagem.getId())) {
                return new PrecoDiesel(t.valorPago(), t.litros(),
                        OrigemPrecoDiesel.ABASTECIMENTOS_DA_VIAGEM, viagem.getId());
            }
        }
        return historicoDoCaminhao.stream()
                .filter(t -> t.dataInicio().isBefore(viagem.getDataInicio()))
                .filter(t -> limites.precoValido(t.valorPago().divide(t.litros(), MathContext.DECIMAL128)))
                .max(ORDEM)
                .map(t -> new PrecoDiesel(t.valorPago(), t.litros(),
                        OrigemPrecoDiesel.ULTIMO_PRECO_DO_CAMINHAO, t.viagemId()))
                .orElseGet(PrecoDiesel::semReferencia);
    }

    private static void acumular(Map<Long, Map<Enum<?>, BigDecimal>> totais, TotalPorTipo t) {
        totais.computeIfAbsent(t.viagemId(), id -> new HashMap<>()).merge(t.tipo(), t.valor(), BigDecimal::add);
    }
}
