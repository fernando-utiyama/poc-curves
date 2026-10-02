package br.com.poc.application.mapper;

import br.com.poc.application.dto.BloombergCurvaPrimrMutableFields;
import br.com.poc.application.dto.BloombergCurvaPrimrResponse;
import br.com.poc.application.dto.CreateBloombergCurvaPrimrRequest;
import br.com.poc.application.dto.UpdateBloombergCurvaPrimrRequest;
import br.com.poc.application.model.BloombergCurvaPrimr;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

/**
 * Mapper responsável pela conversão entre os DTOs de entrada/saída da API e o modelo de domínio
 * {@link BloombergCurvaPrimr}, mantendo o caso de uso livre de código repetitivo de atribuição de campos.
 *
 * <p>As conversões são implementadas manualmente porque os campos seguem o padrão de nomenclatura
 * {@code cIdtfdUnic}/{@code vPrecoLiqdc} (letra minúscula seguida de maiúscula), que o MapStruct não
 * consegue casar automaticamente entre os {@code record}s de entrada e o bean de domínio.</p>
 */
@Mapper(componentModel = "spring")
public interface BloombergCurvaPrimrRequestMapper {

    default BloombergCurvaPrimr toDomain(CreateBloombergCurvaPrimrRequest request) {
        if (request == null) {
            return null;
        }

        BloombergCurvaPrimr domain = new BloombergCurvaPrimr();
        domain.setCTickerIndcd(request.cTickerIndcd());
        applyMutableFields(request, domain);
        return domain;
    }

    default void updateDomainFromRequest(UpdateBloombergCurvaPrimrRequest request, @MappingTarget BloombergCurvaPrimr domain) {
        if (request == null || domain == null) {
            return;
        }

        applyMutableFields(request, domain);
    }

    private void applyMutableFields(BloombergCurvaPrimrMutableFields request, BloombergCurvaPrimr domain) {
        domain.setVPrecoLiqdc(request.vPrecoLiqdc());
        domain.setVPrecoMed(request.vPrecoMed());
        domain.setVPrecoUlt(request.vPrecoUlt());
        domain.setCDiaVcto(request.cDiaVcto());
        domain.setDLiqdcFincr(request.dLiqdcFincr());
        domain.setCTickerBberg(request.cTickerBberg());
        domain.setCFormaLiqdc(request.cFormaLiqdc());
        domain.setDVctoContr(request.dVctoContr());
        domain.setDUltNegoc(request.dUltNegoc());
        domain.setDBaseReft(request.dBaseReft());
    }

    default BloombergCurvaPrimrResponse toResponse(BloombergCurvaPrimr domain) {
        if (domain == null) {
            return null;
        }

        return new BloombergCurvaPrimrResponse(
            domain.getCIdtfdUnic(),
            domain.getCTickerIndcd(),
            domain.getVPrecoLiqdc(),
            domain.getVPrecoMed(),
            domain.getVPrecoUlt(),
            domain.getCDiaVcto(),
            domain.getDLiqdcFincr(),
            domain.getCTickerBberg(),
            domain.getCFormaLiqdc(),
            domain.getDVctoContr(),
            domain.getDUltNegoc(),
            domain.getDBaseReft()
        );
    }
}
