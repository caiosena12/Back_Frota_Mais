package br.com.frotamais.seguranca;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Limite por IP nos POST públicos de login, registro e ativação de convite.
 * Roda antes da leitura e da validação do corpo: acima do limite, 429 no
 * formato padrão (TentativasExcedidasException, tratada pelo
 * ApiExceptionHandler) com Retry-After, e o serviço nem é chamado.
 */
@Configuration
class LimiteTentativasInterceptor implements HandlerInterceptor, WebMvcConfigurer {

    static final String LOGIN = "/api/auth/login";
    static final String REGISTRO = "/api/auth/registro";
    static final String ATIVACAO = "/api/convites/ativacao";

    private final LimitesTentativas limites;

    LimiteTentativasInterceptor(LimitesTentativas limites) {
        this.limites = limites;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(this).addPathPatterns("/api/auth/**", "/api/convites/**");
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!"POST".equals(request.getMethod())) {
            return true;
        }
        // IP já resolvido pelo servidor (com proxies confiáveis, no prod); nunca
        // lido do X-Forwarded-For aqui.
        String ip = request.getRemoteAddr();
        // Padrão de rota que o Spring casou (não a URI bruta): variações como
        // %6Cogin ou ;parametro caem no mesmo padrão e não escapam do limite.
        Object padrao = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        switch (String.valueOf(padrao)) {
            case LOGIN -> limites.contarLoginPorIp(ip);
            case REGISTRO -> limites.contarRegistroPorIp(ip);
            case ATIVACAO -> limites.contarAtivacaoPorIp(ip);
            default -> { }
        }
        return true;
    }
}
