package br.com.fiap.satmonitor.leitura.service;

import br.com.fiap.satmonitor.leitura.enums.StatusLeitura;
import br.com.fiap.satmonitor.sensor.entity.Sensor;
import org.springframework.stereotype.Component;

@Component
public class StatusCalculator {

    public StatusLeitura calcular(Double valor, Sensor sensor) {
        double faixa = sensor.getLimiteMax() - sensor.getLimiteMin();
        double zonaAlerta = faixa * (sensor.getMargemAlerta() / 100.0);
        double zonaAlertaMin = sensor.getLimiteMin() + zonaAlerta;
        double zonaAlertaMax = sensor.getLimiteMax() - zonaAlerta;

        if (valor < sensor.getLimiteMin()) return StatusLeitura.CRITICO;
        if (valor > sensor.getLimiteMax()) return StatusLeitura.CRITICO;
        if (valor < zonaAlertaMin)        return StatusLeitura.ALERTA;
        if (valor > zonaAlertaMax)        return StatusLeitura.ALERTA;
        return StatusLeitura.NORMAL;
    }
}
