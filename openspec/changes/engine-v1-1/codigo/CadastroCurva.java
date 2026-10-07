package br.com.poc.domain.cadastro;

import br.com.poc.domain.curva.Componente;
import br.com.poc.domain.curva.CurvaProvedor;

import java.time.LocalDate;
import java.util.List;

/**
 * Cadastro da curva como está no banco (tCurvaMercd, tCurvaPrvdr, tConfgCurva), antes de validar.
 * O ValidadorCadastro confere e transforma em CurvaMercado.
 * Substitui o antigo CurvaMercadoLida.
 */
public record CadastroCurva(
    String codigo,
    String nome,
    String tipoValor,
    String normaDia,
    String tipoJuro,
    String situacao,
    LocalDate inicioVigencia,
    LocalDate fimVigencia,
    LocalDate dataBaseReferencia,
    List<CurvaProvedor> origens,
    List<Componente> componentes,
    Long idConfiguracao,
    String motorCalc,
    String rotinaCalc,
    String jsonParametrosRaw,      // texto de cModDado, guardado na proveniência
    ParametrosCalculo parametros,  // nulo quando cModDado está vazio ou não pôde ser lido
    String erroParametros,         // motivo quando cModDado não pôde ser lido
    int configuracoesVigentes
) {}
