package br.com.frotamais.viagem;

import java.math.BigDecimal;
import java.math.MathContext;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;

import br.com.frotamais.caminhao.Caminhao;
import br.com.frotamais.comum.RegraNegocioException;

/**
 * Validações de abastecimentos, pedágios e despesas extras, usadas tanto nos
 * lançamentos de uma viagem em andamento quanto no cadastro de viagem
 * histórica. Funções puras: recebem tudo o que precisam.
 */
final class RegrasLancamento {

    /**
     * Palavras que identificam alimentação, já normalizadas (sem acento,
     * minúsculas), no singular e no plural. "Café da manhã" é reconhecido
     * pela palavra "cafe".
     */
    private static final Set<String> ALIMENTACAO = Set.of(
            "alimentacao", "alimentacoes",
            "refeicao", "refeicoes",
            "lanche", "lanches",
            "almoco", "almocos",
            "jantar", "jantares",
            "cafe", "cafes",
            "restaurante", "restaurantes",
            "marmita", "marmitas",
            "comida", "comidas");

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

    /** Descrição de despesa extra aceita (sem espaços nas pontas). Alimentação é recusada. */
    static String descricaoExtra(String descricao) {
        if (ehAlimentacao(descricao)) {
            throw RegraNegocioException.invalido(
                    "Alimentação não é despesa do frete e não entra nos custos do Frota+.");
        }
        return descricao.trim();
    }

    /**
     * Alimentação não é custo do Frota+. A descrição é normalizada (sem
     * acentos, minúscula, pontuação e espaços repetidos viram um separador) e
     * quebrada em palavras; é recusada se alguma palavra for um dos termos de
     * ALIMENTACAO. A comparação é por palavra inteira, para não bloquear por
     * acidente palavras que só contêm as letras de um termo (como
     * "almoxarifado" ou "cafeicultura").
     */
    static boolean ehAlimentacao(String descricao) {
        String normalizada = Normalizer.normalize(descricao, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
        return Arrays.stream(normalizada.split("[^a-z0-9]+")).anyMatch(ALIMENTACAO::contains);
    }
}
