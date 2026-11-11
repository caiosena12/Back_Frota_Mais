package br.com.frotamais.convite;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Configuração dos convites.
 *
 * app.convites.validade-horas: validade do link. Padrão: 24 h, a regra do
 * produto; outro valor só por configuração explícita.
 * app.convites.url-ativacao: página do front-end que recebe ?token=... e
 * chama POST /api/convites/ativacao. Sem ela nenhum convite é enviado.
 */
@Component
public class ConfigConvites {

    private final Duration validade;
    private final String urlAtivacao;

    public ConfigConvites(
            @Value("${app.convites.validade-horas:24}") long validadeHoras,
            @Value("${app.convites.url-ativacao:}") String urlAtivacao) {
        if (validadeHoras < 1 || validadeHoras > 720) {
            throw new IllegalArgumentException("app.convites.validade-horas deve estar entre 1 e 720.");
        }
        this.validade = Duration.ofHours(validadeHoras);
        this.urlAtivacao = urlAtivacao == null ? "" : urlAtivacao.trim();
    }

    public Duration validade() {
        return validade;
    }

    public String urlAtivacao() {
        return urlAtivacao;
    }

    public boolean urlConfigurada() {
        return !urlAtivacao.isEmpty();
    }
}
