package br.com.frotamais.viagem;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Cálculo financeiro de um frete. Função pura: não acessa banco nem contexto
 * de segurança, para poder ser testada isoladamente.
 *
 * Regras:
 * - Litros estimados de cada percurso = km ÷ consumo médio (km/L) informado.
 * - Custo do diesel de cada percurso = litros estimados × preço médio ponderado.
 *   Os valores pagos nos abastecimentos de diesel NÃO são somados como custo:
 *   eles só definem o preço. Somá-los causaria dupla contagem.
 * - Arla, pedágios e despesas extras entram pelo valor pago.
 * - Sem referência de preço, o custo do diesel fica nulo: nada é inventado.
 * - Valores em dinheiro com 2 casas (HALF_UP), arredondados só no fim.
 */
public final class CalculadoraRentabilidade {

    private static final MathContext PRECISAO = MathContext.DECIMAL128;
    private static final BigDecimal CEM = BigDecimal.valueOf(100);
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

    private CalculadoraRentabilidade() {
    }

    public record Entrada(
            BigDecimal valorFrete,
            Integer kmInicialVazio,
            Integer kmInicialCarregado,
            Integer kmFinalCarregado,
            BigDecimal consumoMedioVazio,
            BigDecimal consumoMedioCarregado,
            PrecoDiesel precoDiesel,
            BigDecimal custoArla,
            BigDecimal pedagios,
            BigDecimal despesasExtras) {
    }

    public static RentabilidadeResponse calcular(Entrada e) {
        List<String> pendencias = new ArrayList<>();
        PrecoDiesel preco = e.precoDiesel() != null ? e.precoDiesel() : PrecoDiesel.semReferencia();

        BigDecimal receita = e.valorFrete();
        if (receita == null) {
            pendencias.add("Valor do frete não informado.");
        }

        Integer kmVazio = kmVazio(e);
        Integer kmCarregado = kmCarregado(e);
        if (kmVazio == null) {
            pendencias.add("Quilometragem do carregamento não informada: o deslocamento vazio não foi encerrado.");
        }
        if (kmCarregado == null) {
            pendencias.add("Quilometragem final do percurso carregado não informada.");
        }

        Percurso vazio = percurso(kmVazio, e.consumoMedioVazio(), preco,
                "Consumo médio vazio não informado.", pendencias);
        Percurso carregado = percurso(kmCarregado, e.consumoMedioCarregado(), preco,
                "Consumo médio carregado não informado.", pendencias);

        if (!preco.disponivel() && (precisaDePreco(kmVazio) || precisaDePreco(kmCarregado))) {
            pendencias.add("Sem referência de preço do diesel: não há abastecimento de diesel nesta"
                    + " viagem nem em viagens anteriores do caminhão. O custo do diesel não foi estimado.");
        }

        BigDecimal arla = dinheiro(e.custoArla());
        BigDecimal pedagios = dinheiro(e.pedagios());
        BigDecimal extras = dinheiro(e.despesasExtras());

        BigDecimal custoTotal = null;
        if (vazio.custo() != null && carregado.custo() != null) {
            custoTotal = vazio.custo().add(carregado.custo()).add(arla).add(pedagios).add(extras);
        }

        BigDecimal lucro = null;
        BigDecimal margem = null;
        if (receita != null && custoTotal != null) {
            lucro = receita.subtract(custoTotal).setScale(2, RoundingMode.HALF_UP);
            margem = margem(lucro, receita);
        }

        return new RentabilidadeResponse(
                receita,
                kmVazio,
                kmCarregado,
                vazio.litros(),
                carregado.litros(),
                preco.precoMedio(),
                preco.origem(),
                preco.viagemReferenciaId(),
                preco.consolidadaEm(),
                vazio.custo(),
                carregado.custo(),
                arla,
                pedagios,
                extras,
                custoTotal,
                lucro,
                margem,
                pendencias.isEmpty(),
                List.copyOf(pendencias));
    }

    /** Margem percentual = lucro ÷ receita × 100, com 2 casas. Nula se não houver receita positiva. */
    public static BigDecimal margem(BigDecimal lucro, BigDecimal receita) {
        if (lucro == null || receita == null || receita.signum() <= 0) {
            return null;
        }
        return lucro.multiply(CEM).divide(receita, 2, RoundingMode.HALF_UP);
    }

    /** Sem km inicial vazio, não houve deslocamento vazio: 0 km. */
    static Integer kmVazio(Entrada e) {
        if (e.kmInicialVazio() == null) {
            return 0;
        }
        if (e.kmInicialCarregado() == null) {
            return null;
        }
        return e.kmInicialCarregado() - e.kmInicialVazio();
    }

    static Integer kmCarregado(Entrada e) {
        if (e.kmInicialCarregado() == null || e.kmFinalCarregado() == null) {
            return null;
        }
        return e.kmFinalCarregado() - e.kmInicialCarregado();
    }

    private record Percurso(BigDecimal litros, BigDecimal custo) {
    }

    private static Percurso percurso(Integer km, BigDecimal consumo, PrecoDiesel preco,
            String faltaConsumo, List<String> pendencias) {
        if (km == null) {
            return new Percurso(null, null);
        }
        if (km == 0) {
            return new Percurso(BigDecimal.ZERO.setScale(3), ZERO);
        }
        if (consumo == null || consumo.signum() <= 0) {
            pendencias.add(faltaConsumo);
            return new Percurso(null, null);
        }
        BigDecimal kmDecimal = BigDecimal.valueOf(km);
        BigDecimal litros = kmDecimal.divide(consumo, PRECISAO).setScale(3, RoundingMode.HALF_UP);
        if (!preco.disponivel()) {
            return new Percurso(litros, null);
        }
        // km × total pago ÷ (consumo × litros abastecidos): uma única divisão.
        BigDecimal custo = kmDecimal.multiply(preco.totalPago())
                .divide(consumo.multiply(preco.totalLitros()), PRECISAO)
                .setScale(2, RoundingMode.HALF_UP);
        return new Percurso(litros, custo);
    }

    private static boolean precisaDePreco(Integer km) {
        return km != null && km > 0;
    }

    private static BigDecimal dinheiro(BigDecimal valor) {
        return valor == null ? ZERO : valor.setScale(2, RoundingMode.HALF_UP);
    }
}
