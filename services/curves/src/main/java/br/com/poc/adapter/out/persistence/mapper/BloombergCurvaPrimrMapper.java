package br.com.poc.adapter.out.persistence.mapper;

import br.com.poc.adapter.out.persistence.entity.BloombergCurvaPrimrEntity;
import br.com.poc.application.model.BloombergCurvaPrimr;
import org.springframework.stereotype.Component;

/**
 * Conversões entre a entidade JPA e o modelo de domínio implementadas manualmente porque os campos
 * seguem o padrão {@code cldtfdUnic}/{@code vPrecoLiqdc} (letra minúscula seguida de maiúscula), que o
 * MapStruct não consegue casar automaticamente entre os beans.
 */
@Component
public class BloombergCurvaPrimrMapper {

    public BloombergCurvaPrimr toDomain(BloombergCurvaPrimrEntity entity) {
        if (entity == null) {
            return null;
        }

        BloombergCurvaPrimr domain = new BloombergCurvaPrimr();
        domain.setCldtfdUnic(entity.getCldtfdUnic());
        domain.setCTickerIndcd(entity.getCTickerIndcd());
        domain.setVPrecoLiqdc(entity.getVPrecoLiqdc());
        domain.setVPrecoMed(entity.getVPrecoMed());
        domain.setVPrecoUlt(entity.getVPrecoUlt());
        domain.setCDiaVcto(entity.getCDiaVcto());
        domain.setDLiqdcFincr(entity.getDLiqdcFincr());
        domain.setCTickerBberg(entity.getCTickerBberg());
        domain.setCFormaLiqdc(entity.getCFormaLiqdc());
        domain.setDVctoContr(entity.getDVctoContr());
        domain.setDUltNegoc(entity.getDUltNegoc());
        domain.setDBaseReft(entity.getDBaseReft());
        return domain;
    }

    public BloombergCurvaPrimrEntity toEntity(BloombergCurvaPrimr domain) {
        if (domain == null) {
            return null;
        }

        BloombergCurvaPrimrEntity entity = new BloombergCurvaPrimrEntity();
        entity.setCldtfdUnic(domain.getCldtfdUnic());
        entity.setCTickerIndcd(domain.getCTickerIndcd());
        entity.setVPrecoLiqdc(domain.getVPrecoLiqdc());
        entity.setVPrecoMed(domain.getVPrecoMed());
        entity.setVPrecoUlt(domain.getVPrecoUlt());
        entity.setCDiaVcto(domain.getCDiaVcto());
        entity.setDLiqdcFincr(domain.getDLiqdcFincr());
        entity.setCTickerBberg(domain.getCTickerBberg());
        entity.setCFormaLiqdc(domain.getCFormaLiqdc());
        entity.setDVctoContr(domain.getDVctoContr());
        entity.setDUltNegoc(domain.getDUltNegoc());
        entity.setDBaseReft(domain.getDBaseReft());
        return entity;
    }
}
