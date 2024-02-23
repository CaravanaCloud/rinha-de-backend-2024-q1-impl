package caravanacloud;

import java.io.Serializable;
import java.time.LocalDateTime;


public class Transacao implements Serializable{
    public int valor;
    public String tipo;
    public String descricao;
    public LocalDateTime realizadaEm;

    public static Transacao of(int valor2, String tipo2, String descricao2, LocalDateTime realizadaEm) {
        var t = new Transacao();
        t.valor = valor2;
        t.tipo = tipo2;
        t.descricao = descricao2;
        t.realizadaEm = realizadaEm;
        return t;
    }
}
