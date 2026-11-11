package br.com.frotamais.viagem;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Detalhe do frete. kmFinalVazio é derivado: o deslocamento vazio termina no
 * carregamento, então é igual a kmInicialCarregado quando há vazio.
 *
 * cadastroRetroativoEm preenchido: frete cadastrado depois de acontecer
 * (POST /api/viagens/historicas), no instante indicado.
 */
public record ViagemResponse(
        Long id,
        Long caminhaoId,
        String placa,
        Long motoristaId,
        String motoristaNome,
        StatusViagem status,
        String origem,
        String destino,
        LocalDate dataInicio,
        LocalDate dataFim,
        BigDecimal valorFrete,
        Integer kmInicialVazio,
        Integer kmFinalVazio,
        Integer kmInicialCarregado,
        Integer kmFinalCarregado,
        BigDecimal consumoMedioVazio,
        BigDecimal consumoMedioCarregado,
        Instant criadaEm,
        Instant atualizadaEm,
        Instant finalizadaEm,
        Instant cadastroRetroativoEm,
        List<AbastecimentoResponse> abastecimentosDiesel,
        List<AbastecimentoResponse> abastecimentosArla,
        List<PedagioResponse> pedagios,
        List<DespesaExtraResponse> despesasExtras,
        RentabilidadeResponse rentabilidade) {

    public static ViagemResponse de(Viagem v, List<Abastecimento> abastecimentos,
            List<Despesa> despesas, RentabilidadeResponse rentabilidade) {
        return new ViagemResponse(
                v.getId(),
                v.getCaminhao().getId(),
                v.getCaminhao().getPlaca(),
                v.getMotorista() != null ? v.getMotorista().getId() : null,
                v.getMotorista() != null ? v.getMotorista().getNome() : null,
                v.getStatus(),
                v.getOrigem(),
                v.getDestino(),
                v.getDataInicio(),
                v.getDataFim(),
                v.getValorFrete(),
                v.getKmInicialVazio(),
                v.getKmInicialVazio() != null ? v.getKmInicialCarregado() : null,
                v.getKmInicialCarregado(),
                v.getKmFinalCarregado(),
                v.getConsumoMedioVazio(),
                v.getConsumoMedioCarregado(),
                v.getCriadaEm(),
                v.getAtualizadaEm(),
                v.getFinalizadaEm(),
                v.getCadastroRetroativoEm(),
                abastecimentos.stream().filter(a -> a.getTipo() == TipoCombustivel.DIESEL)
                        .map(AbastecimentoResponse::de).toList(),
                abastecimentos.stream().filter(a -> a.getTipo() == TipoCombustivel.ARLA)
                        .map(AbastecimentoResponse::de).toList(),
                despesas.stream().filter(d -> d.getTipo() == TipoDespesa.PEDAGIO)
                        .map(PedagioResponse::de).toList(),
                despesas.stream().filter(d -> d.getTipo() == TipoDespesa.EXTRA)
                        .map(DespesaExtraResponse::de).toList(),
                rentabilidade);
    }
}
