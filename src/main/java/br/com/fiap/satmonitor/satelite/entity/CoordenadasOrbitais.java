package br.com.fiap.satmonitor.satelite.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CoordenadasOrbitais {

    @NotNull
    @Column(name = "altitude_km")
    private Double altitudeKm;

    @NotNull
    @Column(name = "inclinacao")
    private Double inclinacao;

    @Column(name = "longitude_nodo")
    private Double longitudeNodo;
}
