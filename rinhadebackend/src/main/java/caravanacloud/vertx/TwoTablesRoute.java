package caravanacloud.vertx;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.quarkus.logging.Log;
import io.quarkus.runtime.Startup;
import io.quarkus.runtime.StartupEvent;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.vertx.core.json.JsonObject;
import io.vertx.mutiny.core.Vertx;
import io.vertx.mutiny.pgclient.PgPool;
import io.vertx.mutiny.sqlclient.Row;
import io.vertx.mutiny.sqlclient.RowSet;
import io.vertx.mutiny.sqlclient.Tuple;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Response;

@ApplicationScoped
@Path("/clientes")
public class TwoTablesRoute {

    private static final String EXTRATO_QUERY = "select * from proc_extrato($1)";
    private static final String TRANSACAO_QUERY = "select * from proc_transacao($1, $2, $3, $4, $5)";
    private static final String WARMUP_QUERY = "select 1+1;";

    private Integer shard;

    @Inject
    Vertx vertx;

    @Inject
    PgPool client; 
    public void onStartup(@Observes StartupEvent event) {
        this.shard = envInt("RINHA_SHARD", 0);
        System.out.println("StartupEvent shard["+ shard +"] 🐔💥");
        boolean ready = false;
        do {
            try {
                warmup();
                // processExtrato(1, null);
                // postTransacao(1, "0", "c", "onStartup", null);
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

    private static Integer envInt(String varName, int defaultVal) {
        var result = System.getenv(varName);
        if (result == null) {
            System.out.println("Env var " + varName + " not found, using default " + defaultVal);
            return defaultVal;
        }
        try {
            return Integer.valueOf(result);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private void warmup() {
        var query = getEnv("RINHA_WARMUP_QUERY", WARMUP_QUERY);
        Log.info("Warmup query: " + query);
        client.preparedQuery(query).execute()
                .await().indefinitely();
        Log.info("Warmup done");
    }

    private static String getEnv(String varName, String defaultVal) {
        var result = System.getenv(varName);
        return result != null ? result : defaultVal;
    }


    @GET                                                                                   
    @Path("/{id}/extrato")
    public Uni<Response> doGet(@PathParam("id") Integer id) {
        if (invalid(id)) {
            return Uni.createFrom().item(Response.status(404).build());
        }
        var result = processExtrato(id);
        var resp = result.onItem().transform(r -> r != null ? 
              Response.ok(r.body()).status(r.statusCode()).build() 
            : Response.status(422).build());                                           
        return resp;  
    }


    private boolean invalid(Integer id) {
        return  id > 5 || id < 1;
	}

	private Uni<Result> processExtrato(Integer id) {
        // invoke EXTRATO_QUERY
        var query = client.preparedQuery(EXTRATO_QUERY).execute(Tuple.of(id));
        Uni<Result> result = query.onItem().transform(RowSet::iterator) 
            .onItem().transform(iterator -> iterator.hasNext() ? iterator.next() : null)
            .onItem().transform(data -> data != null ? Result.of(data) : null); 
        return result;
    }

    @Path("/{id}/transacoes")
    @Consumes("application/json")
    @POST
    public Uni<Response> doPost(@PathParam("id") Integer id,  Map<String, String> txx) {
        if (invalid(id)) {
            return Uni.createFrom().item(Response.status(404).build());
        }
        var result = processTransacao(id, txx);
        var resp = result.onItem().transform(r -> r != null ? 
              Response.ok(r.body()).status(r.statusCode()).build() 
            : Response.status(422).build());                                      
        return resp;  
    }

	private Uni<Result> processTransacao(Integer id, Map<String, String> txx) {
        var valorNumber = txx.get("valor");
		if (valorNumber == null || valorNumber.contains(".")) {
            return Uni.createFrom().item(Result.of("{\"err\": \"valor\"}", 422));
        }

        Integer valor = null;
        try {
            valor = Integer.parseInt((String) valorNumber);
        } catch (NumberFormatException e) {
            return Uni.createFrom().item(Result.of("{\"err\": \"valor\"}", 422));
        }

        String tipo = txx.get("tipo");
        if (tipo == null || !("c".equals(tipo) || "d".equals(tipo))) {
            return Uni.createFrom().item(Result.of("{\"err\": \"tipo\"}", 422));
        }

        var descricao = txx.get("descricao");
        if (descricao == null || descricao.isEmpty() || descricao.length() > 10 || "null".equals(descricao)) {
            return Uni.createFrom().item(Result.of("{\"err\": \"descricao\"}", 422));
        }

        var shard = 0;

        var query = client.preparedQuery(TRANSACAO_QUERY).execute(Tuple.of(shard, id, valor, tipo, descricao));
        Uni<Result> result = query.onItem().transform(RowSet::iterator) 
            .onItem().transform(iterator -> iterator.hasNext() ? iterator.next() : null)
            .onItem().transform(data -> data != null ? Result.of(data) : null); 
        return result;
	}
}