package br.com.poc.adapter.out.persistence.jpa;

import br.com.poc.adapter.out.persistence.jpa.entity.ConfgCurvaEntity;
import br.com.poc.adapter.out.persistence.jpa.entity.CurvaMercdEntity;
import br.com.poc.adapter.out.persistence.jpa.repository.ConfgCurvaJpaRepository;
import br.com.poc.adapter.out.persistence.jpa.repository.CurvaMercdJpaRepository;
import br.com.poc.adapter.out.persistence.jpa.repository.CurvaPrvdrJpaRepository;
import br.com.poc.application.exception.BusinessException;
import br.com.poc.application.exception.InfrastructureException;
import br.com.poc.application.port.out.ConfiguracaoCurvaPort;
import br.com.poc.application.port.out.CurvaMercadoPort;
import br.com.poc.application.port.out.CurvaProvedorPort;
import br.com.poc.domain.cadastro.CadastroCurva;
import br.com.poc.domain.cadastro.ParametrosCalculo;
import br.com.poc.domain.curva.CodigoErro;
import br.com.poc.domain.curva.CurvaProvedor;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.QueryTimeoutException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectReader;

import java.text.Normalizer;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Component
public class CurvaMercadoJpaAdapter implements CurvaMercadoPort, CurvaProvedorPort, ConfiguracaoCurvaPort {

    private final CurvaMercdJpaRepository curvaMercdRepo;
    private final CurvaPrvdrJpaRepository curvaPrvdrRepo;
    private final ConfgCurvaJpaRepository confgCurvaRepo;
    private final ObjectReader leitorParametros;
    private final int timeoutTravaSegundos;

    @PersistenceContext
    private EntityManager entityManager;

    public CurvaMercadoJpaAdapter(
        CurvaMercdJpaRepository curvaMercdRepo,
        CurvaPrvdrJpaRepository curvaPrvdrRepo,
        ConfgCurvaJpaRepository confgCurvaRepo,
        ObjectMapper objectMapper,
        @Value("${engine.timeout.trava-curva-segundos:30}") int timeoutTravaSegundos
    ) {
        this.curvaMercdRepo = curvaMercdRepo;
        this.curvaPrvdrRepo = curvaPrvdrRepo;
        this.confgCurvaRepo = confgCurvaRepo;
        // cModDado lido direto em ParametrosCalculo: chave desconhecida ou número fracionário em campo inteiro é erro
        this.leitorParametros = objectMapper.readerFor(ParametrosCalculo.class)
            .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .without(DeserializationFeature.ACCEPT_FLOAT_AS_INT);
        this.timeoutTravaSegundos = timeoutTravaSegundos;
    }

    @Override
    public Optional<CadastroCurva> buscarPorCodigo(String codigo, LocalDate dataBase) {
        List<CurvaMercdEntity> lista = curvaMercdRepo.findByTickerIdtfdUnic(codigo);
        if (lista.isEmpty()) {
            return Optional.empty();
        }
        if (lista.size() > 1) {
            throw new BusinessException(CodigoErro.CODIGO_DUPLICADO, "Código duplicado no cadastro: " + codigo);
        }
        return Optional.of(montarCadastro(lista.getFirst(), dataBase));
    }

    @Override
    public Optional<CadastroCurva> buscarPorNome(String nome, LocalDate dataBase) {
        String nomeNorm = normalizarNome(nome);
        List<CurvaMercdEntity> matches = curvaMercdRepo.findAllByTickerIdtfdUnicIsNotNull().stream()
            .filter(e -> normalizarNome(e.getTickerIndcd()).equals(nomeNorm))
            .toList();

        if (matches.isEmpty()) {
            return Optional.empty();
        }
        if (matches.size() > 1) {
            throw new BusinessException(CodigoErro.NOME_AMBIGUO, "Nome de curva ambíguo: " + nome);
        }
        return Optional.of(montarCadastro(matches.getFirst(), dataBase));
    }

    @Override
    public List<CadastroCurva> listarTodas(LocalDate dataBase) {
        return curvaMercdRepo.findAllByTickerIdtfdUnicIsNotNull().stream()
            .map(e -> montarCadastro(e, dataBase))
            .toList();
    }

