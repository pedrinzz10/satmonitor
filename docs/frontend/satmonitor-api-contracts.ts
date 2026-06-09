// ============================================================
// SATMONITOR — Contratos de tipo para o frontend
// Gerado a partir do scan completo da API REST
// Base URL: http://localhost:8080  (dev)
// Autenticação: Authorization: Bearer <token>  (8 horas)
// ============================================================

// ------------------------------------------------------------
// ENUMS
// ------------------------------------------------------------

export type StatusMissao = "PLANEJADA" | "ATIVA" | "ENCERRADA";
export type RoleMissao = "DONO" | "SUPERVISOR" | "MEMBRO";
export type StatusSolicitacao = "PENDENTE" | "APROVADO" | "REJEITADO";

export type StatusSatelite = "ATIVO" | "STANDBY" | "MANUTENCAO" | "DESATIVADO";
export type TipoOrbita = "LEO" | "MEO" | "GEO" | "HEO";

export type TipoSensor = "TERMICO" | "PRESSAO" | "RADIACAO" | "MAGNETOMETRO";
export type UnidadeEscala = "CELSIUS" | "FAHRENHEIT" | "KELVIN";
export type TipoPressao = "ABSOLUTA" | "RELATIVA";
export type TipoRadiacao = "IONIZANTE" | "NAO_IONIZANTE";
export type EixosMedicao = "X" | "Y" | "Z" | "XY" | "XZ" | "YZ" | "XYZ";

export type StatusLeitura = "NORMAL" | "ALERTA" | "CRITICO";
export type QualidadeLeitura = "BOA" | "DEGRADADA" | "INVALIDA";

export type StatusAlerta = "ATIVO" | "RECONHECIDO" | "RESOLVIDO";

// ------------------------------------------------------------
// PAGINAÇÃO
// Todos os GETs de lista retornam este formato.
// Query params: ?page=0&size=10&sort=nome,asc
// ------------------------------------------------------------

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;       // página atual (0-indexed)
  size: number;
  first: boolean;
  last: boolean;
  empty: boolean;
}

// ------------------------------------------------------------
// HATEOAS
// Todos os responses de recurso único incluem _links.
// Use os hrefs para navegação em vez de montar URLs na mão.
// ------------------------------------------------------------

export interface HateoasLinks {
  _links?: Record<string, { href: string }>;
}

// ------------------------------------------------------------
// ERRO
// Formato padrão de erro da API em todos os 4xx/5xx.
// ------------------------------------------------------------

export interface ErroResponse {
  erro: string;
  detalhes?: string;
  timestamp: string; // ISO-8601
}

// ============================================================
// AUTH
// ============================================================

export interface LoginRequest {
  login: string;   // obrigatório, não vazio
  senha: string;   // obrigatório, não vazio
}

export interface RegistroRequest {
  login: string;      // obrigatório, não vazio, deve ser único
  senha: string;      // obrigatório, não vazio
  nome: string;       // obrigatório, não vazio
  agenciaId?: number; // opcional — operador pode não ter agência no cadastro
}

export interface TokenResponse {
  token: string;
}

// ============================================================
// AGÊNCIA
// ============================================================

export interface AgenciaRequest {
  nome: string;          // obrigatório, max 255 chars
  siglaPais: string;     // obrigatório, exatamente 2 chars (ex: "BR", "US")
  tipoAgencia?: string;  // opcional (ex: "ESPACIAL", "DEFESA")
}

export interface AgenciaResponse extends HateoasLinks {
  id: number;
  nome: string;
  siglaPais: string;
  tipoAgencia: string | null;
  dataCadastro: string; // "YYYY-MM-DD"
}

// ============================================================
// MISSÃO
// ============================================================

export interface MissaoRequest {
  nome: string;                  // obrigatório, max 255 chars
  descricao?: string;            // opcional
  dataLancamento: string;        // obrigatório, "YYYY-MM-DD"
  status: StatusMissao;          // obrigatório
  senhaMissao: string;           // obrigatório, mín. 6 chars
  agenciaId?: number;            // opcional
  objetivo?: string;             // opcional
  dataFimPrevista?: string;      // opcional, "YYYY-MM-DD"
  permitirCowork?: boolean;      // opcional, default false
}

// PUT /missoes/{id} — sem senhaMissao (senha não é alterável)
export interface MissaoUpdateRequest {
  nome: string;                  // obrigatório
  descricao?: string;
  dataLancamento: string;        // obrigatório, "YYYY-MM-DD"
  status: StatusMissao;          // obrigatório
  agenciaId?: number;
  objetivo?: string;
  dataFimPrevista?: string;      // "YYYY-MM-DD"
  permitirCowork?: boolean;
}

export interface EntrarMissaoRequest {
  senha: string; // obrigatório — senha definida pelo DONO na criação
}

export interface MissaoResponse extends HateoasLinks {
  id: number;
  nome: string;
  descricao: string | null;
  dataLancamento: string;        // "YYYY-MM-DD"
  status: StatusMissao;
  roleDoOperador: RoleMissao;    // role do operador autenticado nesta missão
  totalMembros: number;
  totalSatelites: number;
  agenciaId: number | null;
  nomeAgencia: string | null;
  objetivo: string | null;
  dataFimPrevista: string | null; // "YYYY-MM-DD"
  permitirCowork: boolean;
}

export interface MembroResponse extends HateoasLinks {
  operadorId: number;
  nome: string;
  login: string;
  role: RoleMissao;
  dataEntrada: string; // "YYYY-MM-DDTHH:mm:ss"
}

