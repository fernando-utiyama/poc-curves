package br.com.poc.application.service;

import br.com.poc.application.exception.BusinessException;
import br.com.poc.application.exception.CadastroErrorCode;
import br.com.poc.application.exception.ConflictException;
import br.com.poc.application.exception.NotFoundException;
import br.com.poc.application.port.in.usecase.CurvaMercadoUseCase;
import br.com.poc.application.port.out.ConfiguracaoCurvaRepositoryPort;
import br.com.poc.application.port.out.CurvaMercdRepositoryPort;
import br.com.poc.application.port.out.CurvaPrvdrRepositoryPort;
import br.com.poc.application.port.out.EventosPort;
import br.com.poc.domain.CompoundingCotacao;
import br.com.poc.domain.DayCounterCotacao;
import br.com.poc.domain.SituacaoCurva;
import br.com.poc.domain.Unidade;
import br.com.poc.domain.aviso.Detalhe;
import br.com.poc.domain.cadastro.*;
import br.com.poc.domain.evento.EventoCadastroAlterado;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class CurvaMercadoService implements CurvaMercadoUseCase {

    private static final Pattern CODIGO_PATTERN = Pattern.compile("^[A-Z0-9_]{1,50}$");
    private static final Set<String> PAISES_VALIDOS = Set.of(Locale.getISOCountries());

    private final CurvaMercdRepositoryPort repositoryPort;
    private final CurvaPrvdrRepositoryPort curvaPrvdrRepositoryPort;
    private final ConfiguracaoCurvaRepositoryPort configuracaoRepositoryPort;
    private final EventosPort eventosPort;

    @Override
    @Transactional(readOnly = true)
    public Page<CurvaMercado> listar(String nome, String codigo, String unidade, String situacao,
                                     String provedor, String dono, int pagina, int tamanho) {
        int paginaAjustada = Math.max(pagina, 0);
        int tamanhoAjustado = tamanho <= 0 ? 50 : Math.min(tamanho, 500);
        // o código é opcional no banco: o nome (PK) desempata, para a paginação ficar estável
        Pageable pageable = PageRequest.of(paginaAjustada, tamanhoAjustado,
            Sort.by("tickerIdtfdUnic").ascending().and(Sort.by("tickerIndcd").ascending()));
        return repositoryPort.listar(nome, codigo, unidade, situacao, provedor, dono, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<CurvaMercado> listar(String nome, String codigo, String unidade, String situacao, int pagina, int tamanho) {
        return listar(nome, codigo, unidade, situacao, null, null, pagina, tamanho);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, List<String>> buscarProvedoresPorNomes(Collection<String> nomesCurva) {
        if (curvaPrvdrRepositoryPort == null) {
            return Map.of();
        }
        return curvaPrvdrRepositoryPort.findProvedoresPorNomesCurva(nomesCurva);
    }

    @Override
    @Transactional(readOnly = true)
    public CurvaMercadoDetalhada consultar(String nome) {
        CurvaMercado curva = obterCurva(nome);
        return detalhar(curva);
    }

    @Override
    @Transactional(readOnly = true)
    public CurvaAuditoria consultarAuditoria(String nome) {
        CurvaMercado curva = obterCurva(nome);

        List<CurvaProvedor> provedores = provedoresDe(curva.nome());
        List<ConfiguracaoCurva> configuracoes = configuracaoRepositoryPort != null
            ? configuracaoRepositoryPort.findByNomeCurva(curva.nome())
            : List.of();

        return new CurvaAuditoria(curva, provedores, configuracoes);
    }

    public CurvaMercadoDetalhada detalhar(CurvaMercado curva) {
        return new CurvaMercadoDetalhada(curva, provedoresDe(curva.nome()), configuracaoVigenteDe(curva.nome()), List.of());
    }

    @Override
    @Transactional
    public CurvaMercadoDetalhada criar(CurvaMercadoInput input) {
        List<Detalhe> detalhes = new ArrayList<>();
        validarCampos(input, true, null, detalhes);

        if (!detalhes.isEmpty()) {
            throw new BusinessException(CadastroErrorCode.DADOS_INVALIDOS, detalhes.toArray());
        }

        // Unicidade de código
        if (repositoryPort.existsByCodigo(input.codigo())) {
            throw new ConflictException(CadastroErrorCode.CODIGO_EM_USO);
        }

        // Unicidade de nome normalizado contra todos os registros de tCurvaMercd (inclusive os sem código)
        String nomeNormalizado = CurvaMercado.normalizarNome(input.nome());
        boolean nomeEmUso = repositoryPort.findAllNomes().stream()
            .anyMatch(n -> n != null && CurvaMercado.normalizarNome(n).equals(nomeNormalizado));
        if (nomeEmUso) {
            throw new ConflictException(CadastroErrorCode.NOME_EM_USO);
        }

        Unidade unidade = parseEnum(Unidade.class, input.unidade());
        DayCounterCotacao dayCounter = parseEnum(DayCounterCotacao.class, input.dayCounterCotacao());
        CompoundingCotacao compounding = parseEnum(CompoundingCotacao.class, input.compounding());

        LocalDateTime now = LocalDateTime.now();
        CurvaMercado nova = new CurvaMercado(
            input.codigo(),
            input.nome(),
            unidade,
            dayCounter,
            compounding,
            input.moeda(),
            input.pais(),
            input.classificacao(),
            input.classeAtivo(),
            input.dono(),
            SituacaoCurva.ATIVO,
            input.inicioVigencia(),
            input.fimVigencia(),
            now,
            now,
            null,
            null,
            null
        );

        CurvaMercado salva = repositoryPort.salvar(nova);

        publicarEvento(salva.codigo(), salva.nome(), "CRIACAO", null, salva);

        return new CurvaMercadoDetalhada(salva, List.of(), null, List.of());
    }

    @Override
    @Transactional
    public CurvaMercadoDetalhada alterar(String nome, CurvaMercadoInput input) {
        CurvaMercado atual = obterCurva(nome);
        List<CurvaProvedor> curvaProvedores = provedoresDe(atual.nome());

        List<Detalhe> detalhes = new ArrayList<>();
        validarCampos(input, false, atual, detalhes);

        if (!detalhes.isEmpty()) {
            throw new BusinessException(CadastroErrorCode.DADOS_INVALIDOS, detalhes.toArray());
        }

        // Se alterou o código, verifica unicidade (a curva legada pode estar sem código)
        String novoCodigo = input.codigo() != null ? input.codigo() : atual.codigo();
        if (!Objects.equals(novoCodigo, atual.codigo()) && repositoryPort.existsByCodigoDiferente(novoCodigo, atual.nome())) {
            throw new ConflictException(CadastroErrorCode.CODIGO_EM_USO);
        }

        Unidade unidade = parseEnum(Unidade.class, input.unidade());
        DayCounterCotacao dayCounter = parseEnum(DayCounterCotacao.class, input.dayCounterCotacao());
        CompoundingCotacao compounding = parseEnum(CompoundingCotacao.class, input.compounding());

        // 4.3 Coerência entre curva e configuração: rejeita se invalidar versão vigente ou futura
        if (configuracaoRepositoryPort != null) {
            LocalDate hoje = LocalDate.now();
            List<ConfiguracaoCurva> configs = configuracaoRepositoryPort.findByNomeCurva(atual.nome());
            List<CurvaProvedor> provList = curvaPrvdrRepositoryPort != null
                ? curvaPrvdrRepositoryPort.findByNomeCurva(atual.nome()) : List.of();
            for (ConfiguracaoCurva cfg : configs) {
                if (cfg.fimVigencia() == null || !cfg.fimVigencia().isBefore(hoje)) {
                    ValidadorParametros.ValidacaoResultado resVal = ValidadorParametros.validar(
                        cfg.modeloConstrucao(),
                        cfg.interpolador(),
                        cfg.parametros(),
                        unidade,
                        compounding,
                        provList
                    );
                    if (!resVal.isValido()) {
                        throw new BusinessException(CadastroErrorCode.DADOS_INVALIDOS,
                            new Object[]{ new Detalhe("unidade", null, unidade != null ? unidade.name() : null,
                                "Alteração da curva invalida a versão " + cfg.versao() + " de configuração: "
                                    + resVal.erros().getFirst().motivo()) });
                    }
                }
            }
        }

        CurvaMercado alterada = new CurvaMercado(
            novoCodigo,
            atual.nome(), // Nome é imutável
            unidade,
            dayCounter,
            compounding,
            input.moeda(),
            input.pais(),
            input.classificacao(),
            input.classeAtivo(),
            input.dono(),
            atual.situacao(),
            input.inicioVigencia(),
            input.fimVigencia(),
            atual.dataCriacao(),
            LocalDateTime.now(),
            atual.dataBaseReft(),
            atual.usuarioCalculo(),
            atual.usuarioAtualizacao()
        );

        CurvaMercado salva = repositoryPort.salvar(alterada);

        publicarEvento(salva.codigo(), salva.nome(), "ALTERACAO", atual, salva);

        return new CurvaMercadoDetalhada(salva, curvaProvedores, configuracaoVigenteDe(salva.nome()), List.of());
    }

    @Override
    @Transactional
    public CurvaMercadoDetalhada inativar(String nome) {
        CurvaMercado atual = obterCurva(nome);
        List<CurvaProvedor> curvaProvedores = provedoresDe(atual.nome());

        CurvaMercado inativada = new CurvaMercado(
            atual.codigo(),
            atual.nome(),
            atual.unidade(),
            atual.dayCounterCotacao(),
            atual.compounding(),
            atual.moeda(),
            atual.pais(),
            atual.classificacao(),
            atual.classeAtivo(),
            atual.dono(),
            SituacaoCurva.INATIVO,
            atual.inicioVigencia(),
            atual.fimVigencia(),
            atual.dataCriacao(),
            LocalDateTime.now(),
            atual.dataBaseReft(),
            atual.usuarioCalculo(),
            atual.usuarioAtualizacao()
        );

        CurvaMercado salva = repositoryPort.salvar(inativada);

        publicarEvento(salva.codigo(), salva.nome(), "INATIVACAO", atual, salva);

        return new CurvaMercadoDetalhada(salva, curvaProvedores, configuracaoVigenteDe(salva.nome()), List.of());
    }

    @Override
    @Transactional
    public CurvaMercadoDetalhada reativar(String nome) {
        CurvaMercado atual = obterCurva(nome);

        CurvaMercado reativada = new CurvaMercado(
            atual.codigo(),
            atual.nome(),
            atual.unidade(),
            atual.dayCounterCotacao(),
            atual.compounding(),
            atual.moeda(),
            atual.pais(),
            atual.classificacao(),
            atual.classeAtivo(),
            atual.dono(),
            SituacaoCurva.ATIVO,
            atual.inicioVigencia(),
            atual.fimVigencia(),
            atual.dataCriacao(),
            LocalDateTime.now(),
            atual.dataBaseReft(),
            atual.usuarioCalculo(),
            atual.usuarioAtualizacao()
        );

        CurvaMercado salva = repositoryPort.salvar(reativada);

        publicarEvento(salva.codigo(), salva.nome(), "REATIVACAO", atual, salva);

        return new CurvaMercadoDetalhada(salva, List.of(), null, List.of());
    }

    /**
     * Exclui a curva com os provedores e as configurações dela. Quem recusa quando ainda há dados dela em outra tabela
     * (construído, dado bruto) é o próprio banco, pelas chaves estrangeiras: tudo é desfeito e a mensagem diz o que fazer.
     */
    @Override
    @Transactional
    public void excluir(String nome) {
        CurvaMercado curva = obterCurva(nome);

        try {
            if (configuracaoRepositoryPort != null) {
                configuracaoRepositoryPort.findByNomeCurva(curva.nome())
                    .forEach(c -> configuracaoRepositoryPort.excluir(c.id()));
            }
            if (curvaPrvdrRepositoryPort != null) {
                curvaPrvdrRepositoryPort.findByNomeCurva(curva.nome())
                    .forEach(p -> curvaPrvdrRepositoryPort.excluir(p.idCurvaProvedor(), curva.nome()));
            }
            repositoryPort.excluir(curva.nome());   // o adaptador faz flush: a violação aparece aqui
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(CadastroErrorCode.CURVA_COM_HISTORICO, new Object[]{
                new Detalhe("nome", null, nome, "A curva ainda tem dados vinculados (vértices construídos ou dado bruto dos provedores). "
                    + "Apague as datas construídas e o dado bruto antes, ou use a inativação")
            });
        }

        publicarEvento(curva.codigo(), curva.nome(), "EXCLUSAO", curva, null);
    }

    private CurvaMercado obterCurva(String nome) {
        return repositoryPort.findByNome(nome)
            .orElseThrow(() -> new NotFoundException(CadastroErrorCode.NAO_ENCONTRADO.getCode(), "Curva " + nome + " não encontrada"));
    }

    private List<CurvaProvedor> provedoresDe(String nomeCurva) {
        return curvaPrvdrRepositoryPort != null ? curvaPrvdrRepositoryPort.findByNomeCurva(nomeCurva) : List.of();
    }

    private ConfiguracaoCurva configuracaoVigenteDe(String nomeCurva) {
        return configuracaoRepositoryPort != null
            ? configuracaoRepositoryPort.findVigente(nomeCurva, LocalDate.now()).orElse(null)
            : null;
    }

    private void validarCampos(CurvaMercadoInput input, boolean criacao, CurvaMercado atual, List<Detalhe> detalhes) {
        // Código
        if (input.codigo() == null || input.codigo().isBlank()) {
            detalhes.add(new Detalhe("codigo", null, input.codigo(), "Código é obrigatório"));
        } else if (!CODIGO_PATTERN.matcher(input.codigo()).matches()) {
            detalhes.add(new Detalhe("codigo", null, input.codigo(), "Código deve ter 1 a 50 caracteres contendo apenas A-Z, 0-9 e sublinhado"));
        }

        // Nome
        if (criacao) {
            if (input.nome() == null || input.nome().isBlank()) {
                detalhes.add(new Detalhe("nome", null, input.nome(), "Nome é obrigatório"));
            } else if (input.nome().length() > 50) {
                detalhes.add(new Detalhe("nome", null, input.nome(), "Nome deve ter até 50 caracteres"));
            }
        } else {
            if (input.nome() != null && !input.nome().equals(atual.nome())) {
                detalhes.add(new Detalhe("nome", null, input.nome(), "O nome é imutável"));
            }
        }

        // Unidade
        Unidade unidade = null;
        if (input.unidade() == null || input.unidade().isBlank()) {
            detalhes.add(new Detalhe("unidade", null, input.unidade(), "Unidade é obrigatória"));
        } else {
            try {
                unidade = Unidade.valueOf(input.unidade());
            } catch (IllegalArgumentException e) {
                detalhes.add(new Detalhe("unidade", null, input.unidade(), "Valores aceitos: [TAXA, PRECO, PONTOS]"));
            }
        }

        // DayCounter e Compounding
        if (unidade == Unidade.TAXA) {
            if (input.dayCounterCotacao() == null || input.dayCounterCotacao().isBlank()) {
                detalhes.add(new Detalhe("dayCounterCotacao", null, input.dayCounterCotacao(), "dayCounterCotacao é obrigatório para unidade TAXA"));
            } else {
                try {
                    DayCounterCotacao.valueOf(input.dayCounterCotacao());
                } catch (IllegalArgumentException e) {
                    detalhes.add(new Detalhe("dayCounterCotacao", null, input.dayCounterCotacao(), "Valores aceitos: [Business252, Actual360, Actual365Fixed, Thirty360]"));
                }
            }

            if (input.compounding() == null || input.compounding().isBlank()) {
                detalhes.add(new Detalhe("compounding", null, input.compounding(), "compounding é obrigatório para unidade TAXA"));
            } else {
                try {
                    CompoundingCotacao.valueOf(input.compounding());
                } catch (IllegalArgumentException e) {
                    detalhes.add(new Detalhe("compounding", null, input.compounding(), "Valores aceitos: [Simple, Compounded, Continuous]"));
                }
            }
        } else if (unidade != null) {
            if (input.dayCounterCotacao() != null && !input.dayCounterCotacao().isBlank()) {
                detalhes.add(new Detalhe("dayCounterCotacao", null, input.dayCounterCotacao(), "Não deve ser preenchido quando unidade não for TAXA"));
            }
            if (input.compounding() != null && !input.compounding().isBlank()) {
                detalhes.add(new Detalhe("compounding", null, input.compounding(), "Não deve ser preenchido quando unidade não for TAXA"));
            }
        }

        // Moeda
        if (input.moeda() == null || input.moeda().isBlank()) {
            detalhes.add(new Detalhe("moeda", null, input.moeda(), "Moeda é obrigatória"));
        } else {
            try {
                Currency.getInstance(input.moeda());
            } catch (IllegalArgumentException e) {
                detalhes.add(new Detalhe("moeda", null, input.moeda(), "Código de moeda ISO 4217 inválido"));
            }
        }

        // País
        if (input.pais() == null || input.pais().isBlank()) {
            detalhes.add(new Detalhe("pais", null, input.pais(), "País é obrigatório"));
        } else if (!PAISES_VALIDOS.contains(input.pais().toUpperCase())) {
            detalhes.add(new Detalhe("pais", null, input.pais(), "Código de país ISO 3166-1 alfa-2 inválido"));
        }

        // Início de vigência
        if (input.inicioVigencia() == null) {
            detalhes.add(new Detalhe("inicioVigencia", null, null, "Início de vigência é obrigatório"));
        }

        // Fim de vigência
        if (input.fimVigencia() != null && input.inicioVigencia() != null) {
            if (input.fimVigencia().isBefore(input.inicioVigencia())) {
                detalhes.add(new Detalhe("fimVigencia", null, input.fimVigencia().toString(), "Fim de vigência deve ser maior ou igual ao início"));
            }
        }

        // Classificação, classe de ativo e dono
        if (input.classificacao() != null && input.classificacao().length() > 50) {
            detalhes.add(new Detalhe("classificacao", null, input.classificacao(), "Classificação deve ter até 50 caracteres"));
        }
        if (input.classeAtivo() != null && input.classeAtivo().length() > 50) {
            detalhes.add(new Detalhe("classeAtivo", null, input.classeAtivo(), "Classe de ativo deve ter até 50 caracteres"));
        }
        if (input.dono() != null && input.dono().length() > 50) {
            detalhes.add(new Detalhe("dono", null, input.dono(), "Dono deve ter até 50 caracteres"));
        }
    }

    private <E extends Enum<E>> E parseEnum(Class<E> enumClass, String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(enumClass, valor);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private void publicarEvento(String codigo, String nome, String operacao, Object anterior, Object novo) {
        eventosPort.publicarCadastroAlterado(new EventoCadastroAlterado(
            UUID.randomUUID(),
            codigo,
            nome,
            "CURVA",
            operacao,
            OffsetDateTime.now(),
            null,
            anterior,
            novo
        ));
    }
}
