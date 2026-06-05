package br.com.fiap.satmonitor.auth.service;

import br.com.fiap.satmonitor.exception.TooManyRequestsException;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class LoginRateLimiter {

    private static final int MAX_FALHAS = 5;
    private static final long JANELA_MS = 60_000;

    private final ConcurrentHashMap<String, Deque<Long>> falhas = new ConcurrentHashMap<>();

    public void verificarLimite(String login) {
        Deque<Long> timestamps = falhas.computeIfAbsent(login, k -> new ArrayDeque<>());
        long agora = System.currentTimeMillis();
        synchronized (timestamps) {
            while (!timestamps.isEmpty() && agora - timestamps.peekFirst() > JANELA_MS) {
                timestamps.pollFirst();
            }
            if (timestamps.size() >= MAX_FALHAS) {
                throw new TooManyRequestsException(
                        "Muitas tentativas de login. Aguarde 1 minuto antes de tentar novamente.");
            }
        }
    }

    public void registrarFalha(String login) {
        Deque<Long> timestamps = falhas.computeIfAbsent(login, k -> new ArrayDeque<>());
        synchronized (timestamps) {
            timestamps.addLast(System.currentTimeMillis());
        }
    }

    public void registrarSucesso(String login) {
        falhas.remove(login);
    }
}
