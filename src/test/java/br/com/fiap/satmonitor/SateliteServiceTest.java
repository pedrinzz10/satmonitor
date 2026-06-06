package br.com.fiap.satmonitor;

import br.com.fiap.satmonitor.auth.entity.Operador;
import br.com.fiap.satmonitor.exception.AcessoNegadoException;
import br.com.fiap.satmonitor.exception.EntityNotFoundException;
import br.com.fiap.satmonitor.missao.entity.Missao;
import br.com.fiap.satmonitor.missao.entity.OperadorMissao;
import br.com.fiap.satmonitor.missao.enums.RoleMissao;
import br.com.fiap.satmonitor.missao.enums.StatusMissao;
import br.com.fiap.satmonitor.missao.repository.MissaoRepository;
import br.com.fiap.satmonitor.missao.repository.OperadorMissaoRepository;
import br.com.fiap.satmonitor.satelite.dto.CoordenadasOrbitaisRequest;
import br.com.fiap.satmonitor.satelite.dto.SateliteRequest;
import br.com.fiap.satmonitor.satelite.dto.SateliteResponse;
import br.com.fiap.satmonitor.satelite.entity.CoordenadasOrbitais;
import br.com.fiap.satmonitor.satelite.entity.Satelite;
import br.com.fiap.satmonitor.satelite.enums.StatusSatelite;
import br.com.fiap.satmonitor.satelite.enums.TipoOrbita;
import br.com.fiap.satmonitor.satelite.repository.SateliteRepository;
import br.com.fiap.satmonitor.satelite.service.SateliteService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SateliteServiceTest {

    @Mock SateliteRepository sateliteRepository;
    @Mock MissaoRepository missaoRepository;
    @Mock OperadorMissaoRepository operadorMissaoRepository;

    @InjectMocks SateliteService service;

    Missao missao;
    Operador supervisor;
    Operador membro;
    Operador dono;
    Satelite satelite;
    CoordenadasOrbitaisRequest coordReq;
    SateliteRequest req;

    @BeforeEach
    void setUp() {
        missao = Missao.builder().id(1L).nome("Missao X").dataLancamento(LocalDate.now())
                .status(StatusMissao.ATIVA).senhaMissao("h").build();

        supervisor = Operador.builder().id(1L).login("sup@test").nome("Sup").senha("h").build();
        membro     = Operador.builder().id(2L).login("mem@test").nome("Mem").senha("h").build();
        dono       = Operador.builder().id(3L).login("don@test").nome("Don").senha("h").build();

        CoordenadasOrbitais coords = CoordenadasOrbitais.builder()
                .altitudeKm(400.0).inclinacao(51.6).longitudeNodo(0.0).build();
        satelite = Satelite.builder().id(10L).nome("SAT-01")
                .dataLancamento(LocalDate.of(2026,1,1))
                .coordenadas(coords).tipoOrbita(TipoOrbita.LEO)
                .statusSatelite(StatusSatelite.ATIVO).missao(missao).build();

        coordReq = new CoordenadasOrbitaisRequest(400.0, 51.6, 0.0);
        req = new SateliteRequest("SAT-01", LocalDate.of(2026,1,1), 1L, coordReq, TipoOrbita.LEO, StatusSatelite.ATIVO);
    }

    private void mockVinculo(Operador op, RoleMissao role) {
        OperadorMissao v = OperadorMissao.builder().operador(op).missao(missao).role(role).dataEntrada(LocalDateTime.now()).build();
        when(operadorMissaoRepository.findByMissaoIdAndOperadorId(missao.getId(), op.getId())).thenReturn(Optional.of(v));
    }

    // ── criar ──────────────────────────────────────────────────────────
    @Nested @DisplayName("criar")
    class Criar {

        @Test
        @DisplayName("SUPERVISOR cria satélite com sucesso")
        void caminhoFeliz() {
            when(missaoRepository.findById(1L)).thenReturn(Optional.of(missao));
            mockVinculo(supervisor, RoleMissao.SUPERVISOR);
            when(sateliteRepository.existsByNomeAndMissaoId("SAT-01", 1L)).thenReturn(false);
            when(sateliteRepository.save(any())).thenAnswer(inv -> {
                Satelite s = inv.getArgument(0); s.setId(99L); return s;
            });

            SateliteResponse resp = service.criar(req, supervisor);
            assertThat(resp.getNome()).isEqualTo("SAT-01");
            assertThat(resp.getMissaoId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("missão inexistente → EntityNotFoundException")
        void missaoInexistente() {
            when(missaoRepository.findById(99L)).thenReturn(Optional.empty());
            var reqOutra = new SateliteRequest("SAT", LocalDate.now(), 99L, coordReq, TipoOrbita.LEO, StatusSatelite.ATIVO);
            assertThatThrownBy(() -> service.criar(reqOutra, supervisor))
                    .isInstanceOf(EntityNotFoundException.class);
        }

        @Test
        @DisplayName("operador sem vínculo → AcessoNegadoException")
        void semRole() {
            when(missaoRepository.findById(1L)).thenReturn(Optional.of(missao));
            when(operadorMissaoRepository.findByMissaoIdAndOperadorId(1L, membro.getId())).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.criar(req, membro))
                    .isInstanceOf(AcessoNegadoException.class);
        }

        @Test
        @DisplayName("MEMBRO tentando criar → AcessoNegadoException")
        void comRoleMembro() {
            when(missaoRepository.findById(1L)).thenReturn(Optional.of(missao));
            mockVinculo(membro, RoleMissao.MEMBRO);
            assertThatThrownBy(() -> service.criar(req, membro))
                    .isInstanceOf(AcessoNegadoException.class);
        }

        @Test
        @DisplayName("nome duplicado na missão → IllegalArgumentException")
        void nomeDuplicado() {
            when(missaoRepository.findById(1L)).thenReturn(Optional.of(missao));
            mockVinculo(supervisor, RoleMissao.SUPERVISOR);
            when(sateliteRepository.existsByNomeAndMissaoId("SAT-01", 1L)).thenReturn(true);
            assertThatThrownBy(() -> service.criar(req, supervisor))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("SAT-01");
        }
    }

    // ── listarPorMissao ────────────────────────────────────────────────
    @Nested @DisplayName("listarPorMissao")
    class ListarPorMissao {

        @Test
        @DisplayName("missão existente → retorna página")
        void caminhoFeliz() {
            when(missaoRepository.existsById(1L)).thenReturn(true);
            when(sateliteRepository.findByMissaoId(eq(1L), any())).thenReturn(new PageImpl<>(List.of(satelite)));
            var page = service.listarPorMissao(1L, Pageable.unpaged());
            assertThat(page.getTotalElements()).isEqualTo(1);
        }

        @Test
        @DisplayName("missão inexistente → EntityNotFoundException")
        void missaoInexistente() {
            when(missaoRepository.existsById(99L)).thenReturn(false);
            assertThatThrownBy(() -> service.listarPorMissao(99L, Pageable.unpaged()))
                    .isInstanceOf(EntityNotFoundException.class);
        }
    }

    // ── buscarPorId ───────────────────────────────────────────────────
    @Nested @DisplayName("buscarPorId")
    class BuscarPorId {

        @Test
        @DisplayName("satélite encontrado")
        void encontrado() {
            when(sateliteRepository.findById(10L)).thenReturn(Optional.of(satelite));
            SateliteResponse resp = service.buscarPorId(10L);
            assertThat(resp.getId()).isEqualTo(10L);
        }

        @Test
        @DisplayName("satélite inexistente → EntityNotFoundException")
        void naoEncontrado() {
            when(sateliteRepository.findById(99L)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.buscarPorId(99L))
                    .isInstanceOf(EntityNotFoundException.class);
        }
    }

    // ── atualizar ─────────────────────────────────────────────────────
    @Nested @DisplayName("atualizar")
    class Atualizar {

        @Test
        @DisplayName("SUPERVISOR atualiza com sucesso")
        void comSupervisor() {
            when(sateliteRepository.findById(10L)).thenReturn(Optional.of(satelite));
            when(missaoRepository.existsById(1L)).thenReturn(true);
            mockVinculo(supervisor, RoleMissao.SUPERVISOR);
            when(sateliteRepository.existsByNomeAndMissaoIdAndIdNot("SAT-01", 1L, 10L)).thenReturn(false);

            SateliteResponse resp = service.atualizar(10L, req, supervisor);
            assertThat(resp.getNome()).isEqualTo("SAT-01");
        }

        @Test
        @DisplayName("nome duplicado ao atualizar → IllegalArgumentException")
        void nomeDuplicadoAoAtualizar() {
            when(sateliteRepository.findById(10L)).thenReturn(Optional.of(satelite));
            when(missaoRepository.existsById(1L)).thenReturn(true);
            mockVinculo(supervisor, RoleMissao.SUPERVISOR);
            when(sateliteRepository.existsByNomeAndMissaoIdAndIdNot("SAT-01", 1L, 10L)).thenReturn(true);
            assertThatThrownBy(() -> service.atualizar(10L, req, supervisor))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("MEMBRO tentando atualizar → AcessoNegadoException")
        void comMembro() {
            when(sateliteRepository.findById(10L)).thenReturn(Optional.of(satelite));
            when(missaoRepository.existsById(1L)).thenReturn(true);
            mockVinculo(membro, RoleMissao.MEMBRO);
            assertThatThrownBy(() -> service.atualizar(10L, req, membro))
                    .isInstanceOf(AcessoNegadoException.class);
        }

        @Test
        @DisplayName("satélite inexistente → EntityNotFoundException")
        void sateliteInexistente() {
            when(sateliteRepository.findById(99L)).thenReturn(Optional.empty());
            var reqOutro = new SateliteRequest("SAT", LocalDate.now(), 1L, coordReq, TipoOrbita.LEO, StatusSatelite.ATIVO);
            assertThatThrownBy(() -> service.atualizar(99L, reqOutro, supervisor))
                    .isInstanceOf(EntityNotFoundException.class);
        }

        @Test
        @DisplayName("missão do request inexistente → EntityNotFoundException")
        void missaoDoRequestInexistente() {
            when(sateliteRepository.findById(10L)).thenReturn(Optional.of(satelite));
            when(missaoRepository.existsById(99L)).thenReturn(false);
            var reqOutraMissao = new SateliteRequest("SAT-01", LocalDate.now(), 99L, coordReq, TipoOrbita.LEO, StatusSatelite.ATIVO);
            assertThatThrownBy(() -> service.atualizar(10L, reqOutraMissao, supervisor))
                    .isInstanceOf(EntityNotFoundException.class);
        }
    }

    // ── deletar ───────────────────────────────────────────────────────
    @Nested @DisplayName("deletar")
    class Deletar {

        @Test
        @DisplayName("DONO deleta com sucesso")
        void comDono() {
            when(sateliteRepository.findById(10L)).thenReturn(Optional.of(satelite));
            mockVinculo(dono, RoleMissao.DONO);
            service.deletar(10L, dono);
            verify(sateliteRepository).delete(satelite);
        }

        @Test
        @DisplayName("SUPERVISOR tentando deletar → AcessoNegadoException")
        void comSupervisor() {
            when(sateliteRepository.findById(10L)).thenReturn(Optional.of(satelite));
            mockVinculo(supervisor, RoleMissao.SUPERVISOR);
            assertThatThrownBy(() -> service.deletar(10L, supervisor))
                    .isInstanceOf(AcessoNegadoException.class);
        }

        @Test
        @DisplayName("satélite inexistente → EntityNotFoundException")
        void sateliteInexistente() {
            when(sateliteRepository.findById(99L)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.deletar(99L, dono))
                    .isInstanceOf(EntityNotFoundException.class);
        }
    }
}
