package br.com.poc.domain.pipeline;

import br.com.poc.domain.calendar.BusinessCalendar;
import br.com.poc.domain.model.ConvencaoDias;
import br.com.poc.domain.model.TipoInsumoCurva;
import br.com.poc.domain.model.VerticeInsumo;

public interface InsumoNormalizer {

    /**
     * Garante que o VerticeInsumo possua tanto taxa anualizada quanto fator/PU consistentes,
     * convertendo de TAXA -> FATOR ou FATOR/PU -> TAXA conforme o tipo de insumo e a convenção de dias.
     *
     * @param insumo Vértice bruto de entrada
     * @param tipoInsumo Tipo do insumo (TAXA, FATOR, PRECO_UNITARIO)
     * @param convencao Convenção de contagem de dias (ex: DU_252, ACT_360)
     * @return Vértice normalizado com taxa e valor preenchidos
     */
    VerticeInsumo normalizar(VerticeInsumo insumo, TipoInsumoCurva tipoInsumo, ConvencaoDias convencao);

    /**
     * Normaliza o insumo utilizando um calendário específico para apuração de dias úteis entre datas.
     *
     * @param insumo Vértice bruto de entrada
     * @param tipoInsumo Tipo do insumo (TAXA, FATOR, PRECO_UNITARIO)
     * @param convencao Convenção de contagem de dias (ex: DU_252, ACT_360)
     * @param calendar Calendário de dias úteis e feriados (ex: ANBIMA/B3)
     * @return Vértice normalizado
     */
    default VerticeInsumo normalizar(VerticeInsumo insumo, TipoInsumoCurva tipoInsumo, ConvencaoDias convencao, BusinessCalendar calendar) {
        return normalizar(insumo, tipoInsumo, convencao);
    }
}
