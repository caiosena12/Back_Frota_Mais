package br.com.frotamais.viagem;

import org.springframework.stereotype.Component;

/**
 * Política de cálculo vigente: a versão das fórmulas (CalculadoraRentabilidade)
 * e os limites de plausibilidade configurados.
 *
 * Os serviços sempre pedem os limites a este componente no momento da
 * operação, em vez de guardá-los. Viagens em andamento usam a política atual a
 * cada consulta; viagens finalizadas usam a política gravada na fotografia
 * financeira (ResultadoConsolidado), e só uma correção explícita do frete
 * aplica a política atual a ele.
 */
@Component
public class PoliticaCalculo {

    private final LimitesPlausibilidade limites;

    public PoliticaCalculo(LimitesPlausibilidade limites) {
        this.limites = limites;
    }

    public LimitesPlausibilidade limites() {
        return limites;
    }
}
