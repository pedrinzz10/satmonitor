package br.com.fiap.satmonitor.alerta.service;

import br.com.fiap.satmonitor.alerta.dto.AlertaResponse;
import br.com.fiap.satmonitor.alerta.entity.Alerta;
import br.com.fiap.satmonitor.alerta.enums.StatusAlerta;
import br.com.fiap.satmonitor.alerta.repository.AlertaRepository;
import br.com.fiap.satmonitor.auth.entity.Operador;
import br.com.fiap.satmonitor.exception.AcessoNegadoException;
import br.com.fiap.satmonitor.exception.EntityNotFoundException;
import br.com.fiap.satmonitor.missao.entity.OperadorMissao;
import br.com.fiap.satmonitor.missao.enums.RoleMissao;
import br.com.fiap.satmonitor.missao.repository.OperadorMissaoRepository;
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
    private final OperadorMissaoRepository operadorMissaoRepository;

    @Transactional(readOnly = true)
    public Page<AlertaResponse> listar(StatusAlerta status, Operador operadorLogado, Pageable pageable) {
        Page<Alerta> page = status != null
                ? alertaRepository.findByStatusAlertaAndOperadorMissoes(status, operadorLogado.getId(), pageable)
                : alertaRepository.findByOperadorMissoes(operadorLogado.getId(), pageable);
        return page.map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public AlertaResponse buscarPorId(Long id, Operador operadorLogado) {
        Alerta alerta = buscarEntidade(id);
        Long missaoId = alerta.getLeitura().getSensor().getSatelite().getMissao().getId();
        verificarRole(missaoId, operadorLogado.getId(), RoleMissao.MEMBRO);
        return toResponse(alerta);
    }

    @Transactional(readOnly = true)
    public Page<AlertaResponse> listarPorMissao(Long missaoId, StatusAlerta status,
                                                Operador operadorLogado, Pageable pageable) {
        verificarRole(missaoId, operadorLogado.getId(), RoleMissao.MEMBRO);
        Page<Alerta> page = status != null
                ? alertaRepository.findByMissaoIdAndStatus(missaoId, status, pageable)
                : alertaRepository.findByMissaoId(missaoId, pageable);
        return page.map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<AlertaResponse> listarPorSatelite(Long sateliteId, Pageable pageable) {
        if (!sateliteRepository.existsById(sateliteId)) {
            throw new EntityNotFoundException("Satélite não encontrado com id: " + sateliteId);
        }
        return alertaRepository.findBySateliteId(sateliteId, pageable).map(this::toResponse);
    }

    @Transactional
    public AlertaResponse atualizarStatus(Long id, StatusAlerta novoStatus, Operador operadorLogado) {
        Alerta alerta = buscarEntidade(id);

        Long missaoId = alerta.getLeitura().getSensor().getSatelite().getMissao().getId();
        verificarRole(missaoId, operadorLogado.getId(), RoleMissao.SUPERVISOR);

        alerta.setStatusAlerta(novoStatus);
        alertaRepository.save(alerta);
        log.info("Alerta id={} atualizado para {} pelo operador '{}'", id, novoStatus, operadorLogado.getLogin());
        return toResponse(alerta);
    }

    private void verificarRole(Long missaoId, Long operadorId, RoleMissao roleMinimo) {
        OperadorMissao vinculo = operadorMissaoRepository
                .findByMissaoIdAndOperadorId(missaoId, operadorId)
                .orElseThrow(() -> new AcessoNegadoException("Você não é membro desta missão"));

        if (!vinculo.getRole().temPermissao(roleMinimo)) {
            throw new AcessoNegadoException("Role mínima exigida: " + roleMinimo.name());
        }
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
