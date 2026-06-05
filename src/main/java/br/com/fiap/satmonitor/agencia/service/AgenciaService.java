package br.com.fiap.satmonitor.agencia.service;

import br.com.fiap.satmonitor.agencia.dto.AgenciaRequest;
import br.com.fiap.satmonitor.agencia.dto.AgenciaResponse;
import br.com.fiap.satmonitor.agencia.entity.Agencia;
import br.com.fiap.satmonitor.agencia.repository.AgenciaRepository;
import br.com.fiap.satmonitor.exception.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgenciaService {

    private final AgenciaRepository agenciaRepository;

    @Transactional
    public AgenciaResponse criar(AgenciaRequest req) {
        Agencia agencia = Agencia.builder()
                .nome(req.nome())
                .siglaPais(req.siglaPais().toUpperCase())
                .tipoAgencia(req.tipoAgencia())
                .build();

        agenciaRepository.save(agencia);
        log.info("Agência '{}' ({}) criada", agencia.getNome(), agencia.getSiglaPais());
        return toResponse(agencia);
    }

    @Transactional(readOnly = true)
    public Page<AgenciaResponse> listar(Pageable pageable) {
        return agenciaRepository.findAll(pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public AgenciaResponse buscarPorId(Long id) {
        return toResponse(buscarEntidade(id));
    }

    @Transactional
    public AgenciaResponse atualizar(Long id, AgenciaRequest req) {
        Agencia agencia = buscarEntidade(id);
        agencia.setNome(req.nome());
        agencia.setSiglaPais(req.siglaPais().toUpperCase());
        agencia.setTipoAgencia(req.tipoAgencia());
        agenciaRepository.save(agencia);
        log.info("Agência id={} atualizada", id);
        return toResponse(agencia);
    }

    @Transactional
    public void deletar(Long id) {
        Agencia agencia = buscarEntidade(id);
        agenciaRepository.delete(agencia);
        log.info("Agência id={} deletada", id);
    }

    Agencia buscarEntidade(Long id) {
        return agenciaRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Agência não encontrada com id: " + id));
    }

    private AgenciaResponse toResponse(Agencia agencia) {
        return AgenciaResponse.builder()
                .id(agencia.getId())
                .nome(agencia.getNome())
                .siglaPais(agencia.getSiglaPais())
                .tipoAgencia(agencia.getTipoAgencia())
                .dataCadastro(agencia.getDataCadastro())
                .build();
    }
}
