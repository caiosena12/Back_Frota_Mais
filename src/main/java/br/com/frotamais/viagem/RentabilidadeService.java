package br.com.frotamais.viagem;

import java.math.BigDecimal;
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
 * Reúne os dados de um conjunto de viagens (em poucas consultas, todas
 * filtradas pela empresa) e aplica a CalculadoraRentabilidade.
 */
@Service
@Transactional(readOnly = true)
public class RentabilidadeService {

    /** Ordem cronológica das viagens de um caminhão: data de início, depois id. */
    private static final Comparator<TotaisDiesel> ORDEM =
            Comparator.comparing(TotaisDiesel::dataInicio).thenComparing(TotaisDiesel::viagemId);

    private final AbastecimentoRepository abastecimentos;
    private final DespesaRepository despesas;

    public RentabilidadeService(AbastecimentoRepository abastecimentos, DespesaRepository despesas) {
        this.abastecimentos = abastecimentos;
        this.despesas = despesas;
    }

    public RentabilidadeResponse calcular(Long empresaId, Viagem viagem) {
        return calcular(empresaId, List.of(viagem)).get(viagem.getId());
    }

    /** Todas as viagens precisam pertencer a empresaId; o serviço chamador garante isso. */
    public Map<Long, RentabilidadeResponse> calcular(Long empresaId, List<Viagem> viagens) {
        Map<Long, RentabilidadeResponse> resultado = new LinkedHashMap<>();
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
                    : resolverPreco(v, dieselPorCaminhao.getOrDefault(v.getCaminhao().getId(), List.of()));
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
                    daViagem.get(TipoDespesa.EXTRA))));
        }
        return resultado;
    }

    /**
     * Preço do diesel calculado agora, a partir do histórico atual. Usado para
     * consolidar a referência na finalização ou numa correção explícita do frete.
     */
    public PrecoDiesel precoAtual(Long empresaId, Viagem viagem) {
        Long caminhaoId = viagem.getCaminhao().getId();
        return resolverPreco(viagem, abastecimentos.totaisDieselPorViagem(empresaId, Set.of(caminhaoId)));
    }

    /**
     * Preço do diesel da viagem:
     * 1. média ponderada dos abastecimentos de diesel da própria viagem;
     * 2. senão, a da viagem anterior mais recente do mesmo caminhão que teve diesel
     *    ("anterior" pela data de início e, no empate, pelo id);
     * 3. senão, sem referência.
     */
    static PrecoDiesel resolverPreco(Viagem viagem, List<TotaisDiesel> historicoDoCaminhao) {
        TotaisDiesel atual = new TotaisDiesel(viagem.getId(), null, viagem.getDataInicio(), null, null);

        for (TotaisDiesel t : historicoDoCaminhao) {
            if (t.viagemId().equals(viagem.getId())) {
                return new PrecoDiesel(t.valorPago(), t.litros(),
                        OrigemPrecoDiesel.ABASTECIMENTOS_DA_VIAGEM, viagem.getId());
            }
        }
        return historicoDoCaminhao.stream()
                .filter(t -> ORDEM.compare(t, atual) < 0)
                .max(ORDEM)
                .map(t -> new PrecoDiesel(t.valorPago(), t.litros(),
                        OrigemPrecoDiesel.ULTIMO_PRECO_DO_CAMINHAO, t.viagemId()))
                .orElseGet(PrecoDiesel::semReferencia);
    }

    private static void acumular(Map<Long, Map<Enum<?>, BigDecimal>> totais, TotalPorTipo t) {
        totais.computeIfAbsent(t.viagemId(), id -> new HashMap<>()).merge(t.tipo(), t.valor(), BigDecimal::add);
    }
}
