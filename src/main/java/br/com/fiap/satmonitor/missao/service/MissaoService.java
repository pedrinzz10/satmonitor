package br.com.fiap.satmonitor.missao.service;

import br.com.fiap.satmonitor.auth.entity.Operador;
import br.com.fiap.satmonitor.exception.*;
import br.com.fiap.satmonitor.missao.dto.*;
import br.com.fiap.satmonitor.missao.entity.Missao;
import br.com.fiap.satmonitor.missao.entity.OperadorMissao;
import br.com.fiap.satmonitor.missao.enums.RoleMissao;
import br.com.fiap.satmonitor.missao.repository.MissaoRepository;
import br.com.fiap.satmonitor.missao.repository.OperadorMissaoRepository;
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
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public MissaoResponse criar(MissaoRequest req, Operador operadorLogado) {
        Missao missao = Missao.builder()
                .nome(req.nome())
                .descricao(req.descricao())
                .dataLancamento(req.dataLancamento())
                .status(req.status())
                .senhaMissao(passwordEncoder.encode(req.senhaMissao()))
                .operadorDono(operadorLogado)
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
    public MissaoResponse entrar(Long id, EntrarMissaoRequest req, Operador operadorLogado) {
        Missao missao = missaoRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Missão não encontrada com id: " + id));

        if (operadorMissaoRepository.existsByMissaoIdAndOperadorId(id, operadorLogado.getId())) {
            throw new OperadorJaMembroException("Operador já é membro desta missão");
        }

        if (!passwordEncoder.matches(req.senha(), missao.getSenhaMissao())) {
            throw new SenhaMissaoInvalidaException("Senha da missão incorreta");
        }

        OperadorMissao vinculo = OperadorMissao.builder()
                .operador(operadorLogado)
                .missao(missao)
                .role(RoleMissao.MEMBRO)
                .dataEntrada(LocalDateTime.now())
                .build();

        operadorMissaoRepository.save(vinculo);

        log.info("Operador '{}' entrou na missão id={}", operadorLogado.getLogin(), id);
        return toResponse(missao, RoleMissao.MEMBRO);
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
        int totalMembros = missao.getMembros() != null ? missao.getMembros().size() : 0;

        return MissaoResponse.builder()
                .id(missao.getId())
                .nome(missao.getNome())
                .descricao(missao.getDescricao())
                .dataLancamento(missao.getDataLancamento())
                .status(missao.getStatus())
                .roleDoOperador(roleDoOperador.name())
                .totalMembros(totalMembros)
                .totalSatelites(0)
                .build();
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
