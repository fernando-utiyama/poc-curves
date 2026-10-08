package br.com.poc.application.service;

import br.com.poc.application.exception.CadastroErrorCode;
import br.com.poc.application.exception.NotFoundException;
import br.com.poc.application.port.out.CurvaMercdRepositoryPort;
import br.com.poc.application.port.out.DadosConstruidosPort;
import br.com.poc.application.port.out.EventosPort;
import br.com.poc.domain.cadastro.ConstrucaoApagada;
import br.com.poc.domain.cadastro.CurvaMercado;
import br.com.poc.domain.evento.EventoCadastroAlterado;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Desfaz a construção de uma data: apaga os vértices construídos e a interpolada dela, na mesma transação. */
@Service
@RequiredArgsConstructor
public class ApagarConstrucaoService {

    private final CurvaMercdRepositoryPort curvaRepositoryPort;
    private final DadosConstruidosPort dadosConstruidosPort;
    private final EventosPort eventosPort;

    @Transactional
    public ConstrucaoApagada apagar(String codigoCurva, LocalDate dataBase) {
        CurvaMercado curva = curvaRepositoryPort.findByCodigo(codigoCurva)
            .orElseThrow(() -> new NotFoundException(CadastroErrorCode.NAO_ENCONTRADO.getCode(), "Curva " + codigoCurva + " não encontrada"));

        ConstrucaoApagada apagada = dadosConstruidosPort.apagar(curva.nome(), dataBase);
        if (apagada.vertices() == 0 && apagada.pontos() == 0) {
            throw new NotFoundException(CadastroErrorCode.NAO_ENCONTRADO.getCode(),
                "Curva " + codigoCurva + " não construída na data " + dataBase);
        }

        eventosPort.publicarCadastroAlterado(new EventoCadastroAlterado(
            UUID.randomUUID(),
            curva.codigo(),
            curva.nome(),
            "CONSTRUCAO",
            "EXCLUSAO",
            OffsetDateTime.now(),
            null,
            apagada,
            null
        ));
        return apagada;
    }
}
