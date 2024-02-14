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
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/cached/clientes/{id}/extrato")
public class CachedExtratoResource {

    // curl -v -X GET http://localhost:9999/clientes/1/extrato
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Transactional
    public Response getExtrato(
            @PathParam("id") Integer id) {
        Log.tracef("Extrato solicitado: %s ", id);
        throw new WebApplicationException("TODO", 401);
    }
}
