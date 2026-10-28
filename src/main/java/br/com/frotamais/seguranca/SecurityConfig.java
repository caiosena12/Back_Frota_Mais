package br.com.frotamais.seguranca;

import java.util.Base64;
import java.util.List;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

import br.com.frotamais.usuario.Usuario;
import br.com.frotamais.usuario.UsuarioRepository;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final UsuarioRepository usuarios;
    private final SecretKey chave;

    public SecurityConfig(
            UsuarioRepository usuarios,
            @Value("${app.jwt.secret}") String segredoBase64) {
        this.usuarios = usuarios;

        final byte[] segredo;
        try {
            segredo = Base64.getDecoder().decode(segredoBase64.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("JWT_SECRET deve ser uma chave em Base64.", e);
        }

        if (segredo.length < 32) {
            throw new IllegalArgumentException(
                    "JWT_SECRET deve representar pelo menos 32 bytes. Gere com openssl rand -base64 32");
        }

        this.chave = new SecretKeySpec(segredo, "HmacSHA256");
    }

    @Bean
    SecurityFilterChain filtros(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        .requestMatchers(HttpMethod.POST,
                                "/api/auth/registro", "/api/auth/login").permitAll()
                        .requestMatchers("/error").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(o ->
                        o.jwt(j -> j.jwtAuthenticationConverter(conversor())))
                .build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    JwtDecoder jwtDecoder() {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(chave)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();

        decoder.setJwtValidator(
                JwtValidators.createDefaultWithIssuer("frota-mais"));
        return decoder;
    }

    @Bean
    JwtEncoder jwtEncoder() {
        return new NimbusJwtEncoder(new ImmutableSecret<>(chave));
    }

    private Converter<Jwt, AbstractAuthenticationToken> conversor() {
        return jwt -> {
            final Long id;
            try {
                id = Long.valueOf(jwt.getSubject());
            } catch (RuntimeException e) {
                throw new BadCredentialsException("Identificador inválido no token.", e);
            }

            Usuario usuario = usuarios.findById(id)
                    .filter(Usuario::isAtivo)
                    .orElseThrow(() ->
                            new BadCredentialsException("Usuário inativo ou inexistente."));

            Object claim = jwt.getClaims().get("empresaId");
            if (!(claim instanceof Number empresaToken)
                    || usuario.getEmpresa() == null
                    || usuario.getEmpresa().getId() == null
                    || !usuario.getEmpresa().getId().equals(empresaToken.longValue())) {
                throw new BadCredentialsException("Token não corresponde à empresa atual.");
            }

            return new JwtAuthenticationToken(
                    jwt,
                    List.of(new SimpleGrantedAuthority(
                            "ROLE_" + usuario.getPerfil().name())),
                    jwt.getSubject());
        };
    }
}
