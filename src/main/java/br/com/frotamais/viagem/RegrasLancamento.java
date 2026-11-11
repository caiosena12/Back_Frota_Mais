package br.com.frotamais.viagem;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;
import java.time.LocalTime;

import br.com.frotamais.caminhao.Caminhao;
import br.com.frotamais.comum.Datas;
import br.com.frotamais.comum.RegraNegocioException;

/**
 * Validações de abastecimentos, pedágios e despesas extras, usadas tanto nos
 * lançamentos de uma viagem em andamento quanto no cadastro de viagem
 * histórica. Funções puras: recebem tudo o que precisam.
 */
final class RegrasLancamento {

    private RegrasLancamento() {
    }

    /** A data do lançamento cabe no período [inicio, fim] (fim nulo = em aberto) e não está no futuro. */
    static void validarData(LocalDate inicio, LocalDate fim, LocalDate data) {
        if (data.isAfter(ViagemService.hoje())) {
            throw RegraNegocioException.invalido("A data do lançamento não pode estar no futuro.");
        }
        if (data.isBefore(inicio)) {
            throw RegraNegocioException.invalido(
                    "A data do lançamento não pode ser anterior ao início da viagem.");
        }
        if (fim != null && data.isAfter(fim)) {
            throw RegraNegocioException.invalido(
                    "A data do lançamento não pode ser posterior ao fim da viagem.");
        }
    }

    /**
     * Abastecimento (diesel ou Arla): dentro do período da viagem, como os
     * demais lançamentos; ou ANTES do início dela, quando foi feito entre duas
     * viagens: a partir do dia em que a viagem anterior do caminhão terminou
     * (fimAnterior, inclusive) e antes do início desta. A data real é
     * preservada e o abastecimento compõe o diesel desta viagem, não o da
     * anterior.
     *
     * fimAnterior nulo: não há viagem anterior (primeiro frete do caminhão).
     * O tratamento desse caso ainda não foi definido, então o abastecimento
     * anterior ao início é recusado, sem inventar um limite.
     */
    static void validarDataAbastecimento(LocalDate inicio, LocalDate fim, LocalDate fimAnterior,
            LocalDate data, LocalTime hora) {
        if (hora != null && data.equals(ViagemService.hoje()) && hora.isAfter(LocalTime.now(Datas.FUSO))) {
            throw RegraNegocioException.invalido("O horário do abastecimento não pode estar no futuro.");
        }
        if (!data.isBefore(inicio)) {
            validarData(inicio, fim, data);
            return;
        }
        if (fimAnterior == null) {
            throw RegraNegocioException.invalido("Abastecimento anterior ao início da viagem só é aceito quando"
                    + " o caminhão tem uma viagem anterior, e a partir do fim dela. Para o primeiro frete do"
                    + " caminhão, esse caso ainda não foi definido.");
        }
        if (data.isBefore(fimAnterior)) {
            throw RegraNegocioException.invalido("Abastecimento anterior ao início da viagem precisa ter"
                    + " ocorrido a partir do fim da viagem anterior do caminhão (" + fimAnterior + ").");
        }
    }

    /**
     * Diesel: preço por litro dentro dos limites de plausibilidade e, se o
     * caminhão tiver capacidade do tanque cadastrada, litros até essa capacidade.
     * Sem capacidade cadastrada não há limite de litros.
     *
     * Arla: só litros e valor positivos (garantidos na validação do request).
     * Não há limite de preço nem de consumo de Arla, e a capacidade do tanque de
     * diesel não se aplica a ele; o caminhão não tem campo de capacidade do
     * tanque de Arla.
     */
    static void validarAbastecimento(Caminhao caminhao, AbastecimentoRequest req, LimitesPlausibilidade limites) {
        if (req.tipo() != TipoCombustivel.DIESEL) {
            return;
        }
        BigDecimal precoLitro = req.valorTotal().divide(req.litros(), MathContext.DECIMAL128);
        if (!limites.precoValido(precoLitro)) {
            throw RegraNegocioException.invalido("Preço do diesel fora do limite de plausibilidade (R$ "
                    + LimitesPlausibilidade.br(precoLitro) + " por litro). Informe de " + limites.faixaPreco()
                    + "; confira se litros e valor não foram trocados.");
        }
        Integer capacidade = caminhao.getCapacidadeTanqueLitros();
        if (capacidade != null && req.litros().compareTo(BigDecimal.valueOf(capacidade)) > 0) {
            throw RegraNegocioException.invalido("Os litros abastecidos ultrapassam a capacidade do tanque do"
                    + " caminhão (" + capacidade + " L).");
        }
    }
}
