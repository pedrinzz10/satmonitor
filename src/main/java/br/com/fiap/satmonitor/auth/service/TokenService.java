package br.com.fiap.satmonitor.auth.service;

import br.com.fiap.satmonitor.auth.entity.Operador;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Slf4j
@Service
public class TokenService {

    private static final String ISSUER = "satmonitor";

    @Value("${api.security.token.secret}")
    private String secret;

    public String gerarToken(Operador operador) {
        return JWT.create()
                .withIssuer(ISSUER)
                .withSubject(operador.getLogin())
                .withExpiresAt(Instant.now().plus(8, ChronoUnit.HOURS))
                .sign(Algorithm.HMAC256(secret));
    }

    public String validarToken(String token) {
        try {
            return JWT.require(Algorithm.HMAC256(secret))
                    .withIssuer(ISSUER)
                    .build()
                    .verify(token)
                    .getSubject();
        } catch (JWTVerificationException e) {
            log.warn("Token JWT inválido: {}", e.getMessage());
            throw e;
        }
    }
}
