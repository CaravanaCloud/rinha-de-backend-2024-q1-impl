package caravanacloud;

import io.quarkus.logging.Log;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedList;




public class Cliente implements Serializable {
    public Integer shard;
    public Integer id;
    public int saldo;
    public int limite;
    public String jsonData;
    public LinkedList<Transacao> transacoes;

    public static Cliente of(Integer shard, Integer id, String nome, int saldo, int limite, String jsonData) {
        var c = new Cliente();
        c.id = id;
        c.saldo = saldo;
        c.limite = limite;
        c.jsonData = jsonData;
        return c;
    }

    public static int limiteOf(Integer id) {
        return switch (id){
            case 1 -> 100000;
            case 2 -> 80000;
            case 3 -> 1000000;
            case 4 -> 10000000;
            case 5 -> 500000;
            default -> -1;
        };
    }

    public String toExtrato(){
        var txxs = getTransacoes();
        StringBuilder transacoesJson = new StringBuilder("[");
        for (int i = 0; i < txxs.size(); i++) {
            Transacao t = txxs.get(i);
            transacoesJson.append(String.format("""
                {
                    "valor": %d,
                    "tipo": "%s",
                    "descricao": "%s",
                    "realizada_em": "%s"
                }""", t.valor, t.tipo, t.descricao, t.realizadaEm));
            if (i < txxs.size() - 1) {
                transacoesJson.append(",");
            }
        }
        transacoesJson.append("]");

        String dataExtrato = ZonedDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);

        return String.format("""
               {
                 "saldo": {
                   "total": %d,
                   "data_extrato": "%s",
                   "limite": %d
                 },
                 "ultimas_transacoes": %s
               }
               """, saldo, dataExtrato, limite, transacoesJson);
    }

    public synchronized int transacao(Integer valor, String tipo, String descricao) {
        var diff = switch (tipo) {
            case "d" -> -1 * valor;
            default -> valor;
        };
        var novo = valor + diff;
        if (novo < -1 * limiteOf(id)){
            Log.warn("---- LIMITE ULTRAPASSADO ---");
            return 422;
        }
        var txx = Transacao.of(valor, tipo, descricao, LocalDateTime.now());
        var txxs = getTransacoes();
        txxs.add(txx);
        if (txxs.size() > 10){
            txxs.removeFirst();
        }
        this.saldo += diff;
        return 200;
    }

    private synchronized  LinkedList<Transacao> getTransacoes() {
        if (transacoes == null){
            transacoes = new LinkedList<>();
        }
        return transacoes;
    }

    public String toTransacao() {
        return String.format("""
                {
                   "saldo": %d,
                   "limite": %d
                 },
               """, saldo, limite);
    }
}

