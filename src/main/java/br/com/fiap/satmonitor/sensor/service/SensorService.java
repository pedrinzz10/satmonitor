package br.com.fiap.satmonitor.sensor.service;

import br.com.fiap.satmonitor.auth.entity.Operador;
import br.com.fiap.satmonitor.exception.AcessoNegadoException;
import br.com.fiap.satmonitor.exception.EntityNotFoundException;
import br.com.fiap.satmonitor.missao.entity.OperadorMissao;
import br.com.fiap.satmonitor.missao.enums.RoleMissao;
import br.com.fiap.satmonitor.missao.repository.OperadorMissaoRepository;
import br.com.fiap.satmonitor.satelite.entity.Satelite;
import br.com.fiap.satmonitor.satelite.repository.SateliteRepository;
import br.com.fiap.satmonitor.sensor.dto.SensorRequest;
import br.com.fiap.satmonitor.sensor.dto.SensorResponse;
import br.com.fiap.satmonitor.sensor.entity.*;
import br.com.fiap.satmonitor.sensor.enums.*;
import br.com.fiap.satmonitor.sensor.repository.SensorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SensorService {

    private final SensorRepository sensorRepository;
    private final SateliteRepository sateliteRepository;
    private final OperadorMissaoRepository operadorMissaoRepository;

    @Transactional
    public SensorResponse criar(SensorRequest req, Operador operadorLogado) {
        Satelite satelite = sateliteRepository.findById(req.sateliteId())
                .orElseThrow(() -> new EntityNotFoundException("Satélite não encontrado com id: " + req.sateliteId()));

        verificarRole(satelite.getMissao().getId(), operadorLogado.getId(), RoleMissao.SUPERVISOR);

        if (req.limiteMin() >= req.limiteMax()) {
            throw new IllegalArgumentException("limiteMin deve ser menor que limiteMax");
        }

        if (sensorRepository.existsByNomeAndSateliteId(req.nome(), satelite.getId())) {
            throw new IllegalArgumentException(
                    "Já existe um sensor com o nome '" + req.nome() + "' neste satélite");
        }

        Sensor sensor = instanciarSubclasse(req);
        sensor.setNome(req.nome());
        sensor.setUnidade(req.unidade());
        sensor.setLimiteMin(req.limiteMin());
        sensor.setLimiteMax(req.limiteMax());
        sensor.setMargemAlerta(req.margemAlerta());
        sensor.setSatelite(satelite);

        sensorRepository.save(sensor);

        log.info("Sensor '{}' (tipo={}) criado no satélite id={} pelo operador '{}'",
                sensor.getNome(), req.tipo(), satelite.getId(), operadorLogado.getLogin());

        return toResponse(sensor);
    }

    @Transactional(readOnly = true)
    public Page<SensorResponse> listar(Pageable pageable) {
        return sensorRepository.findAll(pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<SensorResponse> listarPorSatelite(Long sateliteId, Pageable pageable) {
        if (!sateliteRepository.existsById(sateliteId)) {
            throw new EntityNotFoundException("Satélite não encontrado com id: " + sateliteId);
        }
        return sensorRepository.findBySateliteId(sateliteId, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public SensorResponse buscarPorId(Long id) {
        Sensor sensor = sensorRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Sensor não encontrado com id: " + id));
        return toResponse(sensor);
    }

    @Transactional
    public SensorResponse atualizar(Long id, SensorRequest req, Operador operadorLogado) {
        Sensor sensor = sensorRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Sensor não encontrado com id: " + id));

        verificarRole(sensor.getSatelite().getMissao().getId(), operadorLogado.getId(), RoleMissao.SUPERVISOR);

        if (req.limiteMin() >= req.limiteMax()) {
            throw new IllegalArgumentException("limiteMin deve ser menor que limiteMax");
        }

        sensor.setNome(req.nome());
        sensor.setUnidade(req.unidade());
        sensor.setLimiteMin(req.limiteMin());
        sensor.setLimiteMax(req.limiteMax());
        sensor.setMargemAlerta(req.margemAlerta());

        sensorRepository.save(sensor);

        log.info("Sensor id={} atualizado pelo operador '{}'", id, operadorLogado.getLogin());
        return toResponse(sensor);
    }

    @Transactional
    public void deletar(Long id, Operador operadorLogado) {
        Sensor sensor = sensorRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Sensor não encontrado com id: " + id));

        verificarRole(sensor.getSatelite().getMissao().getId(), operadorLogado.getId(), RoleMissao.DONO);

        sensorRepository.delete(sensor);
        log.info("Sensor id={} deletado pelo operador '{}'", id, operadorLogado.getLogin());
    }

    private Sensor instanciarSubclasse(SensorRequest req) {
        return switch (req.tipo()) {
            case TERMICO -> {
                if (req.unidadeEscala() == null || req.unidadeEscala().isBlank()) {
                    throw new IllegalArgumentException("unidadeEscala é obrigatório para sensores do tipo TERMICO");
                }
                SensorTermico s = new SensorTermico();
                s.setUnidadeEscala(UnidadeEscala.valueOf(req.unidadeEscala().toUpperCase()));
                yield s;
            }
            case PRESSAO -> {
                if (req.tipoPressao() == null || req.tipoPressao().isBlank()) {
                    throw new IllegalArgumentException("tipoPressao é obrigatório para sensores do tipo PRESSAO");
                }
                SensorPressao s = new SensorPressao();
                s.setTipoPressao(TipoPressao.valueOf(req.tipoPressao().toUpperCase()));
                yield s;
            }
            case RADIACAO -> {
                if (req.tipoRadiacao() == null || req.tipoRadiacao().isBlank()) {
                    throw new IllegalArgumentException("tipoRadiacao é obrigatório para sensores do tipo RADIACAO");
                }
                SensorRadiacao s = new SensorRadiacao();
                s.setTipoRadiacao(TipoRadiacao.valueOf(req.tipoRadiacao().toUpperCase()));
                yield s;
            }
            case MAGNETOMETRO -> {
                if (req.eixosMedicao() == null || req.eixosMedicao().isBlank()) {
                    throw new IllegalArgumentException("eixosMedicao é obrigatório para sensores do tipo MAGNETOMETRO");
                }
                Magnetometro s = new Magnetometro();
                s.setEixosMedicao(EixosMedicao.valueOf(req.eixosMedicao().toUpperCase()));
                yield s;
            }
        };
    }

    private void verificarRole(Long missaoId, Long operadorId, RoleMissao roleMinimo) {
        OperadorMissao vinculo = operadorMissaoRepository
                .findByMissaoIdAndOperadorId(missaoId, operadorId)
                .orElseThrow(() -> new AcessoNegadoException("Você não é membro desta missão"));

        if (!vinculo.getRole().temPermissao(roleMinimo)) {
            throw new AcessoNegadoException("Role mínima exigida: " + roleMinimo.name());
        }
    }

    private SensorResponse toResponse(Sensor sensor) {
        TipoSensor tipo = switch (sensor) {
            case SensorTermico t -> TipoSensor.TERMICO;
            case SensorPressao p -> TipoSensor.PRESSAO;
            case SensorRadiacao r -> TipoSensor.RADIACAO;
            case Magnetometro m -> TipoSensor.MAGNETOMETRO;
            default -> null;
        };

        String detalhe = switch (sensor) {
            case SensorTermico t ->
                    t.getUnidadeEscala() != null ? t.getUnidadeEscala().name() : null;
            case SensorPressao p ->
                    p.getTipoPressao() != null ? p.getTipoPressao().name() : null;
            case SensorRadiacao r ->
                    r.getTipoRadiacao() != null ? r.getTipoRadiacao().name() : null;
            case Magnetometro m ->
                    m.getEixosMedicao() != null ? m.getEixosMedicao().name() : null;
            default -> null;
        };

        return SensorResponse.builder()
                .id(sensor.getId())
                .nome(sensor.getNome())
                .tipo(tipo)
                .unidade(sensor.getUnidade())
                .limiteMin(sensor.getLimiteMin())
                .limiteMax(sensor.getLimiteMax())
                .margemAlerta(sensor.getMargemAlerta())
                .sateliteId(sensor.getSatelite().getId())
                .nomeSatelite(sensor.getSatelite().getNome())
                .detalhe(detalhe)
                .build();
    }
}
