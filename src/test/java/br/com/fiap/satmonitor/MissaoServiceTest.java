package br.com.fiap.satmonitor;

import br.com.fiap.satmonitor.agencia.entity.Agencia;
import br.com.fiap.satmonitor.agencia.repository.AgenciaRepository;
import br.com.fiap.satmonitor.auth.entity.Operador;
import br.com.fiap.satmonitor.exception.AcessoNegadoException;
import br.com.fiap.satmonitor.exception.DonoUnicoException;
import br.com.fiap.satmonitor.exception.EntityNotFoundException;
import br.com.fiap.satmonitor.exception.OperadorJaMembroException;
import br.com.fiap.satmonitor.exception.SenhaMissaoInvalidaException;
import br.com.fiap.satmonitor.missao.dto.EntrarMissaoRequest;
import br.com.fiap.satmonitor.missao.dto.MissaoRequest;
import br.com.fiap.satmonitor.missao.dto.MissaoResponse;
import br.com.fiap.satmonitor.missao.dto.MissaoUpdateRequest;
import br.com.fiap.satmonitor.missao.dto.SolicitacaoResponse;
import br.com.fiap.satmonitor.missao.entity.Missao;
import br.com.fiap.satmonitor.missao.entity.OperadorMissao;
import br.com.fiap.satmonitor.missao.entity.SolicitacaoEntrada;
import br.com.fiap.satmonitor.missao.enums.RoleMissao;
import br.com.fiap.satmonitor.missao.enums.StatusMissao;
import br.com.fiap.satmonitor.missao.enums.StatusSolicitacao;
import br.com.fiap.satmonitor.missao.repository.MissaoRepository;
import br.com.fiap.satmonitor.missao.repository.OperadorMissaoRepository;
import br.com.fiap.satmonitor.missao.repository.SolicitacaoEntradaRepository;
import br.com.fiap.satmonitor.missao.service.MissaoService;
import br.com.fiap.satmonitor.satelite.repository.SateliteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MissaoServiceTest {

    @Mock MissaoRepository missaoRepository;
    @Mock OperadorMissaoRepository operadorMissaoRepository;
    @Mock SolicitacaoEntradaRepository solicitacaoRepository;
    @Mock SateliteRepository sateliteRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock AgenciaRepository agenciaRepository;

    @InjectMocks MissaoService service;

    Agencia agenciaNasa;
    Agencia agenciaEsa;
    Operador dono;
    Operador membro;
    Operador supervisor;
    Operador forasteiro;
    Missao missao;
    OperadorMissao vinculoDono;
    OperadorMissao vinculoMembro;
    OperadorMissao vinculoSupervisor;

    @BeforeEach
    void setUp() {
        agenciaNasa = Agencia.builder().id(1L).nome("NASA").siglaPais("US").build();
        agenciaEsa  = Agencia.builder().id(2L).nome("ESA").siglaPais("EU").build();

        dono       = Operador.builder().id(1L).login("dono@sat.dev").nome("Dono").senha("hash").agencia(agenciaNasa).build();
        membro     = Operador.builder().id(2L).login("membro@sat.dev").nome("Membro").senha("hash").agencia(agenciaNasa).build();
        supervisor = Operador.builder().id(3L).login("sup@sat.dev").nome("Supervisor").senha("hash").agencia(agenciaNasa).build();
        forasteiro = Operador.builder().id(4L).login("forasteiro@sat.dev").nome("Forasteiro").senha("hash").agencia(agenciaEsa).build();

        missao = Missao.builder()
                .id(1L).nome("Missao Alpha").descricao("desc")
                .dataLancamento(LocalDate.of(2026, 1, 1))
                .status(StatusMissao.ATIVA)
                .senhaMissao("hashed123")
                .agencia(agenciaNasa)
                .permitirCowork(false)
                .operadorDono(dono)
                .build();

        vinculoDono       = OperadorMissao.builder().operador(dono).missao(missao).role(RoleMissao.DONO).dataEntrada(LocalDateTime.now()).build();
        vinculoMembro     = OperadorMissao.builder().operador(membro).missao(missao).role(RoleMissao.MEMBRO).dataEntrada(LocalDateTime.now()).build();
        vinculoSupervisor = OperadorMissao.builder().operador(supervisor).missao(missao).role(RoleMissao.SUPERVISOR).dataEntrada(LocalDateTime.now()).build();
    }

    // ── helpers ─────────────────────────────────────────────────────
    private void mockContagens() {
        when(operadorMissaoRepository.countByMissaoId(missao.getId())).thenReturn(3L);
        when(sateliteRepository.countByMissaoId(missao.getId())).thenReturn(0L);
    }

    private void mockVinculo(Operador op, RoleMissao role) {
        OperadorMissao v = OperadorMissao.builder().operador(op).missao(missao).role(role).dataEntrada(LocalDateTime.now()).build();
        when(operadorMissaoRepository.findByMissaoIdAndOperadorId(missao.getId(), op.getId())).thenReturn(Optional.of(v));
    }

    // ── criar ────────────────────────────────────────────────────────
    @Nested @DisplayName("criar")
    class Criar {

        @Test
        @DisplayName("deve retornar response com roleDoOperador=DONO")
        void caminhoFeliz() {
            var req = new MissaoRequest("Missao Alpha", "desc", LocalDate.of(2026,1,1),
                    StatusMissao.PLANEJADA, "senha123", agenciaNasa.getId(), "obj", null, false);

            when(agenciaRepository.findById(agenciaNasa.getId())).thenReturn(Optional.of(agenciaNasa));
            when(passwordEncoder.encode("senha123")).thenReturn("hashed");
            when(missaoRepository.save(any())).thenAnswer(inv -> { Missao m = inv.getArgument(0); m.setId(99L); return m; });
            when(operadorMissaoRepository.countByMissaoId(99L)).thenReturn(1L);
            when(sateliteRepository.countByMissaoId(99L)).thenReturn(0L);

            MissaoResponse resp = service.criar(req, dono);

            assertThat(resp.getRoleDoOperador()).isEqualTo("DONO");
            assertThat(resp.getNome()).isEqualTo("Missao Alpha");
            verify(operadorMissaoRepository).save(argThat(v -> v.getRole() == RoleMissao.DONO));
        }
    }

    // ── buscarPorId ──────────────────────────────────────────────────
    @Nested @DisplayName("buscarPorId")
    class BuscarPorId {

        @Test
        @DisplayName("membro válido recebe a missão com sua role")
        void membroValido() {
            when(missaoRepository.findById(1L)).thenReturn(Optional.of(missao));
            mockVinculo(membro, RoleMissao.MEMBRO);
            mockContagens();

            MissaoResponse resp = service.buscarPorId(1L, membro);
            assertThat(resp.getRoleDoOperador()).isEqualTo("MEMBRO");
        }

        @Test
        @DisplayName("missão inexistente → EntityNotFoundException")
        void missaoInexistente() {
            when(missaoRepository.findById(99L)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.buscarPorId(99L, membro))
                    .isInstanceOf(EntityNotFoundException.class);
        }

        @Test
        @DisplayName("não-membro → AcessoNegadoException")
        void naoMembro() {
            when(missaoRepository.findById(1L)).thenReturn(Optional.of(missao));
            when(operadorMissaoRepository.findByMissaoIdAndOperadorId(1L, forasteiro.getId())).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.buscarPorId(1L, forasteiro))
                    .isInstanceOf(AcessoNegadoException.class);
        }
    }

    // ── atualizar ────────────────────────────────────────────────────
    @Nested @DisplayName("atualizar")
    class Atualizar {

        MissaoUpdateRequest req = new MissaoUpdateRequest("Novo Nome", "d", LocalDate.of(2026,2,1),
                StatusMissao.ATIVA, 1L, null, null, false);

        @Test
        @DisplayName("DONO pode atualizar")
        void comRoleDono() {
            when(missaoRepository.findById(1L)).thenReturn(Optional.of(missao));
            mockVinculo(dono, RoleMissao.DONO);
            when(agenciaRepository.findById(1L)).thenReturn(Optional.of(agenciaNasa));
            mockContagens();

            MissaoResponse resp = service.atualizar(1L, req, dono);
            assertThat(resp.getNome()).isEqualTo("Novo Nome");
        }

        @Test
        @DisplayName("MEMBRO não pode atualizar → AcessoNegadoException")
        void comRoleMembro() {
            when(missaoRepository.findById(1L)).thenReturn(Optional.of(missao));
            mockVinculo(membro, RoleMissao.MEMBRO);
            assertThatThrownBy(() -> service.atualizar(1L, req, membro))
                    .isInstanceOf(AcessoNegadoException.class);
        }

        @Test
        @DisplayName("missão inexistente → EntityNotFoundException")
        void missaoInexistente() {
            when(missaoRepository.findById(99L)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.atualizar(99L, req, dono))
                    .isInstanceOf(EntityNotFoundException.class);
        }
    }

    // ── deletar ──────────────────────────────────────────────────────
    @Nested @DisplayName("deletar")
    class Deletar {

        @Test
        @DisplayName("DONO pode deletar")
        void comRoleDono() {
            when(missaoRepository.findById(1L)).thenReturn(Optional.of(missao));
            mockVinculo(dono, RoleMissao.DONO);
            service.deletar(1L, dono);
            verify(missaoRepository).delete(missao);
        }

        @Test
        @DisplayName("SUPERVISOR não pode deletar → AcessoNegadoException")
        void comRoleSupervisor() {
            when(missaoRepository.findById(1L)).thenReturn(Optional.of(missao));
            mockVinculo(supervisor, RoleMissao.SUPERVISOR);
            assertThatThrownBy(() -> service.deletar(1L, supervisor))
                    .isInstanceOf(AcessoNegadoException.class);
        }
    }

    // ── solicitarEntrada ─────────────────────────────────────────────
    @Nested @DisplayName("solicitarEntrada")
    class SolicitarEntrada {

        EntrarMissaoRequest req = new EntrarMissaoRequest("senha123");

        @Test
        @DisplayName("caminho feliz → solicitação PENDENTE criada")
        void caminhoFeliz() {
            when(missaoRepository.findById(1L)).thenReturn(Optional.of(missao));
            when(passwordEncoder.matches("senha123", "hashed123")).thenReturn(true);
            when(operadorMissaoRepository.existsByMissaoIdAndOperadorId(1L, membro.getId())).thenReturn(false);
            when(solicitacaoRepository.existsByMissaoIdAndOperadorIdAndStatus(1L, membro.getId(), StatusSolicitacao.PENDENTE)).thenReturn(false);
            when(solicitacaoRepository.save(any())).thenAnswer(inv -> {
                SolicitacaoEntrada s = inv.getArgument(0); s.getMissao(); return s;
            });

            SolicitacaoResponse resp = service.solicitarEntrada(1L, req, membro);
            assertThat(resp.status()).isEqualTo(StatusSolicitacao.PENDENTE);
        }

        @Test
        @DisplayName("senha incorreta → SenhaMissaoInvalidaException")
        void senhaErrada() {
            when(missaoRepository.findById(1L)).thenReturn(Optional.of(missao));
            when(passwordEncoder.matches("senha123", "hashed123")).thenReturn(false);
            assertThatThrownBy(() -> service.solicitarEntrada(1L, req, membro))
                    .isInstanceOf(SenhaMissaoInvalidaException.class);
        }

        @Test
        @DisplayName("operador sem agência → IllegalArgumentException")
        void semAgencia() {
            Operador semAgencia = Operador.builder().id(99L).login("x").nome("x").senha("x").agencia(null).build();
            when(missaoRepository.findById(1L)).thenReturn(Optional.of(missao));
            assertThatThrownBy(() -> service.solicitarEntrada(1L, req, semAgencia))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("agência incompatível sem cowork → AcessoNegadoException")
        void agenciaIncompativelSemCowork() {
            when(missaoRepository.findById(1L)).thenReturn(Optional.of(missao));
            assertThatThrownBy(() -> service.solicitarEntrada(1L, req, forasteiro))
                    .isInstanceOf(AcessoNegadoException.class);
        }

        @Test
        @DisplayName("agência incompatível com cowork habilitado → aceita solicitação")
        void agenciaIncompativelComCowork() {
            missao.setPermitirCowork(true);
            when(missaoRepository.findById(1L)).thenReturn(Optional.of(missao));
            when(passwordEncoder.matches("senha123", "hashed123")).thenReturn(true);
            when(operadorMissaoRepository.existsByMissaoIdAndOperadorId(1L, forasteiro.getId())).thenReturn(false);
            when(solicitacaoRepository.existsByMissaoIdAndOperadorIdAndStatus(1L, forasteiro.getId(), StatusSolicitacao.PENDENTE)).thenReturn(false);
            when(solicitacaoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            SolicitacaoResponse resp = service.solicitarEntrada(1L, req, forasteiro);
            assertThat(resp.status()).isEqualTo(StatusSolicitacao.PENDENTE);
        }

        @Test
        @DisplayName("operador já é membro ativo → OperadorJaMembroException")
        void jaMembroAtivo() {
            when(missaoRepository.findById(1L)).thenReturn(Optional.of(missao));
            when(passwordEncoder.matches("senha123", "hashed123")).thenReturn(true);
            when(operadorMissaoRepository.existsByMissaoIdAndOperadorId(1L, membro.getId())).thenReturn(true);
            assertThatThrownBy(() -> service.solicitarEntrada(1L, req, membro))
                    .isInstanceOf(OperadorJaMembroException.class);
        }

        @Test
        @DisplayName("já existe solicitação pendente → OperadorJaMembroException")
        void solicitacaoPendenteDuplicada() {
            when(missaoRepository.findById(1L)).thenReturn(Optional.of(missao));
            when(passwordEncoder.matches("senha123", "hashed123")).thenReturn(true);
            when(operadorMissaoRepository.existsByMissaoIdAndOperadorId(1L, membro.getId())).thenReturn(false);
            when(solicitacaoRepository.existsByMissaoIdAndOperadorIdAndStatus(1L, membro.getId(), StatusSolicitacao.PENDENTE)).thenReturn(true);
            assertThatThrownBy(() -> service.solicitarEntrada(1L, req, membro))
                    .isInstanceOf(OperadorJaMembroException.class);
        }
    }

    // ── listarSolicitacoes ───────────────────────────────────────────
    @Nested @DisplayName("listarSolicitacoes")
    class ListarSolicitacoes {

        @Test
        @DisplayName("SUPERVISOR pode listar")
        void comSupervisor() {
            mockVinculo(supervisor, RoleMissao.SUPERVISOR);
            when(solicitacaoRepository.findByMissaoIdAndStatus(eq(1L), eq(StatusSolicitacao.PENDENTE), any()))
                    .thenReturn(new PageImpl<>(List.of()));

            var page = service.listarSolicitacoes(1L, StatusSolicitacao.PENDENTE, Pageable.unpaged(), supervisor);
            assertThat(page).isEmpty();
        }

        @Test
        @DisplayName("MEMBRO não pode listar → AcessoNegadoException")
        void comMembro() {
            mockVinculo(membro, RoleMissao.MEMBRO);
            assertThatThrownBy(() -> service.listarSolicitacoes(1L, StatusSolicitacao.PENDENTE, Pageable.unpaged(), membro))
                    .isInstanceOf(AcessoNegadoException.class);
        }

        @Test
        @DisplayName("não-membro → EntityNotFoundException")
        void naoMembro() {
            when(operadorMissaoRepository.findByMissaoIdAndOperadorId(1L, forasteiro.getId())).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.listarSolicitacoes(1L, StatusSolicitacao.PENDENTE, Pageable.unpaged(), forasteiro))
                    .isInstanceOf(EntityNotFoundException.class);
        }
    }

    // ── responderSolicitacao ─────────────────────────────────────────
    @Nested @DisplayName("responderSolicitacao")
    class ResponderSolicitacao {

        SolicitacaoEntrada solicitacaoPendente;

        @BeforeEach
        void setUpSolicitacao() {
            solicitacaoPendente = SolicitacaoEntrada.builder()
                    .operador(membro).missao(missao).build();
            // id via reflection não necessário — filtramos por missaoId
        }

        @Test
        @DisplayName("DONO aprova → cria vínculo MEMBRO")
        void aprovarCaminhoFeliz() {
            mockVinculo(dono, RoleMissao.DONO);
            when(solicitacaoRepository.findById(10L))
                    .thenReturn(Optional.of(solicitacaoPendente));

            service.responderSolicitacao(1L, 10L, true, dono);

            verify(operadorMissaoRepository).save(argThat(v -> v.getRole() == RoleMissao.MEMBRO));
            assertThat(solicitacaoPendente.getStatus()).isEqualTo(StatusSolicitacao.APROVADO);
        }

        @Test
        @DisplayName("SUPERVISOR rejeita → marca REJEITADO, não cria vínculo")
        void rejeitarCaminhoFeliz() {
            mockVinculo(supervisor, RoleMissao.SUPERVISOR);
            when(solicitacaoRepository.findById(10L))
                    .thenReturn(Optional.of(solicitacaoPendente));

            service.responderSolicitacao(1L, 10L, false, supervisor);

            verify(operadorMissaoRepository, never()).save(any(OperadorMissao.class));
            assertThat(solicitacaoPendente.getStatus()).isEqualTo(StatusSolicitacao.REJEITADO);
        }

        @Test
        @DisplayName("solicitação já respondida → IllegalArgumentException (400)")
        void jaRespondida() {
            mockVinculo(dono, RoleMissao.DONO);
            solicitacaoPendente.setStatus(StatusSolicitacao.APROVADO);
            when(solicitacaoRepository.findById(10L)).thenReturn(Optional.of(solicitacaoPendente));

            assertThatThrownBy(() -> service.responderSolicitacao(1L, 10L, true, dono))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("MEMBRO tentando aprovar → AcessoNegadoException (403)")
        void membroTentaAprovar() {
            mockVinculo(membro, RoleMissao.MEMBRO);
            assertThatThrownBy(() -> service.responderSolicitacao(1L, 10L, true, membro))
                    .isInstanceOf(AcessoNegadoException.class);
        }

        @Test
        @DisplayName("solicitacaoId de outra missão → EntityNotFoundException (404)")
        void solicitacaoDeOutraMissao() {
            Missao outraMissao = Missao.builder().id(99L).nome("Outra").dataLancamento(LocalDate.now())
                    .status(StatusMissao.PLANEJADA).senhaMissao("x").build();
            SolicitacaoEntrada outra = SolicitacaoEntrada.builder().operador(membro).missao(outraMissao).build();

            mockVinculo(dono, RoleMissao.DONO);
            when(solicitacaoRepository.findById(55L)).thenReturn(Optional.of(outra));

            assertThatThrownBy(() -> service.responderSolicitacao(1L, 55L, true, dono))
                    .isInstanceOf(EntityNotFoundException.class);
        }
    }

    // ── sair ─────────────────────────────────────────────────────────
    @Nested @DisplayName("sair")
    class Sair {

        @Test
        @DisplayName("membro comum sai com sucesso")
        void membroComum() {
            when(operadorMissaoRepository.findByMissaoIdAndOperadorId(1L, membro.getId()))
                    .thenReturn(Optional.of(vinculoMembro));
            service.sair(1L, membro);
            verify(operadorMissaoRepository).delete(vinculoMembro);
        }

        @Test
        @DisplayName("DONO único tenta sair → DonoUnicoException")
        void donoUnicoNaoPodeSair() {
            when(operadorMissaoRepository.findByMissaoIdAndOperadorId(1L, dono.getId()))
                    .thenReturn(Optional.of(vinculoDono));
            when(operadorMissaoRepository.countByMissaoIdAndRole(1L, RoleMissao.DONO)).thenReturn(1L);
            assertThatThrownBy(() -> service.sair(1L, dono))
                    .isInstanceOf(DonoUnicoException.class);
        }

        @Test
        @DisplayName("DONO sai quando há outro DONO")
        void donoComOutroDono() {
            when(operadorMissaoRepository.findByMissaoIdAndOperadorId(1L, dono.getId()))
                    .thenReturn(Optional.of(vinculoDono));
            when(operadorMissaoRepository.countByMissaoIdAndRole(1L, RoleMissao.DONO)).thenReturn(2L);
            service.sair(1L, dono);
            verify(operadorMissaoRepository).delete(vinculoDono);
        }

        @Test
        @DisplayName("não-membro tenta sair → EntityNotFoundException")
        void naoMembro() {
            when(operadorMissaoRepository.findByMissaoIdAndOperadorId(1L, forasteiro.getId()))
                    .thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.sair(1L, forasteiro))
                    .isInstanceOf(EntityNotFoundException.class);
        }
    }

    // ── listarMembros ────────────────────────────────────────────────
    @Nested @DisplayName("listarMembros")
    class ListarMembros {

        @Test
        @DisplayName("membro pode listar")
        void membroPodeLista() {
            when(operadorMissaoRepository.existsByMissaoIdAndOperadorId(1L, membro.getId())).thenReturn(true);
            when(operadorMissaoRepository.findByMissaoId(1L)).thenReturn(List.of(vinculoDono, vinculoMembro));
            assertThat(service.listarMembros(1L, membro)).hasSize(2);
        }

        @Test
        @DisplayName("não-membro → AcessoNegadoException")
        void naoMembro() {
            when(operadorMissaoRepository.existsByMissaoIdAndOperadorId(1L, forasteiro.getId())).thenReturn(false);
            assertThatThrownBy(() -> service.listarMembros(1L, forasteiro))
                    .isInstanceOf(AcessoNegadoException.class);
        }
    }

    // ── removerMembro ────────────────────────────────────────────────
    @Nested @DisplayName("removerMembro")
    class RemoverMembro {

        @Test
        @DisplayName("DONO remove outro membro")
        void donoRemoveMembro() {
            mockVinculo(dono, RoleMissao.DONO);
            when(operadorMissaoRepository.findByMissaoIdAndOperadorId(1L, membro.getId()))
                    .thenReturn(Optional.of(vinculoMembro));
            service.removerMembro(1L, membro.getId(), dono);
            verify(operadorMissaoRepository).delete(vinculoMembro);
        }

        @Test
        @DisplayName("DONO tenta se remover → AcessoNegadoException")
        void donoRemoveSiMesmo() {
            mockVinculo(dono, RoleMissao.DONO);
            assertThatThrownBy(() -> service.removerMembro(1L, dono.getId(), dono))
                    .isInstanceOf(AcessoNegadoException.class);
        }

        @Test
        @DisplayName("SUPERVISOR tenta remover → AcessoNegadoException")
        void supervisorNaoPodeRemover() {
            mockVinculo(supervisor, RoleMissao.SUPERVISOR);
            assertThatThrownBy(() -> service.removerMembro(1L, membro.getId(), supervisor))
                    .isInstanceOf(AcessoNegadoException.class);
        }
    }

    // ── promoverMembro ───────────────────────────────────────────────
    @Nested @DisplayName("promoverMembro")
    class PromoverMembro {

        @Test
        @DisplayName("DONO promove membro para SUPERVISOR")
        void donoPromove() {
            mockVinculo(dono, RoleMissao.DONO);
            when(operadorMissaoRepository.findByMissaoIdAndOperadorId(1L, membro.getId()))
                    .thenReturn(Optional.of(vinculoMembro));
            var resp = service.promoverMembro(1L, membro.getId(), RoleMissao.SUPERVISOR, dono);
            assertThat(resp.getRole()).isEqualTo(RoleMissao.SUPERVISOR);
        }

        @Test
        @DisplayName("DONO tenta alterar a própria role → AcessoNegadoException")
        void donoAlteraPropriaRole() {
            mockVinculo(dono, RoleMissao.DONO);
            assertThatThrownBy(() -> service.promoverMembro(1L, dono.getId(), RoleMissao.MEMBRO, dono))
                    .isInstanceOf(AcessoNegadoException.class);
        }

        @Test
        @DisplayName("membro-alvo não encontrado → EntityNotFoundException")
        void membroNaoEncontrado() {
            mockVinculo(dono, RoleMissao.DONO);
            when(operadorMissaoRepository.findByMissaoIdAndOperadorId(1L, 99L)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.promoverMembro(1L, 99L, RoleMissao.SUPERVISOR, dono))
                    .isInstanceOf(EntityNotFoundException.class);
        }
    }
}
