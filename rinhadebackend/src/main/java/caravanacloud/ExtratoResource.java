package caravanacloud;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import io.quarkus.logging.Log;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/clientes/{id}/extrato")
public class ExtratoResource {

    @Inject
    DataSource ds;

    // curl -v -X GET http://localhost:9999/clientes/1/extrato
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Transactional
    public Response getExtrato(
            @PathParam("id") Integer id) {
        Log.tracef("Extrato solicitado: %s ", id);

        
        var query = "select * from proc_extrato(?)";

        try (var conn = ds.getConnection();
                var stmt = conn.prepareStatement(query);) { // TODO cache statement?
            stmt.setInt(1, id);
            stmt.execute();
            try (var rs = stmt.getResultSet()) {
                if (rs.next()) {
                    var result = rs.getString(1);
                    stmt.close();
                    return Response.ok(result).build();
                } else {
                    throw new WebApplicationException("Extrato nao encontrado", 404);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            throw new WebApplicationException("Erro SQL ao processar a transacao", 500);
        } catch (Exception e) {
            e.printStackTrace();
            Log.debug("Erro ao processar a transacao", e);
            throw new WebApplicationException("Erro ao processar a transacao", 500);
        }
    }
}
