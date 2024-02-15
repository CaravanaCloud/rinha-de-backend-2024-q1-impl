package caravanacloud;

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

@Path("/clientes/{id}/extrato")
public class ExtratoResource {

    @Inject
    DataSource ds;

    // curl -v -X GET http://localhost:9999/clientes/1/extrato
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Transactional // TODO Verificar: Transactional para select??? é necessário???
    public Response getExtrato(
            @PathParam("id") Integer id) {
        Log.tracef("Extrato solicitado: %s ", id);

        
        var query = "select * from proc_extrato(?)";

        try (var conn = ds.getConnection();
                var stmt = conn.prepareStatement(query);) {
            stmt.setInt(1, id);
            stmt.execute();
            try (var rs = stmt.getResultSet()) {
                if (rs.next()) {
                    var result = rs.getString(1);
                    // TODO Verificar: o try-with-resources não fecha o PreparedStatement ????
                    // public interface PreparedStatement extends Statement {
                    // public interface Statement extends Wrapper, AutoCloseable {
                    stmt.close(); 
                    return Response.ok(result).build();
                } else {
                    return Response.status(Status.NOT_FOUND).entity("Extrato nao encontrado").build();
                }
            }
        } catch (SQLException e) {
            var msg = e.getMessage();
            if (msg.contains("CLIENTE_NAO_ENCONTRADO")) {
                return Response.status(Status.NOT_FOUND).entity("Cliente nao encontrado").build();
            }
            //e.printStackTrace();
            // throw new WebApplicationException("Erro SQL ao processar a transacao", 500);
            Log.debug("Erro ao processar a transacao", e);
            return Response.status(Status.INTERNAL_SERVER_ERROR).entity("Erro SQL ao processar a transacao").build();
        } catch (Exception e) {
            //e.printStackTrace();
            Log.error("Erro ao processar a transacao", e);
            //throw new WebApplicationException("Erro ao processar a transacao", 500);
            return Response.status(Status.INTERNAL_SERVER_ERROR).entity("Erro SQL ao processar a transacao").build();
        }
    }
}
