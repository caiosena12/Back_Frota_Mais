package br.com.frotamais.seguranca;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import br.com.frotamais.usuario.Usuario;

@Service
public class TokenService {

    private final JwtEncoder encoder;
    private final long horas;

    public TokenService(
            JwtEncoder encoder,
            @Value("${app.jwt.horas:8}") long horas) {
        if (horas < 1 || horas > 24) {
            throw new IllegalArgumentException("app.jwt.horas deve estar entre 1 e 24.");
        }
        this.encoder = encoder;
        this.horas = horas;
    }

    public String gerar(Usuario usuario) {
        Instant agora = Instant.now();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("frota-mais")
                .issuedAt(agora)
                .expiresAt(agora.plus(horas, ChronoUnit.HOURS))
                .subject(usuario.getId().toString())
                .claim("empresaId", usuario.getEmpresa().getId())
                .claim("perfil", usuario.getPerfil().name())
                .build();

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