export interface SolicitacaoResponse {
  id: number;
  operadorId: number;
  nomeOperador: string;
  agenciaId: number | null;
  nomeAgencia: string | null;
  status: StatusSolicitacao;
  dataSolicitacao: string; // "YYYY-MM-DDTHH:mm:ss"
}

// ============================================================
// SATÉLITE
// ============================================================

export interface CoordenadasOrbitaisRequest {
  altitudeKm: number;      // obrigatório
  inclinacao: number;      // obrigatório
  longitudeNodo?: number;  // opcional
}

export interface SateliteRequest {
  nome: string;                        // obrigatório, max 255 chars
  dataLancamento: string;              // obrigatório, "YYYY-MM-DD"
  missaoId: number;                    // obrigatório
  coordenadas: CoordenadasOrbitaisRequest; // obrigatório, objeto aninhado validado
  tipoOrbita?: TipoOrbita;            // opcional
  statusSatelite?: StatusSatelite;    // opcional
}

export interface SateliteResponse extends HateoasLinks {
  id: number;
  nome: string;
  dataLancamento: string;  // "YYYY-MM-DD"
  altitudeKm: number;
  inclinacao: number;
  longitudeNodo: number | null;
  tipoOrbita: TipoOrbita | null;
  statusSatelite: StatusSatelite | null;
  missaoId: number;
  nomeMissao: string;
  totalSensores: number;
}

export interface EstatisticasResponse {
  sateliteId: number;
  nomeSatelite: string;
  mediaValor: number | null;
  minValor: number | null;
  maxValor: number | null;
  totalLeituras: number;
  totalAlertas: number;
  totalCriticos: number;
  ultimaLeitura: string | null; // "YYYY-MM-DDTHH:mm:ss"
}

// ============================================================
// SENSOR
// O campo `tipo` define qual campo extra é obrigatório:
//   TERMICO      → unidadeEscala
//   PRESSAO      → tipoPressao
//   RADIACAO     → tipoRadiacao
//   MAGNETOMETRO → eixosMedicao
// Na resposta, o campo extra aparece como `detalhe`.
// O tipo é IMUTÁVEL após a criação — não pode ser alterado via PUT.
// ============================================================

export interface SensorRequest {
  nome: string;           // obrigatório, max 255 chars
  unidade: string;        // obrigatório (ex: "graus_C", "hPa", "Gy", "nT")
  limiteMin: number;      // obrigatório, deve ser < limiteMax
  limiteMax: number;      // obrigatório
  margemAlerta: number;   // obrigatório, 0–100 (percentual)
  sateliteId: number;     // obrigatório
  tipo: TipoSensor;       // obrigatório

  // Campos condicionais — obrigatório conforme o tipo:
  unidadeEscala?: UnidadeEscala;  // obrigatório se tipo === "TERMICO"
  tipoPressao?: TipoPressao;      // obrigatório se tipo === "PRESSAO"
  tipoRadiacao?: TipoRadiacao;    // obrigatório se tipo === "RADIACAO"
  eixosMedicao?: EixosMedicao;    // obrigatório se tipo === "MAGNETOMETRO"
}

// PUT /sensores/{id}
// `tipo` e campos condicionais são IGNORADOS pelo backend — o valor original da criação é mantido.
// No formulário de edição, renderizar `tipo` e o campo extra como readonly/disabled.
export interface SensorUpdateRequest {
  nome: string;
  unidade: string;
  limiteMin: number;
  limiteMax: number;
  margemAlerta: number;
  sateliteId: number;
}

export interface SensorResponse extends HateoasLinks {
  id: number;
  nome: string;
  tipo: TipoSensor;
  unidade: string;
  limiteMin: number;
  limiteMax: number;
  margemAlerta: number;
  sateliteId: number;
  nomeSatelite: string;
  detalhe: string | null; // valor do campo específico do tipo (ex: "CELSIUS", "XYZ")
}

// ============================================================
// LEITURA
// POST /leituras é público (IoT/ESP32) — não precisa de token.
// status e dataHoraLeitura são calculados pelo servidor — nunca enviar.
// ============================================================

export interface LeituraRequest {
  valor: number;           // obrigatório
  sensorId: number;        // obrigatório
  latitude?: number;       // opcional
  longitude?: number;      // opcional
  qualidade?: QualidadeLeitura; // opcional, default "BOA"
}

export interface LeituraResponse extends HateoasLinks {
  id: number;
  valor: number;
  dataHoraLeitura: string;  // "YYYY-MM-DDTHH:mm:ss.SSS"
  status: StatusLeitura;    // calculado automaticamente
  sensorId: number;
  nomeSensor: string;
  sateliteId: number;
  nomeSatelite: string;
  latitude: number | null;
  longitude: number | null;
  qualidade: QualidadeLeitura;
}

// ============================================================
// ALERTA
// Alertas são criados automaticamente quando uma leitura é ALERTA ou CRITICO.
// Não existe POST /alertas — apenas GET e PATCH para status.
// ============================================================

export interface AlertaResponse extends HateoasLinks {
  id: number;
  leituraId: number;
  valorLeitura: number;
  sensorId: number;
  nomeSensor: string;
  sateliteId: number;
  nomeSatelite: string;
  missaoId: number;
  nomeMissao: string;
  tipoAlerta: string;    // ex: "ALERTA" ou "CRITICO"
  descricao: string;
  dataAlerta: string;    // "YYYY-MM-DDTHH:mm:ss"
  statusAlerta: StatusAlerta;
}
