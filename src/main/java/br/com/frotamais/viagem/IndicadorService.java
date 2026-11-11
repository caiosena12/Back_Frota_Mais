package br.com.frotamais.viagem;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.frotamais.caminhao.Caminhao;
import br.com.frotamais.caminhao.CaminhaoRepository;
import br.com.frotamais.comum.RegraNegocioException;
import br.com.frotamais.manutencao.ManutencaoRepository;
import br.com.frotamais.manutencao.TotalManutencao;
import br.com.frotamais.seguranca.UsuarioLogado;

/**
 * Totais dos fretes finalizados, das viagens canceladas (em bloco separado)
 * e das manutenções num período, para o dashboard e os relatórios.
 *
 * O período é aplicado sobre a data final (descarga) do frete e sobre a data
 * da manutenção. Filtros (mutuamente exclusivos): ano; ano e mês
 * (calendário); semana (qualquer data dela: segunda a domingo); ou um
 * intervalo de/até, inclusivo. Sem filtro: todo o histórico.
 *
 * Totais oficiais: só fretes finalizados com cálculo completo, lidos da
 * fotografia financeira de cada um. Margem do período ponderada: soma dos
 * lucros ÷ soma das receitas dos mesmos fretes × 100 (nunca média simples das
 * margens). Fretes incompletos ficam fora dos totais e são apresentados à
 * parte, com a soma informativa dos seus valores de frete. Detalhes de cada
 * campo em IndicadoresResponse.
 */
@Service
@Transactional(readOnly = true)
public class IndicadorService {

    static final String CRITERIO = "DATA_FIM";
    private static final BigDecimal CEM = BigDecimal.valueOf(100);
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

    private final ViagemRepository viagens;
    private final CaminhaoRepository caminhoes;
    private final AbastecimentoRepository abastecimentos;
    private final ManutencaoRepository manutencoes;
    private final RentabilidadeService rentabilidade;
    private final UsuarioLogado logado;

    public IndicadorService(ViagemRepository viagens, CaminhaoRepository caminhoes,
            AbastecimentoRepository abastecimentos, ManutencaoRepository manutencoes,
            RentabilidadeService rentabilidade, UsuarioLogado logado) {
        this.viagens = viagens;
        this.caminhoes = caminhoes;
        this.abastecimentos = abastecimentos;
        this.manutencoes = manutencoes;
        this.rentabilidade = rentabilidade;
        this.logado = logado;
    }

    private record Periodo(LocalDate de, LocalDate ate) {
    }

    /** Fretes de um conjunto, já calculados, e o diesel pago em cada um. */
    private record Base(List<Viagem> lista, Map<Long, RentabilidadeResponse> calculos, Map<Long, BigDecimal> dieselPago) {
    }

    /** Da empresa inteira ou, com caminhaoId, de um caminhão. */
    public IndicadoresResponse indicadores(Integer ano, Integer mes, LocalDate semana, LocalDate de, LocalDate ate,
            Long caminhaoId) {
        Long empresaId = logado.empresaId();
        Periodo p = periodo(ano, mes, semana, de, ate);
        if (caminhaoId != null && caminhoes.findByIdAndEmpresaId(caminhaoId, empresaId).isEmpty()) {
            throw RegraNegocioException.naoEncontrado("Caminhão não encontrado.");
        }
        Base base = base(empresaId, viagens.finalizadasNoPeriodo(empresaId, p.de(), p.ate(), caminhaoId));
        List<Viagem> canceladas = viagens.canceladasNoPeriodo(empresaId, p.de(), p.ate(), caminhaoId);
        Map<Long, RentabilidadeResponse> calculosCanceladas = rentabilidade.calcular(empresaId, canceladas);
        List<TotalManutencao> manut = manutencoes.totaisPorCaminhao(empresaId, caminhaoId, p.de(), p.ate());
        return montar(p, caminhaoId, base, base.lista(), canceladas, calculosCanceladas,
                manut.stream().mapToLong(TotalManutencao::quantidade).sum(),
                manut.stream().map(TotalManutencao::valor).reduce(ZERO, BigDecimal::add));
    }

