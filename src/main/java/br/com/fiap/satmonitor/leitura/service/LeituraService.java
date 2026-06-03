package br.com.fiap.satmonitor.leitura.service;

import br.com.fiap.satmonitor.auth.entity.Operador;
import br.com.fiap.satmonitor.exception.AcessoNegadoException;
import br.com.fiap.satmonitor.exception.EntityNotFoundException;
import br.com.fiap.satmonitor.leitura.dto.LeituraRequest;
import br.com.fiap.satmonitor.leitura.dto.LeituraResponse;
import br.com.fiap.satmonitor.leitura.entity.LeituraSensor;
import br.com.fiap.satmonitor.leitura.enums.StatusLeitura;
import br.com.fiap.satmonitor.leitura.repository.LeituraRepository;
import br.com.fiap.satmonitor.missao.entity.OperadorMissao;
import br.com.fiap.satmonitor.missao.enums.RoleMissao;
import br.com.fiap.satmonitor.missao.repository.OperadorMissaoRepository;
import br.com.fiap.satmonitor.satelite.repository.SateliteRepository;
import br.com.fiap.satmonitor.sensor.entity.Sensor;
import br.com.fiap.satmonitor.sensor.repository.SensorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class LeituraService {

    private final LeituraRepository leituraRepository;
    private final SensorRepository sensorRepository;
    private final SateliteRepository sateliteRepository;
    private final StatusCalculator statusCalculator;
    private final OperadorMissaoRepository operadorMissaoRepository;

    @Transactional
    public LeituraResponse criar(LeituraRequest req) {
        Sensor sensor = sensorRepository.findById(req.sensorId())
                .orElseThrow(() -> new EntityNotFoundException("Sensor não encontrado com id: " + req.sensorId()));

        StatusLeitura status = statusCalculator.calcular(req.valor(), sensor);

        LeituraSensor leitura = LeituraSensor.builder()
                .valor(req.valor())
                .dataHoraLeitura(LocalDateTime.now())
                .status(status)
                .sensor(sensor)
                .build();

        leituraRepository.save(leitura);

        if (status == StatusLeitura.ALERTA || status == StatusLeitura.CRITICO) {
            log.warn("Leitura {} para sensor '{}' — status: {}", leitura.getId(), sensor.getNome(), status);
        }

        return toResponse(leitura);
    }

    @Transactional(readOnly = true)
    public Page<LeituraResponse> listar(Pageable pageable) {
        return leituraRepository.findAll(pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public LeituraResponse buscarPorId(Long id) {
        LeituraSensor leitura = leituraRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Leitura não encontrada com id: " + id));
        return toResponse(leitura);
    }

    @Transactional(readOnly = true)
    public Page<LeituraResponse> listarPorSensor(Long sensorId, StatusLeitura status, Pageable pageable) {
        if (!sensorRepository.existsById(sensorId)) {
            throw new EntityNotFoundException("Sensor não encontrado com id: " + sensorId);
        }

        Page<LeituraSensor> page = status != null
                ? leituraRepository.findBySensorIdAndStatus(sensorId, status, pageable)
                : leituraRepository.findBySensorId(sensorId, pageable);

        return page.map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<LeituraResponse> listarPorSatelite(Long sateliteId, StatusLeitura status, Pageable pageable) {
        if (!sateliteRepository.existsById(sateliteId)) {
            throw new EntityNotFoundException("Satélite não encontrado com id: " + sateliteId);
        }

        Page<LeituraSensor> page = status != null
                ? leituraRepository.findBySensorSateliteIdAndStatus(sateliteId, status, pageable)
                : leituraRepository.findBySensorSateliteId(sateliteId, pageable);

        return page.map(this::toResponse);
    }

    @Transactional
    public void deletar(Long id, Operador operadorLogado) {
        LeituraSensor leitura = leituraRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Leitura não encontrada com id: " + id));

        Long missaoId = leitura.getSensor().getSatelite().getMissao().getId();

        OperadorMissao vinculo = operadorMissaoRepository
                .findByMissaoIdAndOperadorId(missaoId, operadorLogado.getId())
                .orElseThrow(() -> new AcessoNegadoException("Você não é membro desta missão"));

        if (!vinculo.getRole().temPermissao(RoleMissao.SUPERVISOR)) {
            throw new AcessoNegadoException("Role mínima exigida: SUPERVISOR");
        }

        leituraRepository.delete(leitura);
        log.info("Leitura id={} deletada pelo operador '{}'", id, operadorLogado.getLogin());
    }

    private LeituraResponse toResponse(LeituraSensor leitura) {
        Sensor sensor = leitura.getSensor();
        return LeituraResponse.builder()
                .id(leitura.getId())
                .valor(leitura.getValor())
                .dataHoraLeitura(leitura.getDataHoraLeitura())
                .status(leitura.getStatus())
                .sensorId(sensor.getId())
                .nomeSensor(sensor.getNome())
                .sateliteId(sensor.getSatelite().getId())
                .nomeSatelite(sensor.getSatelite().getNome())
                .build();
    }
}
