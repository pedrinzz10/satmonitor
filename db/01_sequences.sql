-- ================================================================
-- SatMonitor — Sequences (Oracle)
--
-- A API usa GenerationType.SEQUENCE com allocationSize = 1,
-- portanto cada sequence incrementa de 1 em 1 (sem cache para
-- não gerar lacunas, alinhado ao allocationSize = 1).
--
-- TB_OPERADOR_MISSAO não tem sequence: a PK é composta
-- (operador_id, missao_id).
-- ================================================================

CREATE SEQUENCE SEQ_OPERADOR START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE SEQ_AGENCIA  START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE SEQ_MISSAO   START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE SEQ_SATELITE START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE SEQ_SENSOR   START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE SEQ_LEITURA  START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE SEQ_ALERTA   START WITH 1 INCREMENT BY 1 NOCACHE;
