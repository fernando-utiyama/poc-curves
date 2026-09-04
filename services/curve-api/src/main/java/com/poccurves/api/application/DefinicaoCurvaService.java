package com.poccurves.api.application;

import com.poccurves.api.domain.DefinicaoCurva;
import com.poccurves.api.domain.EstadoDefinicaoCurva;
import com.poccurves.api.domain.ModoOrigem;
import com.poccurves.api.domain.VersaoDefinicaoCurva;
import com.poccurves.api.dto.ApiDtos.*;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

/**
 * Caso de uso puro (sem anotação de framework de infraestrutura). `@Transactional`
 * é a única exceção deliberada à regra de fronteira (ver
 * openspec/changes/hexagonal-architecture/design.md - Decisions): demarcação de
 * transação é tratada como concern de caso de uso, não de infraestrutura.
 */
public class DefinicaoCurvaService {

    private final DefinicaoCurvaRepositoryPort definicaoRepo;
    private final VersaoDefinicaoCurvaRepositoryPort versaoRepo;
    private final ModeloCurvaRepositoryPort modeloRepo;
    private final DefinicaoCoerenciaValidator validador;
    private final com.poccurves.api.domain.ModeloCargaService modeloCargaService;
    private final JsonPort jsonPort;

    public DefinicaoCurvaService(
            DefinicaoCurvaRepositoryPort definicaoRepo,
            VersaoDefinicaoCurvaRepositoryPort versaoRepo,
            ModeloCurvaRepositoryPort modeloRepo,
            DefinicaoCoerenciaValidator validador,
            com.poccurves.api.domain.ModeloCargaService modeloCargaService,
            JsonPort jsonPort
    ) {
        this.definicaoRepo = definicaoRepo;
        this.versaoRepo = versaoRepo;
        this.modeloRepo = modeloRepo;
        this.validador = validador;
        this.modeloCargaService = modeloCargaService;
        this.jsonPort = jsonPort;
    }

    @Transactional
    public DefinicaoCurvaResponse criarDefinicao(CriarDefinicaoRequest req, String criadoPor) {
        if (req.codigo() == null || req.codigo().isBlank()) {
            throw new IllegalArgumentException("Código da curva é obrigatório.");
        }
        String codigo = req.codigo().trim().toUpperCase();

        if (definicaoRepo.existeCodigo(codigo)) {
            throw new IllegalStateException("Já existe uma definição de curva cadastrada com o código '" + codigo + "'.");
        }

        ModoOrigem modoOrigem = ModoOrigem.valueOf(req.modoOrigem());
        EstadoDefinicaoCurva estado = req.estado() != null ? EstadoDefinicaoCurva.valueOf(req.estado()) : EstadoDefinicaoCurva.ATIVA;
        LocalTime horarioLimite = req.horarioLimitePublicacao() != null
                ? LocalTime.parse(req.horarioLimitePublicacao().length() == 5 ? req.horarioLimitePublicacao() + ":00" : req.horarioLimitePublicacao())
                : LocalTime.of(19, 0);

        // Valida coerência
        validador.validarCoerencia(
                codigo,
                modoOrigem,
                req.interpolador(),
                req.politicaExtrapolacao(),
                req.politicaArredondamento(),
                req.modeloApontadoId(),
                req.modeloApontadoCodigo(),
                req.vinculosFonte(),
                req.dependeDe(),
                req.limitesValidacao()
        );

        // Resolução do Modelo
        UUID modeloId = req.modeloApontadoId();
        if (modeloId == null && req.modeloApontadoCodigo() != null && !req.modeloApontadoCodigo().isBlank()) {
            modeloId = modeloRepo.buscarPorCodigo(req.modeloApontadoCodigo())
                    .map(ModeloCurvaRepositoryPort.ModeloCurvaRegistro::id)
                    .orElse(null);
        }
        if (modoOrigem == ModoOrigem.BOOTSTRAPPED && modeloId == null) {
            // Aplica modelo padrão embutido
            modeloId = modeloRepo.buscarPorCodigo("BUILTIN_PRE_DI1")
                    .map(ModeloCurvaRepositoryPort.ModeloCurvaRegistro::id)
                    .orElse(null);
        }

        DefinicaoCurva definicao = DefinicaoCurva.criar(
                codigo,
                req.nome(),
                req.moeda(),
                modoOrigem,
                horarioLimite,
                criadoPor != null ? criadoPor : "operador"
        );
        if (estado == EstadoDefinicaoCurva.ATIVA) {
            definicao.ativar();
        } else if (estado == EstadoDefinicaoCurva.APOSENTADA) {
            definicao.ativar();
            definicao.aposentar();
        }
        definicaoRepo.salvar(definicao);

        LocalDate vigInicio = req.vigenciaInicio() != null ? req.vigenciaInicio() : LocalDate.now();

        String vinculosJson = jsonPort.toJson(req.vinculosFonte());
        String dependeDeJson = jsonPort.toJson(req.dependeDe());
        String limitesJson = jsonPort.toJson(req.limitesValidacao());

        VersaoDefinicaoCurva versao = VersaoDefinicaoCurva.primeiraVersao(
                definicao.id(),
                req.contagemDias(),
                req.calendario(),
                req.interpolador(),
                req.politicaExtrapolacao(),
                req.politicaArredondamento(),
                modeloId,
                req.orcamentoIngestaoSegundos(),
                req.orcamentoConstrucaoSegundos(),
                req.orcamentoValidacaoSegundos(),
                req.orcamentoPublicacaoSegundos(),
                req.janelaBloqueioMinutos(),
                vinculosJson,
                dependeDeJson,
                limitesJson,
                vigInicio
        );
        versaoRepo.salvar(versao);

        return montarResponse(definicao, versao, null);
    }

