package caravanacloud.mysql;

import java.sql.Connection;
import java.sql.SQLException;

import javax.sql.DataSource;

import io.quarkus.logging.Log;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;

@Path("/mysql/clientes/{id}/extrato")
public class ExtratoResource {

    @Inject
    DataSource ds;

    // curl -v -X GET http://localhost:9999/clientes/1/extrato
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Transactional(Transactional.TxType.NEVER) 
    // TODO Verificar: Transactional para select??? é necessário???
    public Response getExtrato(
            @PathParam("id") Integer id) {
        Log.tracef("Extrato solicitado: %s ", id);

        // psql var query = "select * from proc_extrato(?)";
        // mysql
        var query = "call proc_extrato(?)";

        try (var conn = ds.getConnection();
                // psql var stmt = conn.prepareStatement(query);) {
                var stmt = conn.prepareCall(query);) {
            conn.setAutoCommit(false);
            conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            stmt.setInt(1, id);
            boolean hasResults = stmt.execute();
            if (hasResults) {
                try (var rs = stmt.getResultSet()) {
                    if (rs.next()) {
                        var result = rs.getString(1); // Assuming the procedure returns a single string JSON as output
                        //TODO: avoid bad json
                        // result = result.replaceAll("\\\\", "");
                        return Response.ok(result).build();
                    } else {
                        return Response.status(Status.NOT_FOUND).entity("Extrato nao encontrado").build();
                    }
                }
            } else {
                return Response.status(Status.NOT_FOUND).entity("Extrato nao encontrado").build();
            }
        } catch (SQLException e) {
            var msg = e.getMessage();
            if (msg.contains("CLIENTE_NAO_ENCONTRADO")) {
                return Response.status(Status.NOT_FOUND).entity("Cliente nao encontrado").build();
            }
            e.printStackTrace();
            Log.debug("Erro ao processar a transacao", e);
            return Response.status(Status.INTERNAL_SERVER_ERROR).entity("Erro SQL ao processar a transacao").build();
        } catch (Exception e) {
             e.printStackTrace();
            Log.error("Erro ao processar a transacao", e);
            return Response.status(Status.INTERNAL_SERVER_ERROR).entity("Erro SQL ao processar a transacao").build();
        }
    }
}
