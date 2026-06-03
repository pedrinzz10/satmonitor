package br.com.fiap.satmonitor.satelite.service;

import br.com.fiap.satmonitor.auth.entity.Operador;
import br.com.fiap.satmonitor.exception.AcessoNegadoException;
import br.com.fiap.satmonitor.exception.EntityNotFoundException;
import br.com.fiap.satmonitor.missao.entity.Missao;
import br.com.fiap.satmonitor.missao.entity.OperadorMissao;
import br.com.fiap.satmonitor.missao.enums.RoleMissao;
import br.com.fiap.satmonitor.missao.repository.MissaoRepository;
import br.com.fiap.satmonitor.missao.repository.OperadorMissaoRepository;
import br.com.fiap.satmonitor.satelite.dto.EstatisticasResponse;
import br.com.fiap.satmonitor.satelite.dto.SateliteRequest;
import br.com.fiap.satmonitor.satelite.dto.SateliteResponse;
import br.com.fiap.satmonitor.satelite.entity.CoordenadasOrbitais;
import br.com.fiap.satmonitor.satelite.entity.Satelite;
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
public class SateliteService {

    private final SateliteRepository sateliteRepository;
    private final MissaoRepository missaoRepository;
    private final OperadorMissaoRepository operadorMissaoRepository;

    @Transactional
    public SateliteResponse criar(SateliteRequest req, Operador operadorLogado) {
        Missao missao = missaoRepository.findById(req.missaoId())
                .orElseThrow(() -> new EntityNotFoundException("Missão não encontrada com id: " + req.missaoId()));

        verificarRole(missao.getId(), operadorLogado.getId(), RoleMissao.SUPERVISOR);

        if (sateliteRepository.existsByNomeAndMissaoId(req.nome(), missao.getId())) {
            throw new IllegalArgumentException(
                    "Já existe um satélite com o nome '" + req.nome() + "' nesta missão");
        }

        CoordenadasOrbitais coordenadas = CoordenadasOrbitais.builder()
                .altitudeKm(req.coordenadas().altitudeKm())
                .inclinacao(req.coordenadas().inclinacao())
                .longitudeNodo(req.coordenadas().longitudeNodo())
                .build();

        Satelite satelite = Satelite.builder()
                .nome(req.nome())
                .dataLancamento(req.dataLancamento())
                .coordenadas(coordenadas)
                .missao(missao)
                .build();

        sateliteRepository.save(satelite);

        log.info("Satélite '{}' criado na missão id={} pelo operador '{}'",
                satelite.getNome(), missao.getId(), operadorLogado.getLogin());

        return toResponse(satelite);
    }

    @Transactional(readOnly = true)
    public Page<SateliteResponse> listar(Pageable pageable) {
        return sateliteRepository.findAll(pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<SateliteResponse> listarPorMissao(Long missaoId, Pageable pageable) {
        if (!missaoRepository.existsById(missaoId)) {
            throw new EntityNotFoundException("Missão não encontrada com id: " + missaoId);
        }
        return sateliteRepository.findByMissaoId(missaoId, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public SateliteResponse buscarPorId(Long id) {
        Satelite satelite = sateliteRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Satélite não encontrado com id: " + id));
        return toResponse(satelite);
    }

    @Transactional
    public SateliteResponse atualizar(Long id, SateliteRequest req, Operador operadorLogado) {
        Satelite satelite = sateliteRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Satélite não encontrado com id: " + id));

        if (!missaoRepository.existsById(req.missaoId())) {
            throw new EntityNotFoundException("Missão não encontrada com id: " + req.missaoId());
        }

        verificarRole(satelite.getMissao().getId(), operadorLogado.getId(), RoleMissao.SUPERVISOR);

        satelite.setNome(req.nome());
        satelite.setDataLancamento(req.dataLancamento());
        satelite.setCoordenadas(CoordenadasOrbitais.builder()
                .altitudeKm(req.coordenadas().altitudeKm())
                .inclinacao(req.coordenadas().inclinacao())
                .longitudeNodo(req.coordenadas().longitudeNodo())
                .build());

        sateliteRepository.save(satelite);

        log.info("Satélite id={} atualizado pelo operador '{}'", id, operadorLogado.getLogin());
        return toResponse(satelite);
    }

    @Transactional
    public void deletar(Long id, Operador operadorLogado) {
        Satelite satelite = sateliteRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Satélite não encontrado com id: " + id));

        verificarRole(satelite.getMissao().getId(), operadorLogado.getId(), RoleMissao.DONO);

        sateliteRepository.delete(satelite);
        log.info("Satélite id={} deletado pelo operador '{}'", id, operadorLogado.getLogin());
    }

    @Transactional(readOnly = true)
    public EstatisticasResponse buscarEstatisticas(Long id) {
        Satelite satelite = sateliteRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Satélite não encontrado com id: " + id));

        return sateliteRepository.buscarEstatisticas(id)
                .orElse(new EstatisticasResponse(
                        satelite.getId(),
                        satelite.getNome(),
                        0.0, 0.0, 0.0,
                        0L, 0L, 0L,
                        null));
    }

    private void verificarRole(Long missaoId, Long operadorId, RoleMissao roleMinimo) {
        OperadorMissao vinculo = operadorMissaoRepository
                .findByMissaoIdAndOperadorId(missaoId, operadorId)
                .orElseThrow(() -> new AcessoNegadoException("Você não é membro desta missão"));

        if (!vinculo.getRole().temPermissao(roleMinimo)) {
            throw new AcessoNegadoException("Role mínima exigida: " + roleMinimo.name());
        }
    }

    private SateliteResponse toResponse(Satelite satelite) {
        CoordenadasOrbitais c = satelite.getCoordenadas();

        return SateliteResponse.builder()
                .id(satelite.getId())
                .nome(satelite.getNome())
                .dataLancamento(satelite.getDataLancamento())
                .altitudeKm(c != null ? c.getAltitudeKm() : null)
                .inclinacao(c != null ? c.getInclinacao() : null)
                .longitudeNodo(c != null ? c.getLongitudeNodo() : null)
                .missaoId(satelite.getMissao().getId())
                .nomeMissao(satelite.getMissao().getNome())
                .totalSensores(satelite.getSensores().size())
                .build();
    }
}