    @Transactional
    public DefinicaoCurvaResponse atualizarDefinicao(String codigo, AtualizarDefinicaoRequest req) {
        String cod = codigo.trim().toUpperCase();
        DefinicaoCurva definicao = definicaoRepo.buscarPorCodigo(cod)
                .orElseThrow(() -> new NoSuchElementException("Definição de curva não encontrada: " + cod));

        ModoOrigem modoOrigem = ModoOrigem.valueOf(req.modoOrigem());
        LocalTime horarioLimite = req.horarioLimitePublicacao() != null
                ? LocalTime.parse(req.horarioLimitePublicacao().length() == 5 ? req.horarioLimitePublicacao() + ":00" : req.horarioLimitePublicacao())
                : definicao.horarioLimitePublicacao();

        // Valida coerência
        validador.validarCoerencia(
                cod,
                modoOrigem,
                req.interpolador(),
                req.politicaExtrapolacao(),
                req.politicaArredondamento(),
                req.modeloApontadoId(),
                req.modeloApontadoCodigo(),
                req.vinculosFonte(),
                req.dependeDe(),
                req.limitesValidacao()
        );

        // Atualiza nome/estado da definicao base
        definicao.renomear(req.nome());
        if (req.estado() != null) {
            EstadoDefinicaoCurva novoEstado = EstadoDefinicaoCurva.valueOf(req.estado());
            if (novoEstado == EstadoDefinicaoCurva.ATIVA && definicao.estado() == EstadoDefinicaoCurva.RASCUNHO) {
                definicao.ativar();
            } else if (novoEstado == EstadoDefinicaoCurva.APOSENTADA && definicao.estado() == EstadoDefinicaoCurva.ATIVA) {
                definicao.aposentar();
            }
        }
        definicaoRepo.atualizar(definicao);

        // Busca última versão para encerrar vigência e criar sucessora
        VersaoDefinicaoCurva versaoAnterior = versaoRepo.buscarMaisRecente(definicao.id())
                .orElseThrow(() -> new IllegalStateException("Nenhuma versão encontrada para a definição: " + cod));

        LocalDate vigInicio = req.vigenciaInicio() != null ? req.vigenciaInicio() : LocalDate.now();

        UUID modeloId = req.modeloApontadoId();
        if (modeloId == null && req.modeloApontadoCodigo() != null && !req.modeloApontadoCodigo().isBlank()) {
            modeloId = modeloRepo.buscarPorCodigo(req.modeloApontadoCodigo())
                    .map(ModeloCurvaRepositoryPort.ModeloCurvaRegistro::id)
                    .orElse(null);
        }

        String vinculosJson = jsonPort.toJson(req.vinculosFonte());
        String dependeDeJson = jsonPort.toJson(req.dependeDe());
        String limitesJson = jsonPort.toJson(req.limitesValidacao());

        VersaoDefinicaoCurva novaVersao = VersaoDefinicaoCurva.proximaVersao(
                versaoAnterior,
                req.contagemDias(),
                req.calendario(),
                req.interpolador(),
                req.politicaExtrapolacao(),
                req.politicaArredondamento(),
                modeloId,
                req.orcamentoIngestaoSegundos(),
                req.orcamentoConstrucaoSegundos(),
                req.orcamentoValidacaoSegundos(),
                req.orcamentoPublicacaoSegundos(),
                req.janelaBloqueioMinutos(),
                vinculosJson,
                dependeDeJson,
                limitesJson,
                vigInicio
        );

        // Persiste encerramento da anterior e inserção da nova
        versaoRepo.encerrarVigencia(versaoAnterior.id(), vigInicio);
        versaoRepo.salvar(novaVersao);

        return montarResponse(definicao, novaVersao, versaoAnterior.numeroVersao());
    }

    public DefinicaoCurvaResponse obterDefinicao(String codigo, Integer numeroVersao) {
        String cod = codigo.trim().toUpperCase();
        DefinicaoCurva definicao = definicaoRepo.buscarPorCodigo(cod)
                .orElseThrow(() -> new NoSuchElementException("Definição de curva não encontrada: " + cod));

        VersaoDefinicaoCurva versao;
        if (numeroVersao != null) {
            versao = versaoRepo.buscarPorNumero(definicao.id(), numeroVersao)
                    .orElseThrow(() -> new NoSuchElementException("Versão " + numeroVersao + " não encontrada para a curva: " + cod));
        } else {
            versao = versaoRepo.buscarVigente(definicao.id(), LocalDate.now())
                    .or(() -> versaoRepo.buscarMaisRecente(definicao.id()))
                    .orElseThrow(() -> new NoSuchElementException("Nenhuma versão disponível para a curva: " + cod));
        }

        return montarResponse(definicao, versao, null);
    }

    public CatalogoDefinicoesResponse listarDefinicoes(
            String codigo,
            String nome,
            String moeda,
            String modoOrigemStr,
            String estadoStr,
            int pagina,
            int tamanhoPagina
    ) {
        ModoOrigem modoOrigem = modoOrigemStr != null && !modoOrigemStr.isBlank() ? ModoOrigem.valueOf(modoOrigemStr) : null;
        EstadoDefinicaoCurva estado = estadoStr != null && !estadoStr.isBlank() ? EstadoDefinicaoCurva.valueOf(estadoStr) : null;

        int total = definicaoRepo.contar(codigo, nome, moeda, modoOrigem, estado);
        int offset = pagina * tamanhoPagina;
        List<DefinicaoCurva> definicoes = definicaoRepo.listar(codigo, nome, moeda, modoOrigem, estado, offset, tamanhoPagina);

        List<ItemCatalogoDefinicao> itens = new ArrayList<>();
        for (DefinicaoCurva d : definicoes) {
            var versaoOpt = versaoRepo.buscarMaisRecente(d.id());
            int versaoNum = versaoOpt.map(VersaoDefinicaoCurva::numeroVersao).orElse(1);
            String modeloNome = null;
            if (versaoOpt.isPresent() && versaoOpt.get().modeloCurvaId() != null) {
                modeloNome = modeloRepo.buscarPorId(versaoOpt.get().modeloCurvaId())
                        .map(ModeloCurvaRepositoryPort.ModeloCurvaRegistro::nome)
                        .orElse(null);
            }
            itens.add(new ItemCatalogoDefinicao(
                    d.id(),
                    d.codigo(),
                    d.nome(),
                    d.moeda(),
                    d.modoOrigem().name(),
                    d.estado().name(),
                    versaoNum,
                    modeloNome
            ));
        }

        int totalPaginas = (int) Math.ceil((double) total / tamanhoPagina);
        return new CatalogoDefinicoesResponse(itens, total, pagina, totalPaginas);
    }

