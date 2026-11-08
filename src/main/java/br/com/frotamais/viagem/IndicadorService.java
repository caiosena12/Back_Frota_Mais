package br.com.frotamais.viagem;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.frotamais.caminhao.CaminhaoRepository;
import br.com.frotamais.comum.RegraNegocioException;
import br.com.frotamais.seguranca.UsuarioLogado;

/**
 * Totais dos fretes finalizados num período, para o dashboard e os relatórios.
 *
 * O período é aplicado sobre a data final (descarga) do frete. Filtros:
 * ano; ano e mês; ou um intervalo de/até (que cobre a semana, sem o servidor
 * impor uma definição de semana). Sem filtro: todo o histórico.
 */
@Service
@Transactional(readOnly = true)
public class IndicadorService {

    static final String CRITERIO = "DATA_FIM";

    private final ViagemRepository viagens;
    private final CaminhaoRepository caminhoes;
    private final RentabilidadeService rentabilidade;
    private final UsuarioLogado logado;

    public IndicadorService(ViagemRepository viagens, CaminhaoRepository caminhoes,
            RentabilidadeService rentabilidade, UsuarioLogado logado) {
        this.viagens = viagens;
        this.caminhoes = caminhoes;
        this.rentabilidade = rentabilidade;
        this.logado = logado;
    }

    public IndicadoresResponse indicadores(Integer ano, Integer mes, LocalDate de, LocalDate ate, Long caminhaoId) {
        Long empresaId = logado.empresaId();

        if (ano != null || mes != null) {
            if (de != null || ate != null) {
                throw RegraNegocioException.invalido("Use ano e mês ou de e até, não os dois.");
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
        if (caminhaoId != null && caminhoes.findByIdAndEmpresaId(caminhaoId, empresaId).isEmpty()) {
            throw RegraNegocioException.naoEncontrado("Caminhão não encontrado.");
        }

        List<Viagem> lista = viagens.finalizadasNoPeriodo(empresaId, de, ate, caminhaoId);
        Map<Long, RentabilidadeResponse> calculos = rentabilidade.calcular(empresaId, lista);

        List<RentabilidadeResponse> completas = new ArrayList<>();
        List<Long> incompletas = new ArrayList<>();
        for (Viagem v : lista) {
            RentabilidadeResponse r = calculos.get(v.getId());
            if (r.calculoCompleto()) {
                completas.add(r);
            } else {
                incompletas.add(v.getId());
            }
        }

        BigDecimal receita = somar(completas, RentabilidadeResponse::receita);
        BigDecimal custo = somar(completas, RentabilidadeResponse::custoOperacionalTotal);
        BigDecimal lucro = somar(completas, RentabilidadeResponse::lucro);

        return new IndicadoresResponse(
                de,
                ate,
                caminhaoId,
                CRITERIO,
                lista.size(),
                completas.size(),
                incompletas,
                receita,
                somar(completas, RentabilidadeResponse::custoDieselVazio),
                somar(completas, RentabilidadeResponse::custoDieselCarregado),
                somar(completas, RentabilidadeResponse::custoArla),
                somar(completas, RentabilidadeResponse::pedagios),
                somar(completas, RentabilidadeResponse::despesasExtras),
                custo,
                lucro,
                CalculadoraRentabilidade.margem(lucro, receita),
                completas.stream().mapToLong(RentabilidadeResponse::kmVazio).sum(),
                completas.stream().mapToLong(RentabilidadeResponse::kmCarregado).sum());
    }

    private static BigDecimal somar(List<RentabilidadeResponse> itens,
            Function<RentabilidadeResponse, BigDecimal> campo) {
        return itens.stream().map(campo).reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
    }
}
