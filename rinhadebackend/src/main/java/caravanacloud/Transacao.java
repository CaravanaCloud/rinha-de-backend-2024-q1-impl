package caravanacloud;

import java.io.Serializable;


public class Transacao implements Serializable{
    public int valor;
    public String tipo;
    public String descricao;

    public static Transacao of(int valor2, String tipo2, String descricao2) {
        var t = new Transacao();
        t.valor = valor2;
        t.tipo = tipo2;
        t.descricao = descricao2;
        return t;
    }
}
