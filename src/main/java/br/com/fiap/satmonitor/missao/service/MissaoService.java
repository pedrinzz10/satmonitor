package br.com.fiap.satmonitor.missao.service;

import br.com.fiap.satmonitor.agencia.entity.Agencia;
import br.com.fiap.satmonitor.agencia.repository.AgenciaRepository;
import br.com.fiap.satmonitor.auth.entity.Operador;
import br.com.fiap.satmonitor.exception.*;
import br.com.fiap.satmonitor.missao.dto.*;
import br.com.fiap.satmonitor.missao.entity.Missao;
import br.com.fiap.satmonitor.missao.entity.OperadorMissao;
import br.com.fiap.satmonitor.missao.entity.SolicitacaoEntrada;
import br.com.fiap.satmonitor.missao.enums.RoleMissao;
import br.com.fiap.satmonitor.missao.enums.StatusSolicitacao;
import br.com.fiap.satmonitor.missao.repository.MissaoRepository;
import br.com.fiap.satmonitor.missao.repository.OperadorMissaoRepository;
import br.com.fiap.satmonitor.missao.repository.SolicitacaoEntradaRepository;
import br.com.fiap.satmonitor.satelite.repository.SateliteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MissaoService {

    private final MissaoRepository missaoRepository;
    private final OperadorMissaoRepository operadorMissaoRepository;
    private final SolicitacaoEntradaRepository solicitacaoRepository;
    private final SateliteRepository sateliteRepository;
    private final PasswordEncoder passwordEncoder;
    private final AgenciaRepository agenciaRepository;

    @Transactional
    public MissaoResponse criar(MissaoRequest req, Operador operadorLogado) {
        Agencia agencia = resolverAgencia(req.agenciaId());

        Missao missao = Missao.builder()
                .nome(req.nome())
                .descricao(req.descricao())
                .dataLancamento(req.dataLancamento())
                .status(req.status())
                .senhaMissao(passwordEncoder.encode(req.senhaMissao()))
                .operadorDono(operadorLogado)
                .agencia(agencia)
                .objetivo(req.objetivo())
                .dataFimPrevista(req.dataFimPrevista())
                .permitirCowork(req.permitirCowork() != null && req.permitirCowork())
                .build();

        missaoRepository.save(missao);

        OperadorMissao vinculo = OperadorMissao.builder()
                .operador(operadorLogado)
                .missao(missao)
                .role(RoleMissao.DONO)
                .dataEntrada(LocalDateTime.now())
                .build();

        operadorMissaoRepository.save(vinculo);

        log.info("Missão '{}' criada pelo operador '{}'", missao.getNome(), operadorLogado.getLogin());
        return toResponse(missao, RoleMissao.DONO);
    }

    @Transactional(readOnly = true)
    public Page<MissaoResponse> listar(Operador operadorLogado, Pageable pageable) {
        return missaoRepository.findByMembrosOperadorId(operadorLogado.getId(), pageable)
                .map(missao -> {
                    RoleMissao role = operadorMissaoRepository
                            .findByMissaoIdAndOperadorId(missao.getId(), operadorLogado.getId())
                            .map(OperadorMissao::getRole)
                            .orElse(RoleMissao.MEMBRO);
                    return toResponse(missao, role);
                });
    }

    @Transactional(readOnly = true)
    public Page<MissaoResponse> buscarPorNome(String nome, Pageable pageable) {
        return missaoRepository.findByNomeContainingIgnoreCase(nome, pageable)
                .map(m -> toResponse(m, RoleMissao.MEMBRO));
    }

    @Transactional(readOnly = true)
    public MissaoResponse buscarPorId(Long id, Operador operadorLogado) {
        Missao missao = missaoRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Missão não encontrada com id: " + id));

        OperadorMissao vinculo = operadorMissaoRepository
                .findByMissaoIdAndOperadorId(id, operadorLogado.getId())
                .orElseThrow(() -> new AcessoNegadoException("Você não é membro desta missão"));

        return toResponse(missao, vinculo.getRole());
    }

    @Transactional
    public MissaoResponse atualizar(Long id, MissaoUpdateRequest req, Operador operadorLogado) {
        Missao missao = missaoRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Missão não encontrada com id: " + id));

        verificarRole(id, operadorLogado.getId(), RoleMissao.DONO);

        missao.setNome(req.nome());
        missao.setDescricao(req.descricao());
        missao.setDataLancamento(req.dataLancamento());
        missao.setStatus(req.status());
        missao.setAgencia(resolverAgencia(req.agenciaId()));
        missao.setObjetivo(req.objetivo());
        missao.setDataFimPrevista(req.dataFimPrevista());
        missao.setPermitirCowork(req.permitirCowork() != null && req.permitirCowork());

        missaoRepository.save(missao);

        log.info("Missão '{}' atualizada pelo operador '{}'", missao.getNome(), operadorLogado.getLogin());
        return toResponse(missao, RoleMissao.DONO);
    }

    @Transactional
    public void deletar(Long id, Operador operadorLogado) {
        Missao missao = missaoRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Missão não encontrada com id: " + id));

        verificarRole(id, operadorLogado.getId(), RoleMissao.DONO);

        missaoRepository.delete(missao);
        log.info("Missão id={} deletada pelo operador '{}'", id, operadorLogado.getLogin());
    }

    @Transactional
    public SolicitacaoResponse solicitarEntrada(Long id, EntrarMissaoRequest req, Operador operadorLogado) {
        Missao missao = missaoRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Missão não encontrada com id: " + id));

        if (operadorLogado.getAgencia() == null) {
            throw new IllegalArgumentException("Operador não possui agência vinculada");
        }

        if (!missao.getPermitirCowork() && missao.getAgencia() != null) {
            if (!missao.getAgencia().getId().equals(operadorLogado.getAgencia().getId())) {
                throw new AcessoNegadoException("Sua agência não tem permissão para entrar nesta missão");
            }
        }

        if (!passwordEncoder.matches(req.senha(), missao.getSenhaMissao())) {
            throw new SenhaMissaoInvalidaException("Senha da missão incorreta");
        }

        if (operadorMissaoRepository.existsByMissaoIdAndOperadorId(id, operadorLogado.getId())) {
            throw new OperadorJaMembroException("Operador já é membro desta missão");
        }

        if (solicitacaoRepository.existsByMissaoIdAndOperadorIdAndStatus(id, operadorLogado.getId(), StatusSolicitacao.PENDENTE)) {
            throw new OperadorJaMembroException("Já existe uma solicitação pendente para esta missão");
        }

        SolicitacaoEntrada solicitacao = SolicitacaoEntrada.builder()
                .operador(operadorLogado)
                .missao(missao)
                .build();

        solicitacaoRepository.save(solicitacao);
        log.info("Operador '{}' solicitou entrada na missão id={}", operadorLogado.getLogin(), id);
        return toSolicitacaoResponse(solicitacao);
    }

    @Transactional(readOnly = true)
    public Page<SolicitacaoResponse> listarSolicitacoes(Long missaoId, StatusSolicitacao status, Pageable pageable, Operador operadorLogado) {
        verificarRole(missaoId, operadorLogado.getId(), RoleMissao.SUPERVISOR);
        return solicitacaoRepository.findByMissaoIdAndStatus(missaoId, status, pageable)
                .map(this::toSolicitacaoResponse);
    }

    @Transactional
    public void responderSolicitacao(Long missaoId, Long solicitacaoId, boolean aprovar, Operador operadorLogado) {
        verificarRole(missaoId, operadorLogado.getId(), RoleMissao.SUPERVISOR);

        SolicitacaoEntrada solicitacao = solicitacaoRepository.findById(solicitacaoId)
                .filter(s -> s.getMissao().getId().equals(missaoId))
                .orElseThrow(() -> new EntityNotFoundException("Solicitação não encontrada"));

        if (solicitacao.getStatus() != StatusSolicitacao.PENDENTE) {
            throw new IllegalArgumentException("Solicitação já foi respondida");
        }

        solicitacao.setDataResposta(LocalDateTime.now());
        solicitacao.setRespondidoPor(operadorLogado);

        if (aprovar) {
            solicitacao.setStatus(StatusSolicitacao.APROVADO);
            OperadorMissao vinculo = OperadorMissao.builder()
                    .operador(solicitacao.getOperador())
                    .missao(solicitacao.getMissao())
                    .role(RoleMissao.MEMBRO)
                    .dataEntrada(LocalDateTime.now())
                    .build();
            operadorMissaoRepository.save(vinculo);
            log.info("Solicitação id={} aprovada: operador '{}' entrou na missão id={}",
                    solicitacaoId, solicitacao.getOperador().getLogin(), missaoId);
        } else {
            solicitacao.setStatus(StatusSolicitacao.REJEITADO);
            log.info("Solicitação id={} rejeitada pelo operador '{}'", solicitacaoId, operadorLogado.getLogin());
        }

        solicitacaoRepository.save(solicitacao);
    }

    @Transactional
    public void sair(Long id, Operador operadorLogado) {
        OperadorMissao vinculo = operadorMissaoRepository
                .findByMissaoIdAndOperadorId(id, operadorLogado.getId())
                .orElseThrow(() -> new EntityNotFoundException("Você não é membro desta missão"));

        if (vinculo.getRole() == RoleMissao.DONO) {
            long totalDonos = operadorMissaoRepository.countByMissaoIdAndRole(id, RoleMissao.DONO);
            if (totalDonos <= 1) {
                throw new DonoUnicoException("Transfira a propriedade da missão antes de sair");
            }
        }

        operadorMissaoRepository.delete(vinculo);
        log.info("Operador '{}' saiu da missão id={}", operadorLogado.getLogin(), id);
    }

    @Transactional(readOnly = true)
    public List<MembroResponse> listarMembros(Long missaoId, Operador operadorLogado) {
        if (!operadorMissaoRepository.existsByMissaoIdAndOperadorId(missaoId, operadorLogado.getId())) {
            throw new AcessoNegadoException("Você não é membro desta missão");
        }

        return operadorMissaoRepository.findByMissaoId(missaoId).stream()
                .map(this::toMembroResponse)
                .toList();
    }

    @Transactional
    public void removerMembro(Long missaoId, Long membroId, Operador operadorLogado) {
        verificarRole(missaoId, operadorLogado.getId(), RoleMissao.DONO);

        if (operadorLogado.getId().equals(membroId)) {
            throw new AcessoNegadoException("Use o endpoint /sair para sair da missão");
        }

        OperadorMissao vinculo = operadorMissaoRepository
                .findByMissaoIdAndOperadorId(missaoId, membroId)
                .orElseThrow(() -> new EntityNotFoundException("Membro não encontrado na missão"));

        operadorMissaoRepository.delete(vinculo);
        log.info("Membro id={} removido da missão id={} pelo operador '{}'",
                membroId, missaoId, operadorLogado.getLogin());
    }

    @Transactional
    public MembroResponse promoverMembro(Long missaoId, Long membroId, RoleMissao novoRole, Operador operadorLogado) {
        verificarRole(missaoId, operadorLogado.getId(), RoleMissao.DONO);

        if (operadorLogado.getId().equals(membroId)) {
            throw new AcessoNegadoException("Não é possível alterar a própria role");
        }

        OperadorMissao vinculo = operadorMissaoRepository
                .findByMissaoIdAndOperadorId(missaoId, membroId)
                .orElseThrow(() -> new EntityNotFoundException("Membro não encontrado na missão"));

        vinculo.setRole(novoRole);
        operadorMissaoRepository.save(vinculo);

        log.info("Membro id={} promovido para {} na missão id={} pelo operador '{}'",
                membroId, novoRole, missaoId, operadorLogado.getLogin());
        return toMembroResponse(vinculo);
    }

    private void verificarRole(Long missaoId, Long operadorId, RoleMissao roleMinimo) {
        OperadorMissao vinculo = operadorMissaoRepository
                .findByMissaoIdAndOperadorId(missaoId, operadorId)
                .orElseThrow(() -> new EntityNotFoundException("Você não é membro desta missão"));

        if (!vinculo.getRole().temPermissao(roleMinimo)) {
            throw new AcessoNegadoException("Role mínima exigida: " + roleMinimo.name());
        }
    }

    private MissaoResponse toResponse(Missao missao, RoleMissao roleDoOperador) {
        int totalMembros = (int) operadorMissaoRepository.countByMissaoId(missao.getId());
        int totalSatelites = (int) sateliteRepository.countByMissaoId(missao.getId());

        return MissaoResponse.builder()
                .id(missao.getId())
                .nome(missao.getNome())
                .descricao(missao.getDescricao())
                .dataLancamento(missao.getDataLancamento())
                .status(missao.getStatus())
                .roleDoOperador(roleDoOperador.name())
                .totalMembros(totalMembros)
                .totalSatelites(totalSatelites)
                .agenciaId(missao.getAgencia() != null ? missao.getAgencia().getId() : null)
                .nomeAgencia(missao.getAgencia() != null ? missao.getAgencia().getNome() : null)
                .objetivo(missao.getObjetivo())
                .dataFimPrevista(missao.getDataFimPrevista())
                .permitirCowork(missao.getPermitirCowork())
                .build();
    }

    private Agencia resolverAgencia(Long agenciaId) {
        if (agenciaId == null) return null;
        return agenciaRepository.findById(agenciaId)
                .orElseThrow(() -> new EntityNotFoundException("Agência não encontrada com id: " + agenciaId));
    }

    private SolicitacaoResponse toSolicitacaoResponse(SolicitacaoEntrada s) {
        var agencia = s.getOperador().getAgencia();
        return new SolicitacaoResponse(
                s.getId(),
                s.getOperador().getId(),
                s.getOperador().getNome(),
                agencia != null ? agencia.getId() : null,
                agencia != null ? agencia.getNome() : null,
                s.getStatus(),
                s.getDataSolicitacao()
        );
    }

    private MembroResponse toMembroResponse(OperadorMissao om) {
        return MembroResponse.builder()
                .operadorId(om.getOperador().getId())
                .nome(om.getOperador().getNome())
                .login(om.getOperador().getLogin())
                .role(om.getRole())
                .dataEntrada(om.getDataEntrada())
                .build();
    }
}
