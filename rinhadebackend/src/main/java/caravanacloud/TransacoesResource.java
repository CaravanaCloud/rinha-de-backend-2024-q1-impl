package caravanacloud;

import java.sql.SQLException;
import java.util.Map;

import javax.sql.DataSource;

import io.quarkus.logging.Log;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;

@Path("/clientes/{id}/transacoes")
public class TransacoesResource {
    @Inject
    DataSource ds;

    //curl -v -X POST -H "Content-Type: application/json" -d '{"valor": 100, "tipo": "c", "descricao": "Compra de pao"}' http://localhost:9999/clientes/1/transacoes
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Transactional
    public Response postTransacao(
            @PathParam("id") Integer id,
            Map<String, Object> t) {
        Log.tracef("Transacao recebida: %s %s ", id, t);

        var valorNumber = (Number) t.get("valor");
        if (valorNumber == null || !Integer.class.equals(valorNumber.getClass())) {
            return Response.status(422).entity("Valor invalido").build();
        }
        Integer valor = valorNumber.intValue();

        var tipo = (String) t.get("tipo");
        if (tipo == null || !("c".equals(tipo) || "d".equals(tipo))) {
            return Response.status(422).entity("Tipo invalido").build();
        }

        var descricao = (String) t.get("descricao");
        if (descricao == null || descricao.isEmpty() || descricao.length() > 10) {
            return Response.status(422).entity("Descricao invalida").build();
        }

        // Adjusted for MySQL
        var query = "CALL proc_transacao(?, ?, ?, ?)";

        try (var conn = ds.getConnection();
             var stmt = conn.prepareCall(query)) {
            stmt.setInt(1, id);
            stmt.setInt(2, valor);
            stmt.setString(3, tipo);
            stmt.setString(4, descricao);
            boolean hasResults = stmt.execute();
            if (hasResults) {
                try (var rs = stmt.getResultSet()) {
                    if (rs.next()) {
                        Integer saldo = rs.getInt("saldo");
                        Integer limite = rs.getInt("limite");

                        if (saldo < -1 * limite) {
                            Log.error("*** LIMITE ULTRAPASSADO " + saldo + " / " + limite);
                            Log.error(t.toString());
                        }

                        var body = Map.of("limite", limite, "saldo", saldo);
                        return Response.ok(body).build();
                    } else {
                        return Response.status(500).entity("Erro ao processar a transacao").build();
                    }
                }
            } else {
                // This block might need adjustment based on your procedure's expected behavior
                return Response.status(500).entity("Erro ao processar a transacao").build();
            }
        } catch (SQLException e) {
            var msg = e.getMessage();
            if (msg.contains("LIMITE_INDISPONIVEL")) {
                return Response.status(422).entity("Erro: Limite indisponivel").build();
            }
            if (msg.contains("fk_clientes_transacoes_id")) {
                return Response.status(Status.NOT_FOUND).entity("Erro: Cliente inexistente").build();
            }
            Log.debug("Erro ao processar a transacao", e);
            return Response.status(Status.INTERNAL_SERVER_ERROR).entity("Erro SQL ao processar a transacao").build();
        } catch (Exception e) {
            Log.error("Erro ao processar a transacao", e);
            return Response.status(Status.INTERNAL_SERVER_ERROR).entity("Erro ao processar a transacao").build();
        }
    }
}