    @Override
    public void travarCurva(String nomeCurva) {
        try {
            var query = entityManager.createNativeQuery(
                "SELECT cTickerIndcd FROM dbo.tCurvaMercd WITH (UPDLOCK, ROWLOCK) WHERE cTickerIndcd = :tickerIndcd");
            query.setParameter("tickerIndcd", nomeCurva);
            query.setHint("jakarta.persistence.query.timeout", timeoutTravaSegundos * 1000);
            query.getSingleResult();
        } catch (QueryTimeoutException | org.hibernate.QueryTimeoutException e) {
            throw new BusinessException(CodigoErro.CONSTRUCAO_EM_ANDAMENTO, "Construção da curva em andamento: " + nomeCurva);
        } catch (Exception e) {
            if (e.getCause() instanceof QueryTimeoutException || e.getCause() instanceof org.hibernate.QueryTimeoutException) {
                throw new BusinessException(CodigoErro.CONSTRUCAO_EM_ANDAMENTO, "Construção da curva em andamento: " + nomeCurva);
            }
            throw new InfrastructureException(CodigoErro.ERRO_INTERNO.getCode(), "Falha de banco ao obter trava da curva: " + nomeCurva, e);
        }
    }

    @Override
    public void atualizarResumo(String nomeCurva, LocalDate dataBase, String usuario) {
        curvaMercdRepo.atualizarResumo(nomeCurva, dataBase, usuario);
    }

    @Override
    public List<CurvaProvedor> listarPorNomeCurva(String nomeCurva) {
        return curvaPrvdrRepo.findByTickerIndcdOrderByPriorCsumoAscIdtfdUnicAsc(nomeCurva).stream()
            .map(e -> new CurvaProvedor(e.getPrvdrDados(), e.getPrvdrMercd(), e.getTickerPrvdr(),
                e.getPriorCsumo() != null ? e.getPriorCsumo() : 0))
            .toList();
    }

    @Override
    public List<ConfgCurvaEntity> buscarVigentes(String nomeCurva, LocalDate dataBase) {
        return confgCurvaRepo.findVigentes(nomeCurva, dataBase);
    }

    private CadastroCurva montarCadastro(CurvaMercdEntity curva, LocalDate dataBase) {
        List<CurvaProvedor> origens = listarPorNomeCurva(curva.getTickerIndcd());
        List<ConfgCurvaEntity> configs = buscarVigentes(curva.getTickerIndcd(), dataBase);

        // Só lê a configuração quando há exatamente uma vigente; 0 ou 2+ o validador recusa.
        ConfgCurvaEntity config = configs.size() == 1 ? configs.getFirst() : null;
        String rawJson = config != null ? config.getModDado() : null;
        LeituraParametros leitura = lerParametros(rawJson);

        return new CadastroCurva(
            curva.getTickerIdtfdUnic(),
            curva.getTickerIndcd(),
            curva.getTpoVlr(),
            curva.getNormaDia(),
            curva.getTpoJuro(),
            curva.getSitReg(),
            curva.getInicVgcia(),
            curva.getValidAte(),
            curva.getBaseReft(),
            origens,
            List.of(),
            config != null && config.getIdtfdConfg() != null ? config.getIdtfdConfg().longValue() : null,
            config != null ? config.getMotorCalc() : null,
            config != null ? config.getRotnaCalc() : null,
            rawJson,
            leitura.parametros(),
            leitura.erro(),
            configs.size()
        );
    }

    private record LeituraParametros(ParametrosCalculo parametros, String erro) {}

    private LeituraParametros lerParametros(String json) {
        if (json == null || json.isBlank()) {
            return new LeituraParametros(null, null);
        }
        try {
            return new LeituraParametros(leitorParametros.readValue(json), null);
        } catch (JacksonException e) {
            return new LeituraParametros(null, e.getOriginalMessage());
        }
    }

    private String normalizarNome(String s) {
        if (s == null) return "";
        return Normalizer.normalize(s, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .toLowerCase(Locale.ROOT)
            .strip();
    }
}