    public HistoricoVersoesDefinicaoResponse listarHistoricoVersoes(String codigo) {
        String cod = codigo.trim().toUpperCase();
        DefinicaoCurva definicao = definicaoRepo.buscarPorCodigo(cod)
                .orElseThrow(() -> new NoSuchElementException("Definição de curva não encontrada: " + cod));

        List<VersaoDefinicaoCurva> versoes = versaoRepo.listarPorDefinicao(definicao.id());
        List<ItemHistoricoVersaoDefinicao> itens = new ArrayList<>();

        for (VersaoDefinicaoCurva v : versoes) {
            String modeloCodigo = null;
            if (v.modeloCurvaId() != null) {
                modeloCodigo = modeloRepo.buscarPorId(v.modeloCurvaId())
                        .map(ModeloCurvaRepositoryPort.ModeloCurvaRegistro::codigo)
                        .orElse(null);
            }
            itens.add(new ItemHistoricoVersaoDefinicao(
                    v.id(),
                    v.numeroVersao(),
                    v.interpolador(),
                    modeloCodigo,
                    v.vigenciaInicio(),
                    v.vigenciaFim()
            ));
        }
        return new HistoricoVersoesDefinicaoResponse(cod, itens);
    }

    public String gerarModeloCargaCsv(String codigo) {
        String cod = codigo.trim().toUpperCase();
        DefinicaoCurva definicao = definicaoRepo.buscarPorCodigo(cod)
                .orElseThrow(() -> new NoSuchElementException("Definição de curva não encontrada: " + cod));
        VersaoDefinicaoCurva versao = versaoRepo.buscarMaisRecente(definicao.id())
                .orElseThrow(() -> new NoSuchElementException("Versão não encontrada para a curva: " + cod));

        return modeloCargaService.gerarModeloCsv(definicao, versao);
    }

    public byte[] gerarModeloCargaXlsx(String codigo) {
        String cod = codigo.trim().toUpperCase();
        DefinicaoCurva definicao = definicaoRepo.buscarPorCodigo(cod)
                .orElseThrow(() -> new NoSuchElementException("Definição de curva não encontrada: " + cod));
        VersaoDefinicaoCurva versao = versaoRepo.buscarMaisRecente(definicao.id())
                .orElseThrow(() -> new NoSuchElementException("Versão não encontrada para a curva: " + cod));

        return modeloCargaService.gerarModeloXlsx(definicao, versao);
    }

    private DefinicaoCurvaResponse montarResponse(DefinicaoCurva d, VersaoDefinicaoCurva v, Integer versaoOrigemNumero) {
        String modeloCodigo = null;
        if (v.modeloCurvaId() != null) {
            modeloCodigo = modeloRepo.buscarPorId(v.modeloCurvaId())
                    .map(ModeloCurvaRepositoryPort.ModeloCurvaRegistro::codigo)
                    .orElse(null);
        }

        List<String> vinculos = jsonPort.paraListaDeString(v.vinculosFonteJson());
        List<String> dependencias = jsonPort.paraListaDeString(v.dependeDeJson());
        List<LimiteValidacaoDTO> limites = jsonPort.paraListaDeLimites(v.limitesValidacaoJson());

        return new DefinicaoCurvaResponse(
                d.id(),
                d.codigo(),
                d.nome(),
                d.moeda(),
                d.modoOrigem().name(),
                d.estado().name(),
                d.horarioLimitePublicacao().toString(),
                v.id(),
                v.numeroVersao(),
                versaoOrigemNumero,
                v.contagemDias(),
                v.calendario(),
                v.interpolador(),
                v.politicaExtrapolacao(),
                v.politicaArredondamento(),
                modeloCodigo,
                v.orcamentoIngestaoSegundos(),
                v.orcamentoConstrucaoSegundos(),
                v.orcamentoValidacaoSegundos(),
                v.orcamentoPublicacaoSegundos(),
                v.janelaBloqueioMinutos(),
                vinculos != null ? vinculos : Collections.emptyList(),
                dependencias != null ? dependencias : Collections.emptyList(),
                limites != null ? limites : Collections.emptyList(),
                v.vigenciaInicio(),
                v.vigenciaFim()
        );
    }
}
