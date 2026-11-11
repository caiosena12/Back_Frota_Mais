package br.com.frotamais.viagem;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Linha da listagem de fretes. */
public record ViagemResumoResponse(
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
        Integer kmVazio,
        Integer kmCarregado,
        BigDecimal custoOperacionalTotal,
        BigDecimal lucro,
        BigDecimal margemPercentual,
        /** Nulo na visão do MOTORISTA, como custo, lucro e margem. */
        Boolean calculoCompleto) {

    /** r nulo: resumo sem informações financeiras gerenciais (visão do MOTORISTA). */
    public static ViagemResumoResponse de(Viagem v, RentabilidadeResponse r) {
        return new ViagemResumoResponse(
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
                r != null ? r.kmVazio() : kmVazio(v),
                r != null ? r.kmCarregado() : kmCarregado(v),
                r != null ? r.custoOperacionalTotal() : null,
                r != null ? r.lucro() : null,
                r != null ? r.margemPercentual() : null,
                r != null ? r.calculoCompleto() : null);
    }

    /** Mesmos critérios de CalculadoraRentabilidade: sem km inicial vazio, 0 km vazio. */
    private static Integer kmVazio(Viagem v) {
        if (v.getKmInicialVazio() == null) {
            return 0;
        }
        return v.getKmInicialCarregado() == null ? null : v.getKmInicialCarregado() - v.getKmInicialVazio();
    }

    private static Integer kmCarregado(Viagem v) {
        if (v.getKmInicialCarregado() == null || v.getKmFinalCarregado() == null) {
            return null;
        }
        return v.getKmFinalCarregado() - v.getKmInicialCarregado();
    }
}
