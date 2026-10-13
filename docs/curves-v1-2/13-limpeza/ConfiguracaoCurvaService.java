package br.com.poc.application.service;

import br.com.poc.application.exception.BusinessException;
import br.com.poc.application.exception.CadastroErrorCode;
import br.com.poc.application.exception.NotFoundException;
import br.com.poc.application.port.in.usecase.ConfiguracaoCurvaUseCase;
import br.com.poc.application.port.out.ConfiguracaoCurvaRepositoryPort;
import br.com.poc.application.port.out.CurvaMercdRepositoryPort;
import br.com.poc.application.port.out.DadoVertcCurvaRepositoryPort;
import br.com.poc.application.port.out.EventosPort;
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

    private static final int TAMANHO_MAXIMO_TEXTO = 100;

    /** Fim de janela para versão sem fimVigencia (aberta). */
    private static final LocalDate SEM_FIM = LocalDate.of(9999, 12, 31);

    private final CurvaMercdRepositoryPort curvaRepositoryPort;
    private final ConfiguracaoCurvaRepositoryPort configuracaoRepositoryPort;
    private final EventosPort eventosPort;
    private final DadoVertcCurvaRepositoryPort dadoVertcCurvaRepositoryPort;

    @Override
    @Transactional(readOnly = true)
    public List<ConfiguracaoCurva> listarPorCurva(String nomeCurva) {
        CurvaMercado curva = obterCurva(nomeCurva);
        return configuracaoRepositoryPort.findByNomeCurva(curva.nome());
    }

    @Override
    @Transactional(readOnly = true)
    public ConfiguracaoCurva consultarVigente(String nomeCurva, LocalDate data) {
        CurvaMercado curva = obterCurva(nomeCurva);
        LocalDate dataConsulta = data != null ? data : LocalDate.now();
        return configuracaoRepositoryPort.findVigente(curva.nome(), dataConsulta)
            .orElseThrow(() -> new NotFoundException(CadastroErrorCode.NAO_ENCONTRADO.getCode(),
                "Nenhuma configuração vigente para a curva " + nomeCurva + " na data " + dataConsulta));
    }

    @Override
    @Transactional(readOnly = true)
    public void validar(String nomeCurva, CriarConfiguracaoCurvaInput input) {
        validarEntrada(obterCurva(nomeCurva), input);
    }

    @Override
    @Transactional
    public ConfiguracaoCurva criar(String nomeCurva, CriarConfiguracaoCurvaInput input) {
        CurvaMercado curva = obterCurva(nomeCurva);

        List<Detalhe> erros = new ArrayList<>();
        ValidadorParametros.ValidacaoResultado res = validarSemLancar(curva, input, erros);

        Optional<ConfiguracaoCurva> ultimaOpt = configuracaoRepositoryPort.findUltimaVersao(curva.nome());
        validarInicioDaVersao(curva, ultimaOpt.orElse(null), input.inicioVigencia(), erros);
        int proximaVersao = ultimaOpt.map(ultima -> ultima.versao() + 1).orElse(1);

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

        return salva;
    }

    /** Exclui a versão (sem versão: a vigente hoje); a vizinha cobre a vigência da excluída. */
    @Override
    @Transactional
    public void excluir(String nomeCurva, Integer versao) {
        CurvaMercado curva = obterCurva(nomeCurva);

        List<ConfiguracaoCurva> versoes = configuracaoRepositoryPort.findByNomeCurva(curva.nome()).stream()
            .sorted(Comparator.comparing(ConfiguracaoCurva::versao))
            .toList();
        ConfiguracaoCurva alvo = escolherVersao(nomeCurva, versoes, versao);

        LocalDate fim = alvo.fimVigencia() != null ? alvo.fimVigencia() : SEM_FIM;
        if (dadoVertcCurvaRepositoryPort.existeVerticePorNomeCurvaEPeriodo(curva.nome(), alvo.inicioVigencia(), fim)) {
            throw new BusinessException(CadastroErrorCode.VERSAO_EM_USO, new Object[]{
                new Detalhe("versao", null, String.valueOf(alvo.versao()),
                    "Há curva construída na vigência da versão " + alvo.versao() + " (de " + alvo.inicioVigencia()
                        + (alvo.fimVigencia() != null ? " a " + alvo.fimVigencia() : " em diante")
                        + "). Apague as datas construídas antes de excluir a versão")
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
    }

    private ConfiguracaoCurva escolherVersao(String nomeCurva, List<ConfiguracaoCurva> versoes, Integer versao) {
        if (versao == null) {
            LocalDate hoje = LocalDate.now();
            return versoes.stream()
                .filter(v -> !v.inicioVigencia().isAfter(hoje) && (v.fimVigencia() == null || !v.fimVigencia().isBefore(hoje)))
                .findFirst()
                .orElseThrow(() -> new NotFoundException(CadastroErrorCode.NAO_ENCONTRADO.getCode(),
                    "Nenhuma configuração vigente para a curva " + nomeCurva));
        }
        return versoes.stream()
            .filter(v -> v.versao().equals(versao))
            .findFirst()
            .orElseThrow(() -> new NotFoundException(CadastroErrorCode.NAO_ENCONTRADO.getCode(),
                "Versão " + versao + " não encontrada para a curva " + nomeCurva));
    }

    private void validarEntrada(CurvaMercado curva, CriarConfiguracaoCurvaInput input) {
        List<Detalhe> erros = new ArrayList<>();
        validarSemLancar(curva, input, erros);
        validarInicioDaVersao(curva, configuracaoRepositoryPort.findUltimaVersao(curva.nome()).orElse(null), input.inicioVigencia(), erros);
        if (!erros.isEmpty()) {
            throw new BusinessException(CadastroErrorCode.DADOS_INVALIDOS, erros.toArray());
        }
    }

    /** Valida o início de vigência da nova versão. */
    private void validarInicioDaVersao(CurvaMercado curva, ConfiguracaoCurva ultima, LocalDate inicio, List<Detalhe> erros) {
        if (inicio == null) {
            return;   // a ausência já foi apontada em validarCamposObrigatorios
        }
        if (ultima != null) {
            if (!inicio.isAfter(ultima.inicioVigencia())) {
                erros.add(new Detalhe("inicioVigencia", null, inicio.toString(),
                    "Início de vigência da nova versão deve ser posterior ao da versão " + ultima.versao() + " (" + ultima.inicioVigencia() + ")"));
            }
            LocalDate hoje = LocalDate.now();
            if (inicio.isBefore(hoje)) {
                erros.add(new Detalhe("inicioVigencia", null, inicio.toString(),
                    "Início de vigência não pode ser no passado (deve ser maior ou igual a hoje " + hoje + ")"));
            }
        } else if (curva.inicioVigencia() != null && inicio.isBefore(curva.inicioVigencia())) {
            erros.add(new Detalhe("inicioVigencia", null, inicio.toString(),
                "Início de vigência não pode ser anterior ao início de vigência da curva (" + curva.inicioVigencia() + ")"));
        }
    }

    private ValidadorParametros.ValidacaoResultado validarSemLancar(CurvaMercado curva, CriarConfiguracaoCurvaInput input, List<Detalhe> erros) {
        validarCamposObrigatorios(input, erros);
        ValidadorParametros.ValidacaoResultado res = ValidadorParametros.validar(
            input.interpolador(),
            input.parametros(),
            curva.unidade(),
            curva.compounding()
        );
        erros.addAll(res.erros());
        return res;
    }

    private CurvaMercado obterCurva(String nomeCurva) {
        return curvaRepositoryPort.findByNome(nomeCurva)
            .orElseThrow(() -> new NotFoundException(CadastroErrorCode.NAO_ENCONTRADO.getCode(), "Curva " + nomeCurva + " não encontrada"));
    }

    private void validarCamposObrigatorios(CriarConfiguracaoCurvaInput input, List<Detalhe> erros) {
        validarTexto("modeloConstrucao", input.modeloConstrucao(), erros);
        validarTexto("interpolador", input.interpolador(), erros);
        if (input.inicioVigencia() == null) {
            erros.add(new Detalhe("inicioVigencia", null, null, "inicioVigencia é obrigatório"));
        }
    }

    private void validarTexto(String campo, String valor, List<Detalhe> erros) {
        if (valor == null || valor.isBlank()) {
            erros.add(new Detalhe(campo, null, valor, campo + " é obrigatório"));
        } else if (valor.length() > TAMANHO_MAXIMO_TEXTO) {
            erros.add(new Detalhe(campo, null, valor, campo + " deve ter até " + TAMANHO_MAXIMO_TEXTO + " caracteres"));
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
