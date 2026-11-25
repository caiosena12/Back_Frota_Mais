package br.com.frotamais.seguranca;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * CORS da API, montado a partir de app.cors.origens-permitidas
 * (CORS_ORIGENS_PERMITIDAS).
 *
 * - Só origens exatas (esquema + host + porta). Curinga ("*"), caminho, query
 *   e credenciais na URL são recusados ao subir: um erro de digitação não pode
 *   virar uma liberação ampla.
 * - Lista vazia: nenhuma origem externa é liberada (desenvolvimento com o
 *   proxy do Angular). No perfil prod a lista é obrigatória e só aceita https.
 * - Só os métodos que a API usa e os cabeçalhos Authorization, Content-Type e
 *   Accept. Sem cookies (allowCredentials=false): o token vai no cabeçalho.
 */
final class ConfigCors {

    static final List<String> METODOS = List.of("GET", "POST", "PUT", "DELETE", "OPTIONS");
    static final List<String> CABECALHOS = List.of("Authorization", "Content-Type", "Accept");
    static final long CACHE_PREFLIGHT_SEGUNDOS = 3600;

    private ConfigCors() {
    }

    static List<String> origens(String texto, boolean producao) {
        List<String> origens = new ArrayList<>();
        if (texto != null) {
            for (String parte : texto.split(",")) {
                String origem = parte.trim();
                if (!origem.isEmpty()) {
                    String normalizada = normalizar(origem, producao);
                    if (!origens.contains(normalizada)) {
                        origens.add(normalizada);
                    }
                }
            }
        }
        if (producao && origens.isEmpty()) {
            throw new IllegalStateException("CORS_ORIGENS_PERMITIDAS é obrigatória no perfil prod: "
                    + "informe a origem https exata do front-end (ex.: https://app.exemplo.com).");
        }
        return List.copyOf(origens);
    }

    private static String normalizar(String origem, boolean producao) {
        if (origem.contains("*")) {
            throw invalida(origem, "curingas não são aceitos; informe a origem exata");
        }
        final URI uri;
        try {
            uri = new URI(origem);
        } catch (URISyntaxException e) {
            throw invalida(origem, "não é uma URL válida");
        }
        String esquema = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!esquema.equals("https") && !esquema.equals("http")) {
            throw invalida(origem, "use http:// ou https://");
        }
        if (producao && !esquema.equals("https")) {
            throw invalida(origem, "no perfil prod só https é aceito");
        }
        if (uri.getHost() == null || uri.getRawUserInfo() != null) {
            throw invalida(origem, "informe só esquema, host e porta");
        }
        String caminho = uri.getRawPath();
        if ((caminho != null && !caminho.isEmpty() && !caminho.equals("/"))
                || uri.getRawQuery() != null || uri.getRawFragment() != null) {
            throw invalida(origem, "a origem não tem caminho, query nem fragmento");
        }
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        return esquema + "://" + host + (uri.getPort() == -1 ? "" : ":" + uri.getPort());
    }

    private static IllegalStateException invalida(String origem, String motivo) {
        return new IllegalStateException("Origem CORS inválida '" + origem + "': " + motivo + ".");
    }

    static CorsConfigurationSource fonte(List<String> origens) {
        UrlBasedCorsConfigurationSource fonte = new UrlBasedCorsConfigurationSource();
        if (!origens.isEmpty()) {
            CorsConfiguration cfg = new CorsConfiguration();
            cfg.setAllowedOrigins(origens);
            cfg.setAllowedMethods(METODOS);
            cfg.setAllowedHeaders(CABECALHOS);
            cfg.setAllowCredentials(false);
            cfg.setMaxAge(CACHE_PREFLIGHT_SEGUNDOS);
            fonte.registerCorsConfiguration("/**", cfg);
        }
        return fonte;
    }
}
