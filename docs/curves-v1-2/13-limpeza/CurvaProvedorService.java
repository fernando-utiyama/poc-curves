package br.com.poc.application.service;

import br.com.poc.adapter.in.api.rest.dto.CurvaProvedorCurvaResponse;
import br.com.poc.adapter.out.persistence.repository.SpringDataProvedorRepository;
import br.com.poc.application.exception.BusinessException;
import br.com.poc.application.exception.CadastroErrorCode;
import br.com.poc.application.exception.ConflictException;
import br.com.poc.application.exception.NotFoundException;
import br.com.poc.application.port.in.usecase.CurvaProvedorUseCase;
import br.com.poc.application.port.out.CurvaMercdRepositoryPort;
import br.com.poc.application.port.out.CurvaPrvdrRepositoryPort;
import br.com.poc.application.port.out.EventosPort;
import br.com.poc.domain.aviso.Detalhe;
import br.com.poc.domain.cadastro.*;
import br.com.poc.domain.evento.EventoCadastroAlterado;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class CurvaProvedorService implements CurvaProvedorUseCase {

    private final CurvaMercdRepositoryPort curvaRepositoryPort;
    private final CurvaPrvdrRepositoryPort curvaPrvdrRepositoryPort;
    private final SpringDataProvedorRepository provedorRepository;
    private final EventosPort eventosPort;

    @Override
    @Transactional(readOnly = true)
    public List<CurvaProvedor> listarPorCurva(String nomeCurva) {
        return curvaPrvdrRepositoryPort.findByNomeCurva(obterCurva(nomeCurva).nome());
    }

    @Override
    @Transactional
    public CurvaProvedor criar(String nomeCurva, CriarCurvaProvedorInput input) {
        CurvaMercado curva = obterCurva(nomeCurva);

        validarCamposCriacao(input);

        // o provedor tem que existir em tPrvdrDadoMercd
        if (!provedorRepository.existsById(input.provedor())) {
            throw new NotFoundException(CadastroErrorCode.NAO_ENCONTRADO.getCode(), "Provedor " + input.provedor() + " não encontrado");
        }
        if (curvaPrvdrRepositoryPort.existsByNomeCurvaAndProvedorAndProduto(curva.nome(), input.provedor(), input.produto())) {
            throw new ConflictException(CadastroErrorCode.PROVEDOR_DUPLICADO);
        }
        if (curvaPrvdrRepositoryPort.existsByNomeCurvaAndPrioridade(curva.nome(), input.prioridade())) {
            throw new ConflictException(CadastroErrorCode.PRIORIDADE_EM_USO);
        }

        CurvaProvedor nova = new CurvaProvedor(
            curvaPrvdrRepositoryPort.proximoIdCurvaPrvdr(),
            curva.nome(),
            input.provedor(),
            input.produto(),
            input.tickerProvedor().trim(),
            input.prioridade()
        );
        CurvaProvedor salva = curvaPrvdrRepositoryPort.salvar(nova);

        publicarEvento(curva.codigo(), curva.nome(), "CRIACAO", null, salva);
        return salva;
    }

    @Override
    @Transactional
    public CurvaProvedor alterar(String nomeCurva, Long idCurvaProvedor, AtualizarCurvaProvedorInput input) {
        CurvaMercado curva = obterCurva(nomeCurva);
        CurvaProvedor existente = obterProvedorDaCurva(curva, idCurvaProvedor);

        validarCamposAlteracao(input);

        if (curvaPrvdrRepositoryPort.existsByNomeCurvaAndProvedorAndProdutoDiferente(curva.nome(), existente.provedor(), input.produto(), idCurvaProvedor)) {
            throw new ConflictException(CadastroErrorCode.PROVEDOR_DUPLICADO);
        }
        if (curvaPrvdrRepositoryPort.existsByNomeCurvaAndPrioridadeDiferente(curva.nome(), input.prioridade(), idCurvaProvedor)) {
            throw new ConflictException(CadastroErrorCode.PRIORIDADE_EM_USO);
        }

        CurvaProvedor alterada = new CurvaProvedor(
            idCurvaProvedor,
            curva.nome(),
            existente.provedor(),   // o provedor não pode ser alterado
            input.produto(),
            input.tickerProvedor().trim(),
            input.prioridade()
        );
        CurvaProvedor salva = curvaPrvdrRepositoryPort.salvar(alterada);

        publicarEvento(curva.codigo(), curva.nome(), "ALTERACAO", existente, salva);
        return salva;
    }

    @Override
    @Transactional
    public void excluir(String nomeCurva, Long idCurvaProvedor) {
        CurvaMercado curva = obterCurva(nomeCurva);
        CurvaProvedor existente = obterProvedorDaCurva(curva, idCurvaProvedor);

        curvaPrvdrRepositoryPort.excluir(idCurvaProvedor, curva.nome());

        publicarEvento(curva.codigo(), curva.nome(), "EXCLUSAO", existente, null);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CurvaProvedorCurvaResponse> listarPorOrigem(String provedor, String produto, String tickerProvedor) {
        Map<String, String> codigoPorNome = new HashMap<>();
        for (CurvaMercado c : curvaRepositoryPort.findAllValidas()) {
            codigoPorNome.put(c.nome(), c.codigo());
        }

        return curvaPrvdrRepositoryPort.buscarCurvasProvedor(provedor, produto, tickerProvedor).stream()
            .map(l -> new CurvaProvedorCurvaResponse(
                codigoPorNome.getOrDefault(l.nomeCurva(), l.nomeCurva()),
                l.nomeCurva(),
                l.prioridade()
            ))
            .sorted(Comparator.comparing(CurvaProvedorCurvaResponse::prioridade))
            .toList();
    }

    private CurvaMercado obterCurva(String nomeCurva) {
        return curvaRepositoryPort.findByNome(nomeCurva)
            .orElseThrow(() -> new NotFoundException(CadastroErrorCode.NAO_ENCONTRADO.getCode(), "Curva " + nomeCurva + " não encontrada"));
    }

    private CurvaProvedor obterProvedorDaCurva(CurvaMercado curva, Long idCurvaProvedor) {
        return curvaPrvdrRepositoryPort.findByIdAndNomeCurva(idCurvaProvedor, curva.nome())
            .orElseThrow(() -> new NotFoundException(CadastroErrorCode.NAO_ENCONTRADO.getCode(),
                "Curva provedor " + idCurvaProvedor + " não encontrada na curva " + curva.nome()));
    }

    private void validarCamposCriacao(CriarCurvaProvedorInput input) {
        List<Detalhe> detalhes = new ArrayList<>();
        if (input.provedor() == null || input.provedor().isBlank()) {
            detalhes.add(new Detalhe("provedor", null, input.provedor(), "Provedor é obrigatório"));
        }
        validarCamposComuns(input.produto(), input.tickerProvedor(), input.prioridade(), detalhes);
        recusarSeHouverErros(detalhes);
    }

    private void validarCamposAlteracao(AtualizarCurvaProvedorInput input) {
        List<Detalhe> detalhes = new ArrayList<>();
        validarCamposComuns(input.produto(), input.tickerProvedor(), input.prioridade(), detalhes);
        recusarSeHouverErros(detalhes);
    }

    private void validarCamposComuns(String produto, String tickerProvedor, Integer prioridade, List<Detalhe> detalhes) {
        if (produto == null || produto.isBlank() || produto.length() > 50) {
            detalhes.add(new Detalhe("produto", null, produto, "Produto deve ter entre 1 e 50 caracteres"));
        }
        if (tickerProvedor == null || tickerProvedor.isBlank() || tickerProvedor.length() > 1024) {
            detalhes.add(new Detalhe("tickerProvedor", null, tickerProvedor, "Código na fonte deve ter entre 1 e 1024 caracteres"));
        }
        if (prioridade == null || prioridade < 1) {
            detalhes.add(new Detalhe("prioridade", null, prioridade != null ? prioridade.toString() : null,
                "Prioridade deve ser um número inteiro maior ou igual a 1"));
        }
    }

    private void recusarSeHouverErros(List<Detalhe> detalhes) {
        if (!detalhes.isEmpty()) {
            throw new BusinessException(CadastroErrorCode.DADOS_INVALIDOS, detalhes.toArray());
        }
    }

    private void publicarEvento(String codigo, String nome, String operacao, Object anterior, Object novo) {
        eventosPort.publicarCadastroAlterado(new EventoCadastroAlterado(
            UUID.randomUUID(),
            codigo,
            nome,
            "PROVEDOR",
            operacao,
            OffsetDateTime.now(),
            null,
            anterior,
            novo
        ));
    }
}
