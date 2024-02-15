package caravanacloud;

import java.util.Map;

import javax.sql.DataSource;

import io.quarkus.logging.Log;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

// ??? TODO Application Resource or Smallrye Health Plugin??? https://pt.quarkus.io/guides/smallrye-health
// ??? TODO Readiness and / or Liveness Probe ???
@Path("/_hc")
public class HealthCheckResource {
    private static final int TIMEOUT = 5;
    
    @Inject
    DataSource ds;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String,String> healthCheck() {
        try(var conn = ds.getConnection()){            
            var result = String.valueOf(conn.isValid(TIMEOUT));
            return Map.of("healthy", result);
        } catch (Exception e) {
            Log.error("[Health Check Failed] Database connection failed");
            return Map.of("healthy", Boolean.toString(false));
        }
    }
    
}
