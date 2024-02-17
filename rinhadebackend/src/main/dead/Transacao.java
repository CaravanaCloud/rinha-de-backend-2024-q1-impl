package caravanacloud;

import java.io.Serializable;

import org.infinispan.protostream.annotations.ProtoField;

public class Transacao implements Serializable{
    @ProtoField
    public int valor;
    @ProtoField
    public String tipo;
    @ProtoField
    public String descricao;

    public static Transacao of(int valor2, String tipo2, String descricao2) {
        var t = new Transacao();
        t.valor = valor2;
        t.tipo = tipo2;
        t.descricao = descricao2;
        return t;
    }
}
