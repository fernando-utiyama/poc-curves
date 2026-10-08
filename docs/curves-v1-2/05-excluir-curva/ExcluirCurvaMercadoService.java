package br.com.poc.application.service;

import br.com.poc.application.exception.BusinessException;
import br.com.poc.application.exception.CadastroErrorCode;
import br.com.poc.application.exception.NotFoundException;
import br.com.poc.application.port.in.usecase.ExcluirCurvaMercadoUseCase;
import br.com.poc.application.port.out.ConfiguracaoCurvaRepositoryPort;
import br.com.poc.application.port.out.CurvaMercdRepositoryPort;
import br.com.poc.application.port.out.CurvaPrvdrRepositoryPort;
import br.com.poc.application.port.out.DadosConstruidosPort;
import br.com.poc.application.port.out.EventosPort;
import br.com.poc.domain.aviso.Detalhe;
import br.com.poc.domain.cadastro.ConfiguracaoCurva;
import br.com.poc.domain.cadastro.CurvaMercado;
import br.com.poc.domain.cadastro.CurvaProvedor;
import br.com.poc.domain.cadastro.LinhasPorTabela;
import br.com.poc.domain.evento.EventoCadastroAlterado;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ExcluirCurvaMercadoService implements ExcluirCurvaMercadoUseCase {

    /** Provedor que marca a curva derivada: o código da curva componente fica em codigoNaFonte. */
    private static final String PROVEDOR_DERIVADA = "TCEN";

    private final CurvaMercdRepositoryPort curvaRepositoryPort;
    private final CurvaPrvdrRepositoryPort curvaPrvdrRepositoryPort;
    private final ConfiguracaoCurvaRepositoryPort configuracaoRepositoryPort;
    private final DadosConstruidosPort dadosConstruidosPort;
    private final EventosPort eventosPort;

    @Override
    @Transactional
    public void excluir(String codigo) {
        CurvaMercado curva = curvaRepositoryPort.findByCodigo(codigo)
            .orElseThrow(() -> new NotFoundException(CadastroErrorCode.NAO_ENCONTRADO.getCode(), "Curva " + codigo + " não encontrada"));

        List<LinhasPorTabela> dependentes = dadosConstruidosPort.dependentes(curva.nome());
        if (!dependentes.isEmpty()) {
            Object[] detalhes = dependentes.stream()
                .map(d -> new Detalhe("codigo", null, codigo, d.linhas() + " linha(s) em " + d.tabela()
                    + "; apague antes (construído pelo delete da data, dado bruto pelas rotas primaria-*) ou use a inativação"))
                .toArray();
            throw new BusinessException(CadastroErrorCode.CURVA_COM_HISTORICO, detalhes);
        }

        List<CurvaProvedor> derivadas = curvaPrvdrRepositoryPort.buscarCurvasProvedor(PROVEDOR_DERIVADA, null, codigo);
        if (!derivadas.isEmpty()) {
            Object[] detalhes = derivadas.stream()
                .map(d -> new Detalhe("codigo", null, codigo, "Componente da curva " + d.nomeCurva()))
                .toArray();
            throw new BusinessException(CadastroErrorCode.CURVA_COMPONENTE, detalhes);
        }

        List<ConfiguracaoCurva> configuracoes = configuracaoRepositoryPort.findByNomeCurva(curva.nome());
        List<CurvaProvedor> provedores = curvaPrvdrRepositoryPort.findByNomeCurva(curva.nome());

        configuracoes.forEach(c -> configuracaoRepositoryPort.excluir(c.id()));
        provedores.forEach(p -> curvaPrvdrRepositoryPort.excluir(p.idCurvaProvedor(), curva.nome()));
        curvaRepositoryPort.excluir(curva.nome());

        eventosPort.publicarCadastroAlterado(new EventoCadastroAlterado(
            UUID.randomUUID(),
            curva.codigo(),
            curva.nome(),
            "CURVA",
            "EXCLUSAO",
            OffsetDateTime.now(),
            null,
            curva,
            null
        ));
    }
}
