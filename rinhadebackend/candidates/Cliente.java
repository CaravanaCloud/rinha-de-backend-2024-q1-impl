package caravanacloud;

import java.util.Deque;
import java.util.LinkedList;

import org.infinispan.protostream.annotations.ProtoField;


public class Cliente{
    @ProtoField(number = 1)
    public Integer id;

    @ProtoField(number = 2, required = true)
    public int saldo;

    @ProtoField(number = 3, required = true)
    public int limite;

    @ProtoField(number = 4)
    LinkedList<Transacao> transacoes;

    public static Cliente of(Integer id, int saldo, int limite) {
        var c = new Cliente();
        c.id = id;
        c.saldo = saldo;
        c.limite = limite;
        return c;
    }
}

