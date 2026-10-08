package br.com.poc.application.service;

import br.com.poc.application.exception.BusinessException;
import br.com.poc.application.exception.CadastroErrorCode;
import br.com.poc.application.exception.NotFoundException;
import br.com.poc.application.port.in.usecase.ConfiguracaoCurvaUseCase;
import br.com.poc.application.port.out.ConfiguracaoCurvaRepositoryPort;
import br.com.poc.application.port.out.CurvaMercdRepositoryPort;
import br.com.poc.application.port.out.CurvaPrvdrRepositoryPort;
import br.com.poc.application.port.out.DadosConstruidosPort;
import br.com.poc.application.port.out.EventosPort;
import br.com.poc.domain.aviso.AvisoCurva;
import br.com.poc.domain.aviso.CodigoAvisoCurva;
import br.com.poc.domain.aviso.Detalhe;
import br.com.poc.domain.cadastro.*;
import br.com.poc.domain.evento.EventoCadastroAlterado;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ConfiguracaoCurvaService implements ConfiguracaoCurvaUseCase {

    /** Fim de janela para versão sem fimVigencia (aberta). */
    private static final LocalDate SEM_FIM = LocalDate.of(9999, 12, 31);

    private final CurvaMercdRepositoryPort curvaRepositoryPort;
    private final CurvaPrvdrRepositoryPort curvaPrvdrRepositoryPort;
    private final ConfiguracaoCurvaRepositoryPort configuracaoRepositoryPort;
    private final EventosPort eventosPort;
    private final DadosConstruidosPort dadosConstruidosPort;

    @Override
    @Transactional(readOnly = true)
    public List<ConfiguracaoCurva> listarPorCurva(String codigoCurva) {
        CurvaMercado curva = obterCurva(codigoCurva);
        return configuracaoRepositoryPort.findByNomeCurva(curva.nome());
    }

    @Override
    @Transactional(readOnly = true)
    public ConfiguracaoCurva consultarVigente(String codigoCurva, LocalDate data) {
        CurvaMercado curva = obterCurva(codigoCurva);
        LocalDate dataConsulta = data != null ? data : LocalDate.now();
        return configuracaoRepositoryPort.findVigente(curva.nome(), dataConsulta)
            .orElseThrow(() -> new NotFoundException(CadastroErrorCode.NAO_ENCONTRADO.getCode(),
                "Nenhuma configuração vigente para a curva " + codigoCurva + " na data " + dataConsulta));
    }

    @Override
    @Transactional(readOnly = true)
    public List<AvisoCurva> validar(String codigoCurva, CriarConfiguracaoCurvaInput input) {
        CurvaMercado curva = obterCurva(codigoCurva);
        ValidadorParametros.ValidacaoResultado res = validarEntrada(curva, input);
        return res.avisos();
    }

    @Override
    @Transactional
    public ConfiguracaoCurvaResultado criar(String codigoCurva, CriarConfiguracaoCurvaInput input) {
        CurvaMercado curva = obterCurva(codigoCurva);

        List<Detalhe> erros = new ArrayList<>();
        ValidadorParametros.ValidacaoResultado res = validarSemLancar(curva, input, erros);

        LocalDate hoje = LocalDate.now();
        Optional<ConfiguracaoCurva> ultimaOpt = configuracaoRepositoryPort.findUltimaVersao(curva.nome());
        boolean temInicio = input.inicioVigencia() != null;

        int proximaVersao;
        if (ultimaOpt.isPresent()) {
            ConfiguracaoCurva ultima = ultimaOpt.get();
            proximaVersao = ultima.versao() + 1;
            if (temInicio && !input.inicioVigencia().isAfter(ultima.inicioVigencia())) {
                erros.add(new Detalhe("inicioVigencia", null, input.inicioVigencia().toString(),
                    "Início de vigência da nova versão deve ser posterior ao da versão " + ultima.versao() + " (" + ultima.inicioVigencia() + ")"));
            }
            if (temInicio && input.inicioVigencia().isBefore(hoje)) {
                erros.add(new Detalhe("inicioVigencia", null, input.inicioVigencia().toString(),
                    "Início de vigência não pode ser no passado (deve ser maior ou igual a hoje " + hoje + ")"));
            }
        } else {
            proximaVersao = 1;
            if (temInicio && curva.inicioVigencia() != null && input.inicioVigencia().isBefore(curva.inicioVigencia())) {
                erros.add(new Detalhe("inicioVigencia", null, input.inicioVigencia().toString(),
                    "Início de vigência não pode ser anterior ao início de vigência da curva (" + curva.inicioVigencia() + ")"));
            }
        }

        if (!erros.isEmpty()) {
            throw new BusinessException(CadastroErrorCode.DADOS_INVALIDOS, erros.toArray());
        }

        // Se houver versão anterior, fecha com data fim = início da nova - 1 dia
        ultimaOpt.ifPresent(anterior ->
            configuracaoRepositoryPort.salvar(anterior.comFimVigencia(Objects.requireNonNull(input.inicioVigencia()).minusDays(1))));

        ConfiguracaoCurva nova = new ConfiguracaoCurva(
            null,
            curva.nome(),
            proximaVersao,
            input.modeloConstrucao(),
            input.interpolador(),
            res.parametrosNormalizados(),
            input.inicioVigencia(),
            null
        );
        ConfiguracaoCurva salva = configuracaoRepositoryPort.salvar(nova);

        publicarEvento(curva.codigo(), curva.nome(), "CRIACAO", null, salva);

        return new ConfiguracaoCurvaResultado(salva, res.avisos());
    }

    /**
     * Exclui a versão informada (sem versão: a vigente hoje). Recusa se há curva construída na vigência dela.
     * A vizinha cobre a vigência da excluída: a anterior estende o fim; sem anterior, a seguinte antecipa o início.
     */
    @Override
    @Transactional
    public ConfiguracaoCurvaResultado excluir(String codigoCurva, Integer versao) {
        CurvaMercado curva = obterCurva(codigoCurva);

        List<ConfiguracaoCurva> versoes = configuracaoRepositoryPort.findByNomeCurva(curva.nome()).stream()
            .sorted(Comparator.comparing(ConfiguracaoCurva::versao))
            .toList();
        ConfiguracaoCurva alvo = escolherVersao(codigoCurva, versoes, versao);

        ResumoConstrucao construcao = dadosConstruidosPort.resumo(
            curva.nome(), alvo.inicioVigencia(), alvo.fimVigencia() != null ? alvo.fimVigencia() : SEM_FIM);
        if (construcao.datas() > 0) {
            throw new BusinessException(CadastroErrorCode.VERSAO_EM_USO, new Object[]{
                new Detalhe("versao", null, String.valueOf(alvo.versao()),
                    construcao.datas() + " data(s) construída(s) na vigência da versão, de " + construcao.primeira()
                        + " a " + construcao.ultima() + "; apague-as antes de excluir a versão")
            });
        }

        int posicao = versoes.indexOf(alvo);
        Optional<ConfiguracaoCurva> anterior = posicao > 0 ? Optional.of(versoes.get(posicao - 1)) : Optional.empty();
        Optional<ConfiguracaoCurva> seguinte = posicao < versoes.size() - 1 ? Optional.of(versoes.get(posicao + 1)) : Optional.empty();

        configuracaoRepositoryPort.excluir(alvo.id());

        anterior.ifPresentOrElse(
            a -> configuracaoRepositoryPort.salvar(a.comFimVigencia(alvo.fimVigencia())),
            () -> seguinte.ifPresent(s -> configuracaoRepositoryPort.salvar(s.comInicioVigencia(alvo.inicioVigencia()))));

        publicarEvento(curva.codigo(), curva.nome(), "EXCLUSAO", alvo, null);

        List<AvisoCurva> avisos = versoes.size() == 1
            ? List.of(new AvisoCurva(CodigoAvisoCurva.SEM_CONFIGURACAO,
                "A curva ficou sem configuração e não constrói até ganhar uma nova versão", List.of()))
            : List.of();
        return new ConfiguracaoCurvaResultado(null, avisos);
    }

    private ConfiguracaoCurva escolherVersao(String codigoCurva, List<ConfiguracaoCurva> versoes, Integer versao) {
        if (versao == null) {
            LocalDate hoje = LocalDate.now();
            return versoes.stream()
                .filter(v -> !v.inicioVigencia().isAfter(hoje) && (v.fimVigencia() == null || !v.fimVigencia().isBefore(hoje)))
                .findFirst()
                .orElseThrow(() -> new NotFoundException(CadastroErrorCode.NAO_ENCONTRADO.getCode(),
                    "Nenhuma configuração vigente para a curva " + codigoCurva));
        }
        return versoes.stream()
            .filter(v -> v.versao().equals(versao))
            .findFirst()
            .orElseThrow(() -> new NotFoundException(CadastroErrorCode.NAO_ENCONTRADO.getCode(),
                "Versão " + versao + " não encontrada para a curva " + codigoCurva));
    }

    private ValidadorParametros.ValidacaoResultado validarEntrada(CurvaMercado curva, CriarConfiguracaoCurvaInput input) {
        List<Detalhe> erros = new ArrayList<>();
        ValidadorParametros.ValidacaoResultado res = validarSemLancar(curva, input, erros);
        if (!erros.isEmpty()) {
            throw new BusinessException(CadastroErrorCode.DADOS_INVALIDOS, erros.toArray());
        }
        return res;
    }

    private ValidadorParametros.ValidacaoResultado validarSemLancar(CurvaMercado curva, CriarConfiguracaoCurvaInput input, List<Detalhe> erros) {
        validarCamposBasicos(input, erros);
        ValidadorParametros.ValidacaoResultado res = ValidadorParametros.validar(
            input.modeloConstrucao(),
            input.interpolador(),
            input.parametros(),
            curva.unidade(),
            curva.compounding(),
            obterProvedores(curva.nome())
        );
        erros.addAll(res.erros());
        return res;
    }

    private CurvaMercado obterCurva(String codigoCurva) {
        return curvaRepositoryPort.findByCodigo(codigoCurva)
            .orElseThrow(() -> new NotFoundException(CadastroErrorCode.NAO_ENCONTRADO.getCode(), "Curva " + codigoCurva + " não encontrada"));
    }

    private List<CurvaProvedor> obterProvedores(String nomeCurva) {
        return curvaPrvdrRepositoryPort != null ? curvaPrvdrRepositoryPort.findByNomeCurva(nomeCurva) : List.of();
    }

    private void validarCamposBasicos(CriarConfiguracaoCurvaInput input, List<Detalhe> erros) {
        if (input.modeloConstrucao() == null || input.modeloConstrucao().isBlank()) {
            erros.add(new Detalhe("modeloConstrucao", null, input.modeloConstrucao(), "modeloConstrucao é obrigatório"));
        } else if (input.modeloConstrucao().length() > 100) {
            erros.add(new Detalhe("modeloConstrucao", null, input.modeloConstrucao(), "modeloConstrucao deve ter até 100 caracteres"));
        }

        if (input.interpolador() == null || input.interpolador().isBlank()) {
            erros.add(new Detalhe("interpolador", null, input.interpolador(), "interpolador é obrigatório"));
        } else if (input.interpolador().length() > 100) {
            erros.add(new Detalhe("interpolador", null, input.interpolador(), "interpolador deve ter até 100 caracteres"));
        }

        if (input.inicioVigencia() == null) {
            erros.add(new Detalhe("inicioVigencia", null, null, "inicioVigencia é obrigatório"));
        }
    }

    private void publicarEvento(String codigo, String nome, String operacao, Object anterior, Object novo) {
        eventosPort.publicarCadastroAlterado(new EventoCadastroAlterado(
            UUID.randomUUID(),
            codigo,
            nome,
            "CONFIGURACAO",
            operacao,
            OffsetDateTime.now(),
            null,
            anterior,
            novo
        ));
    }
}
