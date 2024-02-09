package caravanacloud;

import java.sql.SQLException;
import java.sql.Types;
import java.util.Map;

import javax.sql.DataSource;

import io.quarkus.logging.Log;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/clientes/{id}/transacoes")
public class TransacoesResource {
    @Inject
    DataSource ds;

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Transactional
    public Response postTransacao(
            @PathParam("id") Integer id,
            Map<String, Object> t) {
        Log.tracef("Transacao recebida: %s %s ", id, t);

        var valorNumber = (Number) t.get("valor");
        if (valorNumber == null
                || !Integer.class.equals(valorNumber.getClass())) {
            throw new WebApplicationException("Valor invalido", 422);
        }
        Integer valor = valorNumber.intValue();

        var tipo = (String) t.get("tipo");
        if (tipo == null
                || !("c".equals(tipo) || "d".equals(tipo))) {
            throw new WebApplicationException("Tipo invalido", 422);
        }

        var descricao = (String) t.get("descricao");
        if (descricao == null) {
            descricao = "";
        }

        var query = "select * from proc_transacao(?, ?, ?, ?)";

        try (var conn = ds.getConnection();
                var stmt = conn.prepareStatement(query);) { // TODO cache statement?
            stmt.setInt(1, id);
            stmt.setInt(2, valor);
            stmt.setString(3, tipo);
            stmt.setString(4, descricao);
            stmt.execute();
            try (var rs = stmt.getResultSet()) {
                if (rs.next()) {
                    Integer saldo = rs.getInt("saldo");
                    Integer limite = rs.getInt("limite");
                    var body = Map.of("limite", limite,
                            "saldo", saldo);
                    stmt.close();
                    return Response.ok(body).build();
                } else {
                    throw new WebApplicationException("Erro ao processar a transacao", 500);
                }
            }
        } catch (SQLException e) {
            var msg = e.getMessage();
            if (msg.contains("LIMITE_INDISPONIVEL")) {
                throw new WebApplicationException("Limite indisponivel", 422);
            }
            if (msg.contains("fk_clientes_transacoes_id")) {
                throw new WebApplicationException("Cliente inexistente", 404);
            }
            e.printStackTrace();
            throw new WebApplicationException("Erro SQL ao processar a transacao", 500);
        } catch (Exception e) {
            e.printStackTrace();
            Log.debug("Erro ao processar a transacao", e);
            throw new WebApplicationException("Erro ao processar a transacao", 500);
        }
    }

}
