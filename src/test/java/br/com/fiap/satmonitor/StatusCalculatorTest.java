package br.com.fiap.satmonitor;

import br.com.fiap.satmonitor.leitura.enums.StatusLeitura;
import br.com.fiap.satmonitor.leitura.service.StatusCalculator;
import br.com.fiap.satmonitor.sensor.entity.SensorTermico;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StatusCalculatorTest {

    // limiteMin=0, limiteMax=80, margemAlerta=10%
    // zonaAlertaMin=8.0, zonaAlertaMax=72.0

    private StatusCalculator calculator;
    private SensorTermico sensor;

    @BeforeEach
    void setUp() {
        calculator = new StatusCalculator();
        sensor = new SensorTermico();
        sensor.setLimiteMin(0.0);
        sensor.setLimiteMax(80.0);
        sensor.setMargemAlerta(10.0);
    }

    @Test
    @DisplayName("valor=40.0 → NORMAL (centro da faixa segura)")
    void valorNoCentro() {
        assertEquals(StatusLeitura.NORMAL, calculator.calcular(40.0, sensor));
    }

    @Test
    @DisplayName("valor=75.0 → ALERTA (acima de zonaAlertaMax=72, abaixo de limiteMax=80)")
    void valorNaZonaAlertaSuperior() {
        assertEquals(StatusLeitura.ALERTA, calculator.calcular(75.0, sensor));
    }

    @Test
    @DisplayName("valor=95.0 → CRITICO (acima de limiteMax=80)")
    void valorAcimaLimiteMax() {
        assertEquals(StatusLeitura.CRITICO, calculator.calcular(95.0, sensor));
    }

    @Test
    @DisplayName("valor=5.0 → ALERTA (abaixo de zonaAlertaMin=8, acima de limiteMin=0)")
    void valorNaZonaAlertaInferior() {
        assertEquals(StatusLeitura.ALERTA, calculator.calcular(5.0, sensor));
    }

    @Test
    @DisplayName("valor=-5.0 → CRITICO (abaixo de limiteMin=0)")
    void valorAbaixoLimiteMin() {
        assertEquals(StatusLeitura.CRITICO, calculator.calcular(-5.0, sensor));
    }

    @Test
    @DisplayName("valor=72.0 → NORMAL (exatamente no limite da zona de alerta — dentro ainda)")
    void valorExatamenteNaZonaAlertaMax() {
        assertEquals(StatusLeitura.NORMAL, calculator.calcular(72.0, sensor));
    }

    @Test
    @DisplayName("valor=80.0 → ALERTA (exatamente no limiteMax — zona de alerta, não crítico)")
    void valorExatamenteNoLimiteMax() {
        assertEquals(StatusLeitura.ALERTA, calculator.calcular(80.0, sensor));
    }

    @Test
    @DisplayName("valor=0.0 → ALERTA (exatamente no limiteMin — zona de alerta, não crítico)")
    void valorExatamenteNoLimiteMin() {
        assertEquals(StatusLeitura.ALERTA, calculator.calcular(0.0, sensor));
    }

    // --- margemAlerta = 0 (comportamento binário NORMAL / CRITICO) ---

    @Test
    @DisplayName("margemAlerta=0, valor=40.0 → NORMAL (sem zona de alerta)")
    void margemZeroValorNormal() {
        sensor.setMargemAlerta(0.0);
        assertEquals(StatusLeitura.NORMAL, calculator.calcular(40.0, sensor));
    }

    @Test
    @DisplayName("margemAlerta=0, valor=80.1 → CRITICO (ultrapassa limiteMax, sem alerta intermediário)")
    void margemZeroValorCritico() {
        sensor.setMargemAlerta(0.0);
        assertEquals(StatusLeitura.CRITICO, calculator.calcular(80.1, sensor));
    }

    @Test
    @DisplayName("margemAlerta=0, valor=79.9 → NORMAL (abaixo de limiteMax, sem zona de alerta)")
    void margemZeroValorAbaixoDoMax() {
        sensor.setMargemAlerta(0.0);
        assertEquals(StatusLeitura.NORMAL, calculator.calcular(79.9, sensor));
    }
}
