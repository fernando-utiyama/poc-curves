package br.com.poc.adapter.in.api.rest.dto;

import br.com.poc.domain.cadastro.CurvaMercadoInput;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

@Schema(description = "Requisição para criar ou alterar uma curva de mercado")
public record CurvaMercadoRequest(
    @Schema(description = "Código da curva (A-Z, 0-9 e sublinhado)", example = "PRE") String codigo,
    @Schema(description = "Nome da curva, único e imutável (até 50 caracteres)", example = "DIxPRE") String nome,
    @Schema(description = "Unidade da cotação: TAXA, PRECO ou PONTOS", example = "TAXA") String unidade,
    @Schema(description = "Base de dias da cotação; só para unidade TAXA", example = "Business252") String dayCounterCotacao,
    @Schema(description = "Capitalização da cotação; só para unidade TAXA", example = "Compounded") String compounding,
    @Schema(description = "Moeda (ISO 4217)", example = "BRL") String moeda,
    @Schema(description = "País (ISO 3166-1 alfa-2)", example = "BR") String pais,
    @Schema(description = "Classificação (até 50 caracteres)", example = "Juros") String classificacao,
    @Schema(description = "Classe do ativo (até 50 caracteres)", example = "Swap DI x Pré") String classeAtivo,
    @Schema(description = "Dono da curva (até 50 caracteres)", example = "Mesa de Mercado") String dono,
    @Schema(description = "Início da vigência (AAAA-MM-DD)", example = "2026-01-01") LocalDate inicioVigencia,
    @Schema(description = "Fim da vigência (AAAA-MM-DD); vazio, a curva fica em aberto", example = "2026-12-31") LocalDate fimVigencia
) {

    public CurvaMercadoInput toInput() {
        return new CurvaMercadoInput(
            codigo,
            nome,
            unidade,
            dayCounterCotacao,
            compounding,
            moeda,
            pais,
            classificacao,
            classeAtivo,
            dono,
            inicioVigencia,
            fimVigencia
        );
    }
}
