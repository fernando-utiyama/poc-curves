# B3: do download ao TaxaSwap.txt (exemplo para o processor)

Pacotes como `br.com.poc`: troque por `br.com.bradesco`.

| Arquivo | Onde fica | O que faz |
|---|---|---|
| `B3TaxaSwapClient.java` | `adapter/out/client/b3/` | `GET` do `TS{AAMMDD}.ex_` com `java.net.http.HttpClient` |
| `ExtratorTaxaSwap.java` | `adapter/out/client/b3/` | zip → `.exe` autoextraível → `TaxaSwap.txt` |
| `LeiauteTaxaSwap.java` | `application/model/leiaute/` | forma canônica e data-base (o `interpretar` segue o guia, seção 11.1) |
| `B3DownloadSiteProvedor.java` | `adapter/out/client/b3/` | encadeia: baixar → extrair → canonizar → conferir a data |
| `CargaException.java` | `application/exception/` | 503 / 502 / 422 da spec; troque pelas exceções do processor, se já houver |
| `ExtratorTaxaSwapTest.java` | `src/test/java/.../client/b3/` | testes com o arquivo real |

Configuração:

```yaml
processor:
  b3:
    url: https://www.b3.com.br/pesquisapregao/download?filelist=TS{data}.ex_
```

Recurso de teste: copiar `docs/TS260914.exe` para `src/test/resources/b3/TS260914.exe` sem alterar nenhum byte.

## Por que pular até o `PK`

O `.ex_` é um zip com um `.exe` autoextraível dentro. O `.exe` começa com o programa (`MZ...`) e o zip só começa depois (no arquivo de 14/09/2026, no byte 86.036). O `ZipInputStream` lê os cabeçalhos em sequência a partir do primeiro byte e não acharia nada; por isso cada nível é aberto a partir da primeira assinatura `PK\3\4`. Funciona também se a B3 um dia entregar o zip sem o `.exe`, ou se o usuário subir o `TaxaSwap.txt` direto.
