-- ================================================================
-- SatMonitor — Tabelas (Oracle)
--
-- Hierarquia: Missao -> Satelite -> Sensor -> LeituraSensor
-- Sensor usa herança JOINED (uma tabela base + uma por subclasse).
-- Os campos de enum (@Enumerated STRING) viram VARCHAR2 + CHECK.
-- As FKs ficam no script 03_foreign_keys.sql (ordem independente).
-- ================================================================

-- ── Agências espaciais ──────────────────────────────────────────
CREATE TABLE TB_AGENCIA (
    id            NUMBER(19)    NOT NULL,
    nome          VARCHAR2(255) NOT NULL,
    sigla_pais    CHAR(2)       NOT NULL,
    tipo_agencia  VARCHAR2(50),
    data_cadastro DATE          DEFAULT SYSDATE NOT NULL,
    CONSTRAINT PK_AGENCIA PRIMARY KEY (id)
);

-- ── Operadores ──────────────────────────────────────────────────
CREATE TABLE TB_OPERADOR (
    id     NUMBER(19)    NOT NULL,
    login  VARCHAR2(255) NOT NULL,
    senha  VARCHAR2(255) NOT NULL,
    nome   VARCHAR2(255) NOT NULL,
    role   VARCHAR2(255) DEFAULT 'OPERADOR' NOT NULL,
    CONSTRAINT PK_OPERADOR       PRIMARY KEY (id),
    CONSTRAINT UK_OPERADOR_LOGIN UNIQUE (login),
    CONSTRAINT CK_OPERADOR_ROLE  CHECK (role IN ('OPERADOR','ADMIN'))
);

-- ── Missões ─────────────────────────────────────────────────────
CREATE TABLE TB_MISSAO (
    id               NUMBER(19)    NOT NULL,
    nome             VARCHAR2(255) NOT NULL,
    descricao        CLOB,
    data_lancamento  DATE          NOT NULL,
    status           VARCHAR2(20)  NOT NULL,
    senha_missao     VARCHAR2(255) NOT NULL,
    operador_dono_id NUMBER(19),
    agencia_id       NUMBER(19),
    objetivo         VARCHAR2(500),
    data_fim_prevista DATE,
    CONSTRAINT PK_MISSAO        PRIMARY KEY (id),
    CONSTRAINT CK_MISSAO_STATUS CHECK (status IN ('PLANEJADA','ATIVA','ENCERRADA'))
);

-- ── Vínculo Operador x Missão (N:N com role) ────────────────────
CREATE TABLE TB_OPERADOR_MISSAO (
    operador_id  NUMBER(19)   NOT NULL,
    missao_id    NUMBER(19)   NOT NULL,
    role         VARCHAR2(20) NOT NULL,
    data_entrada TIMESTAMP    NOT NULL,
    CONSTRAINT PK_OPERADOR_MISSAO PRIMARY KEY (operador_id, missao_id),
    CONSTRAINT CK_OPMISSAO_ROLE   CHECK (role IN ('DONO','SUPERVISOR','MEMBRO'))
);

-- ── Satélites (CoordenadasOrbitais é @Embedded → colunas aqui) ──
CREATE TABLE TB_SATELITE (
    id              NUMBER(19)    NOT NULL,
    nome            VARCHAR2(255) NOT NULL,
    data_lancamento DATE          NOT NULL,
    altitude_km     NUMBER,
    inclinacao      NUMBER,
    longitude_nodo  NUMBER,
    tipo_orbita     VARCHAR2(20),
    status_satelite VARCHAR2(20),
    missao_id       NUMBER(19)    NOT NULL,
    CONSTRAINT PK_SATELITE       PRIMARY KEY (id),
    CONSTRAINT CK_SAT_ORBITA     CHECK (tipo_orbita IS NULL OR tipo_orbita IN ('LEO','MEO','GEO','HEO')),
    CONSTRAINT CK_SAT_STATUS     CHECK (status_satelite IS NULL OR status_satelite IN ('ATIVO','STANDBY','MANUTENCAO','DESATIVADO'))
);

