package caravanacloud;

import java.util.Deque;
import java.util.LinkedList;



public class Cliente{
    public Integer id;
    public int saldo;
    public int limite;
    public LinkedList<Transacao> transacoes = new LinkedList<>();

    public static Cliente of(Integer id, int saldo, int limite) {
        var c = new Cliente();
        c.id = id;
        c.saldo = saldo;
        c.limite = limite;
        return c;
    }
}

