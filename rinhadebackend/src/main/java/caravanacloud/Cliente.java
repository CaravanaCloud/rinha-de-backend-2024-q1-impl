package caravanacloud;

import java.util.Deque;

import org.infinispan.protostream.annotations.ProtoField;


public class Cliente{
    @ProtoField(number = 1)
    public Integer id;
    @ProtoField(number = 2)
    public int saldo;
    @ProtoField(number = 3)
    public int limite;
    @ProtoField(number = 4)
    Deque<Transacao> transacoes;
}

