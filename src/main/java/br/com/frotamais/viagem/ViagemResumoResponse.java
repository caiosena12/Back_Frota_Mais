package br.com.frotamais.viagem;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Linha da listagem de fretes. */
public record ViagemResumoResponse(
        Long id,
        Long caminhaoId,
        String placa,
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
        boolean calculoCompleto) {

    public static ViagemResumoResponse de(Viagem v, RentabilidadeResponse r) {
        return new ViagemResumoResponse(
                v.getId(),
                v.getCaminhao().getId(),
                v.getCaminhao().getPlaca(),
                v.getStatus(),
                v.getOrigem(),
                v.getDestino(),
                v.getDataInicio(),
                v.getDataFim(),
                v.getValorFrete(),
                r.kmVazio(),
                r.kmCarregado(),
                r.custoOperacionalTotal(),
                r.lucro(),
                r.margemPercentual(),
                r.calculoCompleto());
    }
}