-- ── Sensor (tabela base da herança JOINED) ──────────────────────
CREATE TABLE TB_SENSOR (
    id            NUMBER(19)    NOT NULL,
    nome          VARCHAR2(255) NOT NULL,
    unidade       VARCHAR2(255) NOT NULL,
    limite_min    NUMBER        NOT NULL,
    limite_max    NUMBER        NOT NULL,
    margem_alerta NUMBER        NOT NULL,
    tipo_sensor   VARCHAR2(31)  NOT NULL,
    satelite_id   NUMBER(19)    NOT NULL,
    CONSTRAINT PK_SENSOR PRIMARY KEY (id)
);

-- ── Subclasses do Sensor (uma tabela por tipo, PK = FK p/ TB_SENSOR)
CREATE TABLE TB_SENSOR_TERMICO (
    id             NUMBER(19) NOT NULL,
    unidade_escala VARCHAR2(20),
    CONSTRAINT PK_SENSOR_TERMICO PRIMARY KEY (id),
    CONSTRAINT CK_TERMICO_ESCALA CHECK (unidade_escala IN ('CELSIUS','FAHRENHEIT','KELVIN'))
);

CREATE TABLE TB_SENSOR_PRESSAO (
    id           NUMBER(19) NOT NULL,
    tipo_pressao VARCHAR2(20),
    CONSTRAINT PK_SENSOR_PRESSAO PRIMARY KEY (id),
    CONSTRAINT CK_PRESSAO_TIPO   CHECK (tipo_pressao IN ('ABSOLUTA','RELATIVA'))
);

CREATE TABLE TB_SENSOR_RADIACAO (
    id            NUMBER(19) NOT NULL,
    tipo_radiacao VARCHAR2(20),
    CONSTRAINT PK_SENSOR_RADIACAO PRIMARY KEY (id),
    CONSTRAINT CK_RADIACAO_TIPO   CHECK (tipo_radiacao IN ('IONIZANTE','NAO_IONIZANTE'))
);

CREATE TABLE TB_MAGNETOMETRO (
    id            NUMBER(19) NOT NULL,
    eixos_medicao VARCHAR2(10),
    CONSTRAINT PK_MAGNETOMETRO PRIMARY KEY (id),
    CONSTRAINT CK_MAG_EIXOS    CHECK (eixos_medicao IN ('X','Y','Z','XY','XZ','YZ','XYZ'))
);

-- ── Leituras dos sensores ───────────────────────────────────────
CREATE TABLE TB_LEITURA_SENSOR (
    id                NUMBER(19)   NOT NULL,
    valor             NUMBER       NOT NULL,
    data_hora_leitura TIMESTAMP    NOT NULL,
    status            VARCHAR2(20) NOT NULL,
    sensor_id         NUMBER(19)   NOT NULL,
    latitude          NUMBER(9,6),
    longitude         NUMBER(9,6),
    qualidade         VARCHAR2(20) DEFAULT 'BOA',
    CONSTRAINT PK_LEITURA_SENSOR  PRIMARY KEY (id),
    CONSTRAINT CK_LEITURA_STATUS  CHECK (status IN ('NORMAL','ALERTA','CRITICO')),
    CONSTRAINT CK_LEITURA_QUAL    CHECK (qualidade IS NULL OR qualidade IN ('BOA','DEGRADADA','INVALIDA'))
);

-- ── Alertas (gerados automaticamente pela API Java) ─────────────
CREATE TABLE TB_ALERTA (
    id            NUMBER(19)    NOT NULL,
    leitura_id    NUMBER(19)    NOT NULL,
    tipo_alerta   VARCHAR2(20)  NOT NULL,
    descricao     VARCHAR2(500),
    data_alerta   TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL,
    status_alerta VARCHAR2(20)  DEFAULT 'ATIVO' NOT NULL,
    CONSTRAINT PK_ALERTA        PRIMARY KEY (id),
    CONSTRAINT CK_ALERTA_TIPO   CHECK (tipo_alerta IN ('ALERTA','CRITICO')),
    CONSTRAINT CK_ALERTA_STATUS CHECK (status_alerta IN ('ATIVO','RECONHECIDO','RESOLVIDO'))
);
