package br.com.fiap.satmonitor;

import br.com.fiap.satmonitor.alerta.dto.AlertaResponse;
import br.com.fiap.satmonitor.alerta.entity.Alerta;
import br.com.fiap.satmonitor.alerta.enums.StatusAlerta;
import br.com.fiap.satmonitor.alerta.repository.AlertaRepository;
import br.com.fiap.satmonitor.alerta.service.AlertaService;
import br.com.fiap.satmonitor.auth.entity.Operador;
import br.com.fiap.satmonitor.exception.AcessoNegadoException;
import br.com.fiap.satmonitor.exception.EntityNotFoundException;
import br.com.fiap.satmonitor.leitura.entity.LeituraSensor;
import br.com.fiap.satmonitor.leitura.enums.QualidadeLeitura;
import br.com.fiap.satmonitor.leitura.enums.StatusLeitura;
import br.com.fiap.satmonitor.missao.entity.Missao;
import br.com.fiap.satmonitor.missao.entity.OperadorMissao;
import br.com.fiap.satmonitor.missao.enums.RoleMissao;
import br.com.fiap.satmonitor.missao.enums.StatusMissao;
import br.com.fiap.satmonitor.missao.repository.OperadorMissaoRepository;
import br.com.fiap.satmonitor.satelite.entity.CoordenadasOrbitais;
import br.com.fiap.satmonitor.satelite.entity.Satelite;
import br.com.fiap.satmonitor.satelite.enums.StatusSatelite;
import br.com.fiap.satmonitor.satelite.enums.TipoOrbita;
import br.com.fiap.satmonitor.satelite.repository.SateliteRepository;
import br.com.fiap.satmonitor.sensor.entity.SensorTermico;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AlertaServiceTest {

    @Mock AlertaRepository alertaRepository;
    @Mock SateliteRepository sateliteRepository;
    @Mock OperadorMissaoRepository operadorMissaoRepository;

    @InjectMocks AlertaService service;

    Missao missao;
    Satelite satelite;
    SensorTermico sensor;
    LeituraSensor leitura;
    Alerta alerta;

    Operador supervisor;
    Operador membro;

    @BeforeEach
    void setUp() {
        missao = Missao.builder().id(1L).nome("M").dataLancamento(LocalDate.now())
                .status(StatusMissao.ATIVA).senhaMissao("h").build();

        CoordenadasOrbitais coords = CoordenadasOrbitais.builder()
                .altitudeKm(400.0).inclinacao(51.6).longitudeNodo(0.0).build();
        satelite = Satelite.builder().id(5L).nome("SAT-01")
                .dataLancamento(LocalDate.now()).coordenadas(coords)
                .tipoOrbita(TipoOrbita.LEO).statusSatelite(StatusSatelite.ATIVO)
                .missao(missao).build();

        sensor = new SensorTermico();
        sensor.setId(10L);
        sensor.setNome("Temp");
        sensor.setSatelite(satelite);

        leitura = LeituraSensor.builder()
                .id(100L).valor(250.0)
                .dataHoraLeitura(LocalDateTime.now())
                .status(StatusLeitura.NORMAL)
                .qualidade(QualidadeLeitura.BOA)
                .sensor(sensor)
                .build();

        alerta = Alerta.builder()
                .id(1L).leitura(leitura)
                .tipoAlerta("LIMITE_MAX").descricao("Acima do limite")
                .statusAlerta(StatusAlerta.ATIVO)
                .build();

        supervisor = Operador.builder().id(1L).login("sup@t").nome("S").senha("h").build();
        membro     = Operador.builder().id(2L).login("mem@t").nome("M").senha("h").build();
    }

    private void mockVinculo(Operador op, RoleMissao role) {
        OperadorMissao v = OperadorMissao.builder().operador(op).missao(missao).role(role).dataEntrada(LocalDateTime.now()).build();
        when(operadorMissaoRepository.findByMissaoIdAndOperadorId(missao.getId(), op.getId())).thenReturn(Optional.of(v));
    }

    // ── listar ─────────────────────────────────────────────────────────
    @Nested @DisplayName("listar")
    class Listar {

        @Test
        @DisplayName("sem filtro → findAll")
        void semFiltro() {
            when(alertaRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(alerta)));
            var page = service.listar(null, Pageable.unpaged());
            assertThat(page.getTotalElements()).isEqualTo(1);
            verify(alertaRepository).findAll(any(Pageable.class));
            verify(alertaRepository, never()).findByStatusAlerta(any(), any());
        }

        @Test
        @DisplayName("com filtro ATIVO → findByStatusAlerta")
        void comFiltroAtivo() {
            when(alertaRepository.findByStatusAlerta(eq(StatusAlerta.ATIVO), any()))
                    .thenReturn(new PageImpl<>(List.of(alerta)));
            var page = service.listar(StatusAlerta.ATIVO, Pageable.unpaged());
            assertThat(page.getTotalElements()).isEqualTo(1);
            verify(alertaRepository).findByStatusAlerta(eq(StatusAlerta.ATIVO), any());
            verify(alertaRepository, never()).findAll(any(Pageable.class));
        }

        @Test
        @DisplayName("filtro sem resultados → página vazia")
        void filtroSemResultados() {
            when(alertaRepository.findByStatusAlerta(eq(StatusAlerta.RESOLVIDO), any()))
                    .thenReturn(new PageImpl<>(List.of()));
            var page = service.listar(StatusAlerta.RESOLVIDO, Pageable.unpaged());
            assertThat(page).isEmpty();
        }
    }

    // ── buscarPorId ────────────────────────────────────────────────────
    @Nested @DisplayName("buscarPorId")
    class BuscarPorId {

        @Test
        @DisplayName("alerta encontrado → retorna response")
        void encontrado() {
            when(alertaRepository.findById(1L)).thenReturn(Optional.of(alerta));
            AlertaResponse resp = service.buscarPorId(1L);
            assertThat(resp.getId()).isEqualTo(1L);
            assertThat(resp.getStatusAlerta()).isEqualTo(StatusAlerta.ATIVO);
        }

        @Test
        @DisplayName("alerta inexistente → EntityNotFoundException")
        void naoEncontrado() {
            when(alertaRepository.findById(99L)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.buscarPorId(99L))
                    .isInstanceOf(EntityNotFoundException.class);
        }
    }

    // ── listarPorSatelite ──────────────────────────────────────────────
    @Nested @DisplayName("listarPorSatelite")
    class ListarPorSatelite {

        @Test
        @DisplayName("satélite existente → retorna alertas")
        void caminhoFeliz() {
            when(sateliteRepository.existsById(5L)).thenReturn(true);
            when(alertaRepository.findBySateliteId(eq(5L), any())).thenReturn(new PageImpl<>(List.of(alerta)));
            var page = service.listarPorSatelite(5L, Pageable.unpaged());
            assertThat(page.getTotalElements()).isEqualTo(1);
        }

        @Test
        @DisplayName("satélite inexistente → EntityNotFoundException")
        void sateliteInexistente() {
            when(sateliteRepository.existsById(99L)).thenReturn(false);
            assertThatThrownBy(() -> service.listarPorSatelite(99L, Pageable.unpaged()))
                    .isInstanceOf(EntityNotFoundException.class);
        }
    }

    // ── atualizarStatus ────────────────────────────────────────────────
    @Nested @DisplayName("atualizarStatus")
    class AtualizarStatus {

        @Test
        @DisplayName("SUPERVISOR atualiza para RECONHECIDO")
        void supervisorAtualiza() {
            when(alertaRepository.findById(1L)).thenReturn(Optional.of(alerta));
            mockVinculo(supervisor, RoleMissao.SUPERVISOR);

            AlertaResponse resp = service.atualizarStatus(1L, StatusAlerta.RECONHECIDO, supervisor);
            assertThat(resp.getStatusAlerta()).isEqualTo(StatusAlerta.RECONHECIDO);
            verify(alertaRepository).save(alerta);
        }

        @Test
        @DisplayName("DONO também pode atualizar")
        void donoAtualiza() {
            Operador donoOp = Operador.builder().id(99L).login("don@t").nome("D").senha("h").build();
            when(alertaRepository.findById(1L)).thenReturn(Optional.of(alerta));
            OperadorMissao v = OperadorMissao.builder().operador(donoOp).missao(missao).role(RoleMissao.DONO).dataEntrada(LocalDateTime.now()).build();
            when(operadorMissaoRepository.findByMissaoIdAndOperadorId(1L, donoOp.getId())).thenReturn(Optional.of(v));

            AlertaResponse resp = service.atualizarStatus(1L, StatusAlerta.RESOLVIDO, donoOp);
            assertThat(resp.getStatusAlerta()).isEqualTo(StatusAlerta.RESOLVIDO);
        }

        @Test
        @DisplayName("MEMBRO tentando atualizar → AcessoNegadoException")
        void membroNaoPode() {
            when(alertaRepository.findById(1L)).thenReturn(Optional.of(alerta));
            mockVinculo(membro, RoleMissao.MEMBRO);
            assertThatThrownBy(() -> service.atualizarStatus(1L, StatusAlerta.RECONHECIDO, membro))
                    .isInstanceOf(AcessoNegadoException.class);
        }

        @Test
        @DisplayName("não-membro → AcessoNegadoException")
        void naoMembro() {
            Operador forasteiro = Operador.builder().id(77L).login("f@t").nome("F").senha("h").build();
            when(alertaRepository.findById(1L)).thenReturn(Optional.of(alerta));
            when(operadorMissaoRepository.findByMissaoIdAndOperadorId(1L, forasteiro.getId())).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.atualizarStatus(1L, StatusAlerta.RECONHECIDO, forasteiro))
                    .isInstanceOf(AcessoNegadoException.class);
        }

        @Test
        @DisplayName("alerta inexistente → EntityNotFoundException")
        void alertaInexistente() {
            when(alertaRepository.findById(99L)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.atualizarStatus(99L, StatusAlerta.RECONHECIDO, supervisor))
                    .isInstanceOf(EntityNotFoundException.class);
        }
    }
}
