# Scripts SQL — Oracle (SatMonitor)

DDL para provisionar o schema da API no Oracle (FIAP). Reflete o estado atual das entidades JPA.

## Ordem de execução

```
01_sequences.sql      → cria as 7 sequences (allocationSize = 1)
02_tables.sql         → cria as 12 tabelas + PKs + CHECKs de enum
03_foreign_keys.sql   → adiciona as FKs entre as tabelas
```

`99_drop_all.sql` derruba tudo (ordem inversa) para resetar.

### SQL*Plus

```sql
@01_sequences.sql
@02_tables.sql
@03_foreign_keys.sql
```

### SQL Developer / SQLcl
Abra cada arquivo na ordem e execute como script (F5), ou rode `@caminho/arquivo.sql`.

## Tabelas criadas

| Tabela                | Origem (entidade)              | Observação                                                   |
|-----------------------|--------------------------------|--------------------------------------------------------------|
| `TB_AGENCIA`          | `Agencia`                      | `sigla_pais` CHAR(2); `tipo_agencia` opcional                |
| `TB_OPERADOR`         | `Operador`                     | `login` único; `role` OPERADOR/ADMIN                         |
| `TB_MISSAO`           | `Missao`                       | `status` PLANEJADA/ATIVA/ENCERRADA; FK opcional p/ TB_AGENCIA; `objetivo` e `data_fim_prevista` opcionais |
| `TB_OPERADOR_MISSAO`  | `OperadorMissao`               | PK composta; `role` DONO/SUPERVISOR/MEMBRO                   |
| `TB_SATELITE`         | `Satelite` + `CoordenadasOrbitais` | coordenadas embutidas; `tipo_orbita` e `status_satelite` opcionais |
| `TB_SENSOR`           | `Sensor` (base JOINED)         | `tipo_sensor` é o discriminador                              |
| `TB_SENSOR_TERMICO`   | `SensorTermico`                | `unidade_escala` CELSIUS/FAHRENHEIT/KELVIN                   |
| `TB_SENSOR_PRESSAO`   | `SensorPressao`                | `tipo_pressao` ABSOLUTA/RELATIVA                             |
| `TB_SENSOR_RADIACAO`  | `SensorRadiacao`               | `tipo_radiacao` IONIZANTE/NAO_IONIZANTE                      |
| `TB_MAGNETOMETRO`     | `Magnetometro`                 | `eixos_medicao` X/Y/Z/XY/XZ/YZ/XYZ                          |
| `TB_LEITURA_SENSOR`   | `LeituraSensor`                | `status` NORMAL/ALERTA/CRITICO; `latitude`/`longitude`/`qualidade` opcionais |
| `TB_ALERTA`           | `Alerta`                       | gerado automaticamente em ALERTA/CRITICO; `status_alerta` ATIVO/RECONHECIDO/RESOLVIDO |

## Relação com `ddl-auto`

O `application-prod.properties` está com `spring.jpa.hibernate.ddl-auto=update`. Rodando estes scripts **antes** de subir a aplicação, o Hibernate encontra as tabelas já existentes e não recria nada (em `update` ele só adiciona o que falta, nunca altera/derruba o que existe).

Se preferir que o banco seja a única fonte da verdade do schema (sem o Hibernate tocar na estrutura), troque para:

```properties
spring.jpa.hibernate.ddl-auto=none
```

> As subclasses de sensor usam herança JOINED: a PK de cada tabela filha é também
> FK para `TB_SENSOR`. A exclusão em cascata é feita pela aplicação (JPA), por isso
> as FKs não têm `ON DELETE CASCADE`.