    /** Um item por caminhão com frete finalizado ou manutenção no período, em ordem de placa. */
    public List<IndicadoresCaminhaoResponse> porCaminhao(Integer ano, Integer mes, LocalDate semana, LocalDate de,
            LocalDate ate) {
        Long empresaId = logado.empresaId();
        Periodo p = periodo(ano, mes, semana, de, ate);
        Base base = base(empresaId, viagens.finalizadasNoPeriodo(empresaId, p.de(), p.ate(), null));
        Map<Long, List<Viagem>> porCaminhao = base.lista().stream()
                .collect(Collectors.groupingBy(v -> v.getCaminhao().getId()));
        List<Viagem> canceladas = viagens.canceladasNoPeriodo(empresaId, p.de(), p.ate(), null);
        Map<Long, RentabilidadeResponse> calculosCanceladas = rentabilidade.calcular(empresaId, canceladas);
        Map<Long, List<Viagem>> canceladasPorCaminhao = canceladas.stream()
                .collect(Collectors.groupingBy(v -> v.getCaminhao().getId()));
        Map<Long, TotalManutencao> manut = manutencoes.totaisPorCaminhao(empresaId, null, p.de(), p.ate()).stream()
                .collect(Collectors.toMap(TotalManutencao::caminhaoId, Function.identity()));

        Set<Long> ids = new LinkedHashSet<>(porCaminhao.keySet());
        ids.addAll(canceladasPorCaminhao.keySet());
        ids.addAll(manut.keySet());
        if (ids.isEmpty()) {
            return List.of();
        }
        return caminhoes.findAllByEmpresaIdAndIdIn(empresaId, ids).stream()
                .sorted(Comparator.comparing(Caminhao::getPlaca).thenComparing(Caminhao::getId))
                .map(c -> {
                    TotalManutencao m = manut.get(c.getId());
                    return new IndicadoresCaminhaoResponse(c.getId(), c.getPlaca(), montar(p, c.getId(), base,
                            porCaminhao.getOrDefault(c.getId(), List.of()),
                            canceladasPorCaminhao.getOrDefault(c.getId(), List.of()), calculosCanceladas,
                            m != null ? m.quantidade() : 0, m != null ? m.valor() : ZERO));
                })
                .toList();
    }

    private Periodo periodo(Integer ano, Integer mes, LocalDate semana, LocalDate de, LocalDate ate) {
        if (semana != null) {
            if (ano != null || mes != null || de != null || ate != null) {
                throw RegraNegocioException.invalido("Use só um filtro de período: ano e mês, semana ou de e até.");
            }
            de = semana.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            ate = de.plusDays(6);
        }
        if (ano != null || mes != null) {
            if (de != null || ate != null) {
                throw RegraNegocioException.invalido("Use só um filtro de período: ano e mês, semana ou de e até.");
            }
            if (ano == null) {
                throw RegraNegocioException.invalido("Informe o ano junto com o mês.");
            }
            if (ano < 2000 || ano > 2100) {
                throw RegraNegocioException.invalido("Ano inválido.");
            }
            if (mes == null) {
                de = LocalDate.of(ano, 1, 1);
                ate = LocalDate.of(ano, 12, 31);
            } else {
                if (mes < 1 || mes > 12) {
                    throw RegraNegocioException.invalido("Mês inválido: use de 1 a 12.");
                }
                YearMonth ym = YearMonth.of(ano, mes);
                de = ym.atDay(1);
                ate = ym.atEndOfMonth();
            }
        }
        if (de != null && ate != null && de.isAfter(ate)) {
            throw RegraNegocioException.invalido("A data inicial do período não pode ser posterior à final.");
        }
        return new Periodo(de, ate);
    }

    private Base base(Long empresaId, List<Viagem> lista) {
        Map<Long, RentabilidadeResponse> calculos = rentabilidade.calcular(empresaId, lista);
        Map<Long, BigDecimal> dieselPago = new HashMap<>();
        if (!lista.isEmpty()) {
            abastecimentos.totaisPorViagem(empresaId, lista.stream().map(Viagem::getId).collect(Collectors.toSet()))
                    .stream()
                    .filter(t -> t.tipo() == TipoCombustivel.DIESEL)
                    .forEach(t -> dieselPago.merge(t.viagemId(), t.valor(), BigDecimal::add));
        }
        return new Base(lista, calculos, dieselPago);
    }

