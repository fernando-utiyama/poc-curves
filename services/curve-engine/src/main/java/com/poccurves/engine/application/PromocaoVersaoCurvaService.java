package com.poccurves.engine.application;
import com.poccurves.engine.domain.versao.MomentoCurva;
import com.poccurves.engine.domain.versao.VersaoCurva;

import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * As duas transições de estado da publicação (promover, reprovar), isoladas num bean próprio.
 * <p>
 * Motivo de existir separado de {@link PublicacaoCurvaService}: {@code @Transactional} do Spring
 * funciona via proxy — só tem efeito quando o método é chamado de FORA do bean, através do proxy.
 * Um método privado (ou público, mas invocado via {@code this.metodo(...)} de dentro da própria
 * classe) nunca passa pelo proxy, e a anotação é silenciosamente ignorada — o erro real que motivou
 * esta classe: {@code promoverVersao}/{@code reprovarVersao} eram métodos privados de
 * {@code PublicacaoCurvaService}, chamados via auto-invocação, sem transação nenhuma de verdade.
 * Isolar num bean injetado, chamado de fora, é a forma correta e padrão de resolver isso.
 */
public class PromocaoVersaoCurvaService {

    private final VersaoCurvaRepositoryPort versaoCurvaRepository;

    public PromocaoVersaoCurvaService(VersaoCurvaRepositoryPort versaoCurvaRepository) {
        this.versaoCurvaRepository = versaoCurvaRepository;
    }

    /**
     * Promove a versão para PUBLICADA, substituindo antes a versão anteriormente vigente (se houver)
     * — nessa ordem, na mesma transação, para nunca haver zero nem duas versões PUBLICADA ao mesmo
     * tempo para a mesma curva/data/momento (índice único {@code ux_versao_curva_publicada}).
     */
    @Transactional
    public VersaoCurva promoverVersao(UUID versaoCurvaId, UUID definicaoCurvaId, LocalDate referenceDate, MomentoCurva momento) {
        VersaoCurva novaVersao = versaoCurvaRepository.buscarPorId(versaoCurvaId)
                .orElseThrow(() -> new IllegalStateException("Versão não encontrada: " + versaoCurvaId));

        Optional<VersaoCurva> antigaOpt = versaoCurvaRepository.buscarVersaoVigentePublicada(definicaoCurvaId, referenceDate, momento);
        if (antigaOpt.isPresent()) {
            VersaoCurva antiga = antigaOpt.get();
            antiga.substituir();
            versaoCurvaRepository.atualizar(antiga);
        }

        novaVersao.publicar();
        versaoCurvaRepository.atualizar(novaVersao);

        return novaVersao;
    }

    @Transactional
    public void reprovarVersao(UUID versaoCurvaId) {
        VersaoCurva versao = versaoCurvaRepository.buscarPorId(versaoCurvaId)
                .orElseThrow(() -> new IllegalStateException("Versão não encontrada: " + versaoCurvaId));
        versao.reprovar();
        versaoCurvaRepository.atualizar(versao);
    }
}
