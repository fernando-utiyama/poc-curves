package br.com.poc.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "tPrvdrDadoMercd")
@Getter
@Setter
@NoArgsConstructor
public class ProvedorEntity {

    @Id
    @Column(name = "iPrvdrDados", nullable = false, length = 1024)
    private String nomeProvedor;

    @Column(name = "cInfoProdt", length = 1024)
    private String descricao;

    @Column(name = "cProdt", length = 1024)
    private String produto;

    @Column(name = "iCopIt", length = 50)
    private String nomeCompletoAtivoOuInstrumento;
}
