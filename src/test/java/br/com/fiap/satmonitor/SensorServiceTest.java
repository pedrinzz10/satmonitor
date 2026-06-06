package br.com.fiap.satmonitor;

import br.com.fiap.satmonitor.auth.entity.Operador;
import br.com.fiap.satmonitor.exception.AcessoNegadoException;
import br.com.fiap.satmonitor.exception.EntityNotFoundException;
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
import br.com.fiap.satmonitor.sensor.dto.SensorRequest;
import br.com.fiap.satmonitor.sensor.dto.SensorResponse;
import br.com.fiap.satmonitor.sensor.entity.*;
import br.com.fiap.satmonitor.sensor.enums.TipoSensor;
import br.com.fiap.satmonitor.sensor.repository.SensorRepository;
import br.com.fiap.satmonitor.sensor.service.SensorService;
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
class SensorServiceTest {

    @Mock SensorRepository sensorRepository;
    @Mock SateliteRepository sateliteRepository;
    @Mock OperadorMissaoRepository operadorMissaoRepository;

    @InjectMocks SensorService service;

    Missao missao;
    Satelite satelite;
    Operador supervisor;
    Operador membro;
    Operador dono;

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

        supervisor = Operador.builder().id(1L).login("sup@t").nome("S").senha("h").build();
        membro     = Operador.builder().id(2L).login("mem@t").nome("M").senha("h").build();
        dono       = Operador.builder().id(3L).login("don@t").nome("D").senha("h").build();
    }

    private void mockVinculo(Operador op, RoleMissao role) {
        OperadorMissao v = OperadorMissao.builder().operador(op).missao(missao).role(role).dataEntrada(LocalDateTime.now()).build();
        when(operadorMissaoRepository.findByMissaoIdAndOperadorId(missao.getId(), op.getId())).thenReturn(Optional.of(v));
    }

    private SensorRequest termico(String nome) {
        return new SensorRequest(nome, "°C", -50.0, 200.0, 5.0, satelite.getId(),
                TipoSensor.TERMICO, "CELSIUS", null, null, null);
    }

    // ── criar ─────────────────────────────────────────────────────────
    @Nested @DisplayName("criar")
    class Criar {

        @Test
        @DisplayName("sensor TERMICO criado com sucesso")
        void tipoTermico() {
            when(sateliteRepository.findById(5L)).thenReturn(Optional.of(satelite));
            mockVinculo(supervisor, RoleMissao.SUPERVISOR);
            when(sensorRepository.existsByNomeAndSateliteId("Temp-01", 5L)).thenReturn(false);
            when(sensorRepository.save(any())).thenAnswer(inv -> {
                Sensor s = inv.getArgument(0); s.setId(20L); return s;
            });

            SensorResponse resp = service.criar(termico("Temp-01"), supervisor);
            assertThat(resp.getTipo()).isEqualTo(TipoSensor.TERMICO);
            assertThat(resp.getNome()).isEqualTo("Temp-01");
        }

        @Test
        @DisplayName("sensor PRESSAO criado com sucesso")
        void tipoPressao() {
            SensorRequest req = new SensorRequest("Press-01", "Pa", 0.0, 1000.0, 10.0, 5L,
                    TipoSensor.PRESSAO, null, "ABSOLUTA", null, null);
            when(sateliteRepository.findById(5L)).thenReturn(Optional.of(satelite));
            mockVinculo(supervisor, RoleMissao.SUPERVISOR);
            when(sensorRepository.existsByNomeAndSateliteId("Press-01", 5L)).thenReturn(false);
            when(sensorRepository.save(any())).thenAnswer(inv -> { Sensor s = inv.getArgument(0); s.setId(21L); return s; });

            SensorResponse resp = service.criar(req, supervisor);
            assertThat(resp.getTipo()).isEqualTo(TipoSensor.PRESSAO);
        }

        @Test
        @DisplayName("sensor RADIACAO criado com sucesso")
        void tipoRadiacao() {
            SensorRequest req = new SensorRequest("Rad-01", "Gy", 0.0, 100.0, 5.0, 5L,
                    TipoSensor.RADIACAO, null, null, "IONIZANTE", null);
            when(sateliteRepository.findById(5L)).thenReturn(Optional.of(satelite));
            mockVinculo(supervisor, RoleMissao.SUPERVISOR);
            when(sensorRepository.existsByNomeAndSateliteId("Rad-01", 5L)).thenReturn(false);
            when(sensorRepository.save(any())).thenAnswer(inv -> { Sensor s = inv.getArgument(0); s.setId(22L); return s; });

            SensorResponse resp = service.criar(req, supervisor);
            assertThat(resp.getTipo()).isEqualTo(TipoSensor.RADIACAO);
        }

        @Test
        @DisplayName("sensor MAGNETOMETRO criado com sucesso")
        void tipoMagnetometro() {
            SensorRequest req = new SensorRequest("Mag-01", "T", -10.0, 10.0, 1.0, 5L,
                    TipoSensor.MAGNETOMETRO, null, null, null, "XYZ");
            when(sateliteRepository.findById(5L)).thenReturn(Optional.of(satelite));
            mockVinculo(supervisor, RoleMissao.SUPERVISOR);
            when(sensorRepository.existsByNomeAndSateliteId("Mag-01", 5L)).thenReturn(false);
            when(sensorRepository.save(any())).thenAnswer(inv -> { Sensor s = inv.getArgument(0); s.setId(23L); return s; });

            SensorResponse resp = service.criar(req, supervisor);
            assertThat(resp.getTipo()).isEqualTo(TipoSensor.MAGNETOMETRO);
        }

        @Test
        @DisplayName("satélite inexistente → EntityNotFoundException")
        void sateliteInexistente() {
            when(sateliteRepository.findById(99L)).thenReturn(Optional.empty());
            var reqInvalido = new SensorRequest("T", "°C", 0.0, 100.0, 5.0, 99L,
                    TipoSensor.TERMICO, "CELSIUS", null, null, null);
            assertThatThrownBy(() -> service.criar(reqInvalido, supervisor))
                    .isInstanceOf(EntityNotFoundException.class);
        }

        @Test
        @DisplayName("MEMBRO tentando criar → AcessoNegadoException")
        void comMembro() {
            when(sateliteRepository.findById(5L)).thenReturn(Optional.of(satelite));
            mockVinculo(membro, RoleMissao.MEMBRO);
            assertThatThrownBy(() -> service.criar(termico("T"), membro))
                    .isInstanceOf(AcessoNegadoException.class);
        }

        @Test
        @DisplayName("limiteMin >= limiteMax → IllegalArgumentException")
        void limiteInvalido() {
            when(sateliteRepository.findById(5L)).thenReturn(Optional.of(satelite));
            mockVinculo(supervisor, RoleMissao.SUPERVISOR);
            var req = new SensorRequest("T", "°C", 100.0, 50.0, 5.0, 5L,
                    TipoSensor.TERMICO, "CELSIUS", null, null, null);
            assertThatThrownBy(() -> service.criar(req, supervisor))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("limiteMin");
        }

        @Test
        @DisplayName("nome duplicado no satélite → IllegalArgumentException")
        void nomeDuplicado() {
            when(sateliteRepository.findById(5L)).thenReturn(Optional.of(satelite));
            mockVinculo(supervisor, RoleMissao.SUPERVISOR);
            when(sensorRepository.existsByNomeAndSateliteId("Temp-01", 5L)).thenReturn(true);
            assertThatThrownBy(() -> service.criar(termico("Temp-01"), supervisor))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Temp-01");
        }

        @Test
        @DisplayName("TERMICO sem unidadeEscala → IllegalArgumentException")
        void termincoSemUnidadeEscala() {
            var req = new SensorRequest("T", "°C", 0.0, 100.0, 5.0, 5L,
                    TipoSensor.TERMICO, null, null, null, null);
            when(sateliteRepository.findById(5L)).thenReturn(Optional.of(satelite));
            mockVinculo(supervisor, RoleMissao.SUPERVISOR);
            when(sensorRepository.existsByNomeAndSateliteId("T", 5L)).thenReturn(false);
            assertThatThrownBy(() -> service.criar(req, supervisor))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("unidadeEscala");
        }

        @Test
        @DisplayName("PRESSAO sem tipoPressao → IllegalArgumentException")
        void pressaoSemTipo() {
            var req = new SensorRequest("P", "Pa", 0.0, 100.0, 5.0, 5L,
                    TipoSensor.PRESSAO, null, null, null, null);
            when(sateliteRepository.findById(5L)).thenReturn(Optional.of(satelite));
            mockVinculo(supervisor, RoleMissao.SUPERVISOR);
            when(sensorRepository.existsByNomeAndSateliteId("P", 5L)).thenReturn(false);
            assertThatThrownBy(() -> service.criar(req, supervisor))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("tipoPressao");
        }

        @Test
        @DisplayName("RADIACAO sem tipoRadiacao → IllegalArgumentException")
        void radiacaoSemTipo() {
            var req = new SensorRequest("R", "Gy", 0.0, 100.0, 5.0, 5L,
                    TipoSensor.RADIACAO, null, null, null, null);
            when(sateliteRepository.findById(5L)).thenReturn(Optional.of(satelite));
            mockVinculo(supervisor, RoleMissao.SUPERVISOR);
            when(sensorRepository.existsByNomeAndSateliteId("R", 5L)).thenReturn(false);
            assertThatThrownBy(() -> service.criar(req, supervisor))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("tipoRadiacao");
        }

        @Test
        @DisplayName("MAGNETOMETRO sem eixosMedicao → IllegalArgumentException")
        void magnetometroSemEixos() {
            var req = new SensorRequest("Mag", "T", 0.0, 10.0, 1.0, 5L,
                    TipoSensor.MAGNETOMETRO, null, null, null, null);
            when(sateliteRepository.findById(5L)).thenReturn(Optional.of(satelite));
            mockVinculo(supervisor, RoleMissao.SUPERVISOR);
            when(sensorRepository.existsByNomeAndSateliteId("Mag", 5L)).thenReturn(false);
            assertThatThrownBy(() -> service.criar(req, supervisor))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("eixosMedicao");
        }
    }

    // ── listarPorSatelite ──────────────────────────────────────────────
    @Nested @DisplayName("listarPorSatelite")
    class ListarPorSatelite {

        @Test
        @DisplayName("satélite existente → retorna página")
        void caminhoFeliz() {
            when(sateliteRepository.existsById(5L)).thenReturn(true);
            when(sensorRepository.findBySateliteId(eq(5L), any())).thenReturn(new PageImpl<>(List.of()));
            var page = service.listarPorSatelite(5L, Pageable.unpaged());
            assertThat(page).isEmpty();
        }

        @Test
        @DisplayName("satélite inexistente → EntityNotFoundException")
        void sateliteInexistente() {
            when(sateliteRepository.existsById(99L)).thenReturn(false);
            assertThatThrownBy(() -> service.listarPorSatelite(99L, Pageable.unpaged()))
                    .isInstanceOf(EntityNotFoundException.class);
        }
    }

    // ── buscarPorId ────────────────────────────────────────────────────
    @Nested @DisplayName("buscarPorId")
    class BuscarPorId {

        @Test
        @DisplayName("sensor encontrado")
        void encontrado() {
            SensorTermico st = new SensorTermico();
            st.setId(20L); st.setNome("Temp"); st.setSatelite(satelite);
            when(sensorRepository.findById(20L)).thenReturn(Optional.of(st));
            assertThat(service.buscarPorId(20L).getId()).isEqualTo(20L);
        }

        @Test
        @DisplayName("sensor inexistente → EntityNotFoundException")
        void naoEncontrado() {
            when(sensorRepository.findById(99L)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.buscarPorId(99L))
                    .isInstanceOf(EntityNotFoundException.class);
        }
    }

    // ── atualizar ──────────────────────────────────────────────────────
    @Nested @DisplayName("atualizar")
    class Atualizar {

        SensorTermico sensorExistente;

        @BeforeEach
        void setupSensor() {
            sensorExistente = new SensorTermico();
            sensorExistente.setId(20L);
            sensorExistente.setNome("Temp-01");
            sensorExistente.setUnidade("°C");
            sensorExistente.setLimiteMin(-50.0);
            sensorExistente.setLimiteMax(200.0);
            sensorExistente.setMargemAlerta(5.0);
            sensorExistente.setSatelite(satelite);
        }

        @Test
        @DisplayName("SUPERVISOR atualiza com sucesso")
        void comSupervisor() {
            when(sensorRepository.findById(20L)).thenReturn(Optional.of(sensorExistente));
            mockVinculo(supervisor, RoleMissao.SUPERVISOR);
            when(sensorRepository.existsByNomeAndSateliteIdAndIdNot("Temp-02", 5L, 20L)).thenReturn(false);

            var req = new SensorRequest("Temp-02", "°C", -50.0, 200.0, 5.0, 5L,
                    TipoSensor.TERMICO, "CELSIUS", null, null, null);
            SensorResponse resp = service.atualizar(20L, req, supervisor);
            assertThat(resp.getNome()).isEqualTo("Temp-02");
        }

        @Test
        @DisplayName("limiteMin >= limiteMax → IllegalArgumentException")
        void limiteInvalido() {
            when(sensorRepository.findById(20L)).thenReturn(Optional.of(sensorExistente));
            mockVinculo(supervisor, RoleMissao.SUPERVISOR);
            var req = new SensorRequest("T", "°C", 200.0, 50.0, 5.0, 5L,
                    TipoSensor.TERMICO, "CELSIUS", null, null, null);
            assertThatThrownBy(() -> service.atualizar(20L, req, supervisor))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("nome duplicado ao atualizar → IllegalArgumentException")
        void nomeDuplicado() {
            when(sensorRepository.findById(20L)).thenReturn(Optional.of(sensorExistente));
            mockVinculo(supervisor, RoleMissao.SUPERVISOR);
            when(sensorRepository.existsByNomeAndSateliteIdAndIdNot("OutroNome", 5L, 20L)).thenReturn(true);
            var req = new SensorRequest("OutroNome", "°C", -50.0, 200.0, 5.0, 5L,
                    TipoSensor.TERMICO, "CELSIUS", null, null, null);
            assertThatThrownBy(() -> service.atualizar(20L, req, supervisor))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("MEMBRO tentando atualizar → AcessoNegadoException")
        void comMembro() {
            when(sensorRepository.findById(20L)).thenReturn(Optional.of(sensorExistente));
            mockVinculo(membro, RoleMissao.MEMBRO);
            var req = termico("T");
            assertThatThrownBy(() -> service.atualizar(20L, req, membro))
                    .isInstanceOf(AcessoNegadoException.class);
        }

        @Test
        @DisplayName("sensor inexistente → EntityNotFoundException")
        void sensorInexistente() {
            when(sensorRepository.findById(99L)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.atualizar(99L, termico("T"), supervisor))
                    .isInstanceOf(EntityNotFoundException.class);
        }
    }

    // ── deletar ────────────────────────────────────────────────────────
    @Nested @DisplayName("deletar")
    class Deletar {

        SensorTermico sensorExistente;

        @BeforeEach
        void setupSensor() {
            sensorExistente = new SensorTermico();
            sensorExistente.setId(20L);
            sensorExistente.setNome("Temp-01");
            sensorExistente.setSatelite(satelite);
        }

        @Test
        @DisplayName("DONO deleta com sucesso")
        void comDono() {
            when(sensorRepository.findById(20L)).thenReturn(Optional.of(sensorExistente));
            mockVinculo(dono, RoleMissao.DONO);
            service.deletar(20L, dono);
            verify(sensorRepository).delete(sensorExistente);
        }

        @Test
        @DisplayName("SUPERVISOR tentando deletar → AcessoNegadoException")
        void comSupervisor() {
            when(sensorRepository.findById(20L)).thenReturn(Optional.of(sensorExistente));
            mockVinculo(supervisor, RoleMissao.SUPERVISOR);
            assertThatThrownBy(() -> service.deletar(20L, supervisor))
                    .isInstanceOf(AcessoNegadoException.class);
        }

        @Test
        @DisplayName("sensor inexistente → EntityNotFoundException")
        void sensorInexistente() {
            when(sensorRepository.findById(99L)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.deletar(99L, dono))
                    .isInstanceOf(EntityNotFoundException.class);
        }
    }
}
