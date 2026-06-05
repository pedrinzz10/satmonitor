package br.com.fiap.satmonitor.alerta.service;

import br.com.fiap.satmonitor.alerta.dto.AlertaResponse;
import br.com.fiap.satmonitor.alerta.entity.Alerta;
import br.com.fiap.satmonitor.alerta.enums.StatusAlerta;
import br.com.fiap.satmonitor.alerta.repository.AlertaRepository;
import br.com.fiap.satmonitor.exception.EntityNotFoundException;
import br.com.fiap.satmonitor.satelite.repository.SateliteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlertaService {

    private final AlertaRepository alertaRepository;
    private final SateliteRepository sateliteRepository;

    @Transactional(readOnly = true)
    public Page<AlertaResponse> listar(StatusAlerta status, Pageable pageable) {
        Page<Alerta> page = status != null
                ? alertaRepository.findByStatusAlerta(status, pageable)
                : alertaRepository.findAll(pageable);
        return page.map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public AlertaResponse buscarPorId(Long id) {
        return toResponse(buscarEntidade(id));
    }

    @Transactional(readOnly = true)
    public Page<AlertaResponse> listarPorSatelite(Long sateliteId, Pageable pageable) {
        if (!sateliteRepository.existsById(sateliteId)) {
            throw new EntityNotFoundException("Satélite não encontrado com id: " + sateliteId);
        }
        return alertaRepository.findBySateliteId(sateliteId, pageable).map(this::toResponse);
    }

    @Transactional
    public AlertaResponse atualizarStatus(Long id, StatusAlerta novoStatus) {
        Alerta alerta = buscarEntidade(id);
        alerta.setStatusAlerta(novoStatus);
        alertaRepository.save(alerta);
        log.info("Alerta id={} atualizado para {}", id, novoStatus);
        return toResponse(alerta);
    }

    private Alerta buscarEntidade(Long id) {
        return alertaRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Alerta não encontrado com id: " + id));
    }

    private AlertaResponse toResponse(Alerta alerta) {
        var leitura = alerta.getLeitura();
        var sensor = leitura.getSensor();
        return AlertaResponse.builder()
                .id(alerta.getId())
                .leituraId(leitura.getId())
                .valorLeitura(leitura.getValor())
                .nomeSensor(sensor.getNome())
                .nomeSatelite(sensor.getSatelite().getNome())
                .tipoAlerta(alerta.getTipoAlerta())
                .descricao(alerta.getDescricao())
                .dataAlerta(alerta.getDataAlerta())
                .statusAlerta(alerta.getStatusAlerta())
                .build();
    }
}
