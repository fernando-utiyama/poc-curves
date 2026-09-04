package com.poccurves.processor.domain.parsing;
import com.poccurves.processor.domain.ingestao.PontoDadoMercado;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.UnsupportedCharsetException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Parser do relatório de preços/ajustes da B3 (schema {@code urn:bvmf.217.01.xsd},
 * mensagem {@code BVBG.086.01} — confirmado em arquivo real de produção
 * fornecido pelo usuário, capturado em 2026-08-22: o produto "Preços de
 * Referência" (PR) baixado em {@code PR260821.zip} contém internamente um
 * arquivo {@code BVBG.086.01_*.xml}. Por essa evidência, este parser também
 * é registrado sob o dataset {@code PR_DI1} — ver
 * {@link #datasetsCobertos()}; se um formato de PR distinto do BVBG.086
 * for confirmado depois, separar em outra classe.
 * <p>
 * Escopo desta versão: extrai só registros de instrumento com
 * {@code AdjstdQtTax} preenchido (taxa de ajuste do dia — o campo relevante
 * para bootstrapping de curva de juros, ex. contratos futuros de DI1).
 * Registros de outros tipos de instrumento sem esse campo (ex. opções sobre
 * ações) são ignorados silenciosamente, não é erro de parsing — real no
 * arquivo de produção, um único boletim traz todos os instrumentos
 * negociados no dia, não só os de juros.
 */
public final class Bvbg086PricRptParser implements DatasetParser {

    /** Rótulo de {@code tipoCotacao} para o valor extraído (taxa de ajuste do dia). */
    public static final String TIPO_COTACAO_TAXA_AJUSTE = "TAXA_AJUSTE";

    private final String dataset;

    public Bvbg086PricRptParser(String dataset) {
        if (dataset == null || dataset.isBlank()) {
            throw new IllegalArgumentException("dataset não pode ser nulo ou vazio");
        }
        this.dataset = dataset;
    }

    /** Os dois identificadores de dataset sob os quais este parser deve ser registrado — ver javadoc da classe. */
    public static List<String> datasetsCobertos() {
        return List.of("BVBG.086", "PR_DI1");
    }

    @Override
    public String dataset() {
        return dataset;
    }

    @Override
    public ParseResult parse(byte[] conteudo, String encoding, LocalDate referenceDate) {
        Charset charset;
        try {
            charset = Charset.forName(encoding);
        } catch (IllegalCharsetNameException | UnsupportedCharsetException e) {
            return new ParseResult.Falha("encoding não suportado: " + encoding, e.toString());
        }

        String textoCompleto = new String(conteudo, charset);
        List<String> fragmentos;
        try {
            fragmentos = ElementosXmlRepetidos.extrair(textoCompleto, "BizGrp");
        } catch (IllegalArgumentException e) {
            return new ParseResult.Falha("XML malformado", e.getMessage());
        }
        if (fragmentos.isEmpty()) {
            return new ParseResult.Falha("nenhum elemento <BizGrp> encontrado no bloco", "");
        }

        List<PontoDadoMercado> pontos = new ArrayList<>();
        DocumentBuilderFactory factory = criarFactory();

        for (String fragmento : fragmentos) {
            Element pricRpt = extrairPricRpt(fragmento, factory);
            if (pricRpt == null) {
                continue;
            }
            PontoDadoMercado ponto = extrairPonto(pricRpt);
            if (ponto != null) {
                pontos.add(ponto);
            }
        }

        return new ParseResult.Sucesso(pontos);
    }

    private Element extrairPricRpt(String fragmentoBizGrp, DocumentBuilderFactory factory) {
        try {
            DocumentBuilder builder = factory.newDocumentBuilder();
            try (InputStream in = new ByteArrayInputStream(fragmentoBizGrp.getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
                Document doc = builder.parse(in);
                Element bizGrp = doc.getDocumentElement();
                Element documento = NavegacaoDom.filhoDireto(bizGrp, "Document");
                return NavegacaoDom.filhoDireto(documento, "PricRpt");
            }
        } catch (Exception e) {
            return null;
        }
    }

    private PontoDadoMercado extrairPonto(Element pricRpt) {
        Element sctyId = NavegacaoDom.filhoDireto(pricRpt, "SctyId");
        String ticker = NavegacaoDom.texto(NavegacaoDom.filhoDireto(sctyId, "TckrSymb"));

        Element finInstrmId = NavegacaoDom.filhoDireto(pricRpt, "FinInstrmId");
        String idInstrumento = NavegacaoDom.texto(NavegacaoDom.caminho(finInstrmId, "OthrId", "Id"));

        String chaveInstrumento = (ticker != null && !ticker.isBlank()) ? ticker : idInstrumento;
        if (chaveInstrumento == null || chaveInstrumento.isBlank()) {
            return null;
        }

        String dataTexto = NavegacaoDom.texto(NavegacaoDom.caminho(pricRpt, "TradDt", "Dt"));
        if (dataTexto == null || dataTexto.isBlank()) {
            return null;
        }
        LocalDate dataReferencia;
        try {
            dataReferencia = LocalDate.parse(dataTexto);
        } catch (java.time.format.DateTimeParseException e) {
            return null;
        }

        Element atributos = NavegacaoDom.filhoDireto(pricRpt, "FinInstrmAttrbts");
        Element adjstdQtTax = NavegacaoDom.filhoDireto(atributos, "AdjstdQtTax");
        String valorTexto = NavegacaoDom.texto(adjstdQtTax);
        if (valorTexto == null || valorTexto.isBlank()) {
            // Sem taxa de ajuste publicada para este instrumento neste bloco — não é erro, apenas fora do escopo.
            return null;
        }

        BigDecimal valor;
        try {
            valor = ConversorDecimal.paraBigDecimal(valorTexto, '.');
        } catch (IllegalArgumentException e) {
            return null;
        }

        // dataVencimento: requer correlação com o cadastro (BVBG.028, campo
        // XprtnDt), que chega em evento/lote separado — não resolvido nesta
        // versão do parser. Ver services/curve-processor tasks.md, seção 3.
        return new PontoDadoMercado(
                "B3",
                dataset,
                dataReferencia,
                chaveInstrumento,
                valor,
                TIPO_COTACAO_TAXA_AJUSTE,
                null
        );
    }

    private static DocumentBuilderFactory criarFactory() {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        try {
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        } catch (javax.xml.parsers.ParserConfigurationException e) {
            throw new IllegalStateException("falha ao configurar parser XML seguro", e);
        }
        return factory;
    }
}