    private static IndicadoresResponse montar(Periodo p, Long caminhaoId, Base base, List<Viagem> lista,
            List<Viagem> canceladas, Map<Long, RentabilidadeResponse> calculosCanceladas,
            long quantidadeManutencoes, BigDecimal custoManutencoes) {
        // Canceladas: valores reais conhecidos, em bloco separado dos fretes concluídos.
        BigDecimal receitaCanceladas = ZERO;
        BigDecimal custoCanceladas = ZERO;
        List<Long> canceladasIncompletas = new ArrayList<>();
        for (Viagem v : canceladas) {
            RentabilidadeResponse r = calculosCanceladas.get(v.getId());
            if (r.receita() != null) {
                receitaCanceladas = receitaCanceladas.add(r.receita());
            }
            custoCanceladas = custoCanceladas.add(nulo0(r.custoArla())).add(nulo0(r.pedagios()))
                    .add(nulo0(r.despesasExtras())).add(nulo0(r.custoDieselVazio()))
                    .add(nulo0(r.custoDieselCarregado()));
            if (r.custoOperacionalTotal() == null) {
                canceladasIncompletas.add(v.getId());
            }
        }
        BigDecimal resultadoCanceladas = receitaCanceladas.subtract(custoCanceladas).setScale(2, RoundingMode.HALF_UP);

        List<RentabilidadeResponse> completas = new ArrayList<>();
        List<Long> incompletas = new ArrayList<>();
        BigDecimal freteIncompletas = ZERO;
        BigDecimal dieselPago = ZERO;
        for (Viagem v : lista) {
            RentabilidadeResponse r = base.calculos().get(v.getId());
            if (r.calculoCompleto()) {
                completas.add(r);
                dieselPago = dieselPago.add(base.dieselPago().getOrDefault(v.getId(), ZERO));
            } else {
                incompletas.add(v.getId());
                if (v.getValorFrete() != null) {
                    freteIncompletas = freteIncompletas.add(v.getValorFrete());
                }
            }
        }

        BigDecimal receita = somar(completas, RentabilidadeResponse::receita);
        BigDecimal custo = somar(completas, RentabilidadeResponse::custoOperacionalTotal);
        BigDecimal lucro = somar(completas, RentabilidadeResponse::lucro);
        BigDecimal dieselVazio = somar(completas, RentabilidadeResponse::custoDieselVazio);
        BigDecimal dieselCarregado = somar(completas, RentabilidadeResponse::custoDieselCarregado);
        BigDecimal dieselEstimado = dieselVazio.add(dieselCarregado);
        long kmVazio = completas.stream().mapToLong(RentabilidadeResponse::kmVazio).sum();
        long kmCarregado = completas.stream().mapToLong(RentabilidadeResponse::kmCarregado).sum();
        BigDecimal kmTotal = BigDecimal.valueOf(kmVazio + kmCarregado);
        BigDecimal litros = completas.stream()
                .map(r -> nulo0(r.litrosEstimadosVazio()).add(nulo0(r.litrosEstimadosCarregado())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new IndicadoresResponse(
                p.de(),
                p.ate(),
                caminhaoId,
                CRITERIO,
                lista.size(),
                completas.size(),
                incompletas.size(),
                incompletas,
                freteIncompletas,
                receita,
                dieselVazio,
                dieselCarregado,
                somar(completas, RentabilidadeResponse::custoArla),
                somar(completas, RentabilidadeResponse::pedagios),
                somar(completas, RentabilidadeResponse::despesasExtras),
                custo,
                lucro,
                CalculadoraRentabilidade.margem(lucro, receita),
                kmVazio,
                kmCarregado,
                dividir(custo, kmTotal),
                dividir(lucro, kmTotal),
                dividir(BigDecimal.valueOf(kmVazio).multiply(CEM), kmTotal),
                dividir(kmTotal, litros),
                dieselPago,
                dieselEstimado,
                dieselPago.subtract(dieselEstimado),
                quantidadeManutencoes,
                custoManutencoes.setScale(2, RoundingMode.HALF_UP),
                lucro.subtract(custoManutencoes).setScale(2, RoundingMode.HALF_UP),
                canceladas.size(),
                canceladasIncompletas,
                receitaCanceladas.setScale(2, RoundingMode.HALF_UP),
                custoCanceladas.setScale(2, RoundingMode.HALF_UP),
                resultadoCanceladas,
                lucro.add(resultadoCanceladas).subtract(custoManutencoes).setScale(2, RoundingMode.HALF_UP));
    }

    /** Duas casas, ou nulo quando não há base (divisor zero). */
    private static BigDecimal dividir(BigDecimal dividendo, BigDecimal divisor) {
        if (divisor.signum() == 0) {
            return null;
        }
        return dividendo.divide(divisor, 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal nulo0(BigDecimal valor) {
        return valor == null ? BigDecimal.ZERO : valor;
    }

    private static BigDecimal somar(List<RentabilidadeResponse> itens,
            Function<RentabilidadeResponse, BigDecimal> campo) {
        return itens.stream().map(campo).reduce(ZERO, BigDecimal::add);
    }
}
