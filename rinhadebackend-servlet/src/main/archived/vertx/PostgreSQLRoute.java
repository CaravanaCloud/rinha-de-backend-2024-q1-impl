package caravanacloud.vertx;

import java.time.Duration;
import java.util.Map;

import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.core.Vertx;
import io.vertx.mutiny.pgclient.PgPool;
import io.vertx.mutiny.sqlclient.Row;
import io.vertx.mutiny.sqlclient.RowSet;
import io.vertx.mutiny.sqlclient.Tuple;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Response;

@ApplicationScoped
@Path("/vertx/clientes")
public class PostgreSQLRoute {
    private static final String VERSION_ID = "0.0.3-pgsql";
    private static final String EXTRATO_QUERY = "select * from proc_extrato($1)";
    private static final String TRANSACAO_QUERY = "select * from proc_transacao($1, $2, $3, $4, $5)";
    private static final String WARMUP_QUERY = "select 1+1;";
    private static final int WARMUP_LEVEL = 10;

    private int shard;

    @Inject
    Vertx vertx;

    @Inject
    PgPool client;

    public void onStartup(@Observes StartupEvent event) {
        shard = envInt("RINHA_SHARD", 0);
        System.out.println("StartupEvent shard[" + shard + "] version[" + VERSION_ID + "] 🐔💥");
        boolean ready = false;
        do {
            try {
                for (int i = 0; i < WARMUP_LEVEL; i++) {
                    warmup();
                    processExtrato(333);
                    processTransacao(333, Map.of("valor", "0", "tipo", "c", "descricao", "warmup"));
                }
                ready = true;
            } catch (Exception e) {
                System.err.println("Warmup failed, waiting for db: " + e.getMessage());
                ready = false;
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException ie) {
                    ie.printStackTrace();
                }
            }
        } while (!ready);
    }

    private void warmup() {
        var query = getEnv("RINHA_WARMUP_QUERY", WARMUP_QUERY);
        Log.info("Warmup query: " + query);
        client.preparedQuery(query)
                .execute()
                .await()
                .atMost(Duration.ofSeconds(30));
        Log.info("Warmup done");
    }

    private boolean invalid(int id) {
        return id > 5 || id < 1;
    }

    @GET
    @Path("/{id}/extrato")
    public Uni<Response> doGet(@PathParam("id") int id) {
        if (invalid(id)) {
            return Uni.createFrom().item(Response.status(404).build());
        }
        var result = processExtrato(id);
        // .onItem().ifNull().continueWith(Response.status(422).build())
        var resp = result.onItem().transform(r -> r != null ? r : Response.status(422).build());
        return resp;
    }

    private Uni<Response> processExtrato(int id) {
        return client.withTransaction(conn -> 
            conn.preparedQuery(EXTRATO_QUERY)
                .execute(Tuple.of(id))
                .onItem().transform(RowSet::iterator) 
                .onItem().transform(iterator -> iterator.hasNext() ? iterator.next() : null)
                .onItem().transform(r -> r != null ? responseOf(r) : null)
                .onFailure().recoverWithItem(e -> errorOf(e,"err_extrato"))); 
    }

    @Path("/{id}/transacoes")
    @Consumes("application/json")
    @POST
    public Uni<Response> doPost(@PathParam("id") int id, Map<String, String> txx) {
        if (invalid(id)) {
            return Uni.createFrom().item(Response.status(404).build());
        }
        var result = processTransacao(id, txx);
        var resp = result.onItem().transform(r -> r != null ? r : Response.status(422).build());
        return resp;
    }

    private Uni<Response> processTransacao(int id, Map<String, String> txx) {
        var valorNumber = txx.get("valor");
        if (valorNumber == null || valorNumber.contains(".")) {
            return Uni.createFrom().item(errorOf("valor invalido", 422));
        }

        int valor = -1;
        try {
            valor = Integer.parseInt(valorNumber);
        } catch (NumberFormatException e) {
            return Uni.createFrom().item(errorOf("valor invalido", 422));
        }
        final int valorFinal = valor;

        String tipo = txx.get("tipo");
        if (tipo == null || !("c".equals(tipo) || "d".equals(tipo))) {
            return Uni.createFrom().item(errorOf("tipo invalido", 422));
        }

        var descricao = txx.get("descricao");
        if (descricao == null || descricao.isEmpty() || descricao.length() > 10 || "null".equals(descricao)) {
            return Uni.createFrom().item(errorOf("descricao invalida", 422));
        }

        Uni<Response>  result = client.withTransaction(
            conn -> conn.preparedQuery(TRANSACAO_QUERY)
                .execute(Tuple.of(shard, id, valorFinal, tipo, descricao))
                .onItem().transform(RowSet::iterator)
                .onItem().transform(iterator -> iterator.hasNext() ? iterator.next() : null)
                .onItem().transform(r -> r != null ? responseOf(r) : null)
                .onFailure().recoverWithItem(e -> errorOf(e, "err_transacao")));
        
        return result;
    }

    private static int envInt(String varName, int defaultVal) {
        var result = System.getenv(varName);
        if (result == null) {
            System.out.println("Env var " + varName + " not found, using default " + defaultVal);
            return defaultVal;
        }
        try {
            return Integer.parseInt(result);
        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        }
    }

    private static String getEnv(String varName, String defaultVal) {
        var result = System.getenv(varName);
        return result != null ? result : defaultVal;
    }

    private Response errorOf(Throwable e, String key) {
        return Response.status(422).entity("{\"" + key + "\": \"" + e.getMessage() + "\"}").build();
    }

    private Response errorOf(String e, int status) {
        return Response.status(status).entity("{\"err\": \"" + e + "\"}").build();
    }

    private Response responseOf(Row r) {
        var status = r.getInteger("status_code");
        var body = r.getJson("body");
        return Response.status(status).entity(body).build();
    }
}