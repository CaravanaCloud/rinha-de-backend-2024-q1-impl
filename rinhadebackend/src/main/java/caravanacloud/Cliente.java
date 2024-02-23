package caravanacloud;

import java.io.Serializable;
import java.util.LinkedList;


import com.fasterxml.jackson.annotation.JsonProperty;


public class Cliente implements Serializable {
    public Integer shard;
    public Integer id;
    public int saldo;
    public int limite;
    public LinkedList<Transacao> transacoes = new LinkedList<>();

    public static Cliente of(Integer shard, Integer id, String nome, int saldo, int limite) {
        var c = new Cliente();
        c.id = id;
        c.saldo = saldo;
        c.limite = limite;
        return c;
    }
}

