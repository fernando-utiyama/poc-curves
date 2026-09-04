package com.poccurves.engine.adapter.out.cache;

import com.poccurves.engine.application.CacheInterpolacaoPort;
import com.poccurves.engine.dto.EngineDtos.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class RedisCacheInterpolacaoAdapter implements CacheInterpolacaoPort {

    private static final Logger log = LoggerFactory.getLogger(RedisCacheInterpolacaoAdapter.class);

    private final RedisTemplate<String, String> redisTemplate;

    public RedisCacheInterpolacaoAdapter(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Optional<ItemInterpolacaoResultado> buscar(String chave, int prazoDiasUteis) {
        try {
            String valorCacheado = redisTemplate.opsForValue().get(chave);
            if (valorCacheado == null) {
                return Optional.empty();
            }
            String[] partes = valorCacheado.split("\\|", 4);
            String status = partes[0];
            String taxa = (partes.length > 1 && !partes[1].isEmpty()) ? partes[1] : null;
            String fatorDesconto = (partes.length > 2 && !partes[2].isEmpty()) ? partes[2] : null;
            String erroMensagem = (partes.length > 3 && !partes[3].isEmpty()) ? partes[3] : null;
            return Optional.of(new ItemInterpolacaoResultado(prazoDiasUteis, taxa, fatorDesconto, status, erroMensagem));
        } catch (RuntimeException e) {
            log.warn("Falha ao buscar chave '{}' no Redis: {}", chave, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void armazenar(String chave, ItemInterpolacaoResultado item) {
        try {
            String status = item.status() != null ? item.status() : "";
            String taxa = item.taxa() != null ? item.taxa() : "";
            String fatorDesconto = item.fatorDesconto() != null ? item.fatorDesconto() : "";
            String erroMensagem = item.erroMensagem() != null ? item.erroMensagem() : "";
            String valorSerializado = status + "|" + taxa + "|" + fatorDesconto + "|" + erroMensagem;
            redisTemplate.opsForValue().set(chave, valorSerializado);
        } catch (RuntimeException e) {
            log.warn("Falha ao armazenar chave '{}' no Redis: {}", chave, e.getMessage());
        }
    }
}
