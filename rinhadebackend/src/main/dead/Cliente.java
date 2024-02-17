package caravanacloud;

import java.io.Serializable;
import java.util.Deque;
import java.util.LinkedList;

import org.infinispan.protostream.annotations.ProtoField;

import com.fasterxml.jackson.annotation.JsonProperty;


public class Cliente implements Serializable {
    @ProtoField
    public Integer id;
    @ProtoField
    @JsonProperty("total")
    public int saldo;
    @ProtoField
    @JsonProperty("limite")
    public int limite;
    @ProtoField
    @JsonProperty("ultimas_transacoes")
    public LinkedList<Transacao> transacoes = new LinkedList<>();

    public static Cliente of(Integer id, int saldo, int limite) {
        var c = new Cliente();
        c.id = id;
        c.saldo = saldo;
        c.limite = limite;
        return c;
    }
}

