package caravanacloud.vertx;

import java.util.function.Supplier;

import io.quarkus.logging.Log;
import io.vertx.core.json.JsonObject;
import io.vertx.mutiny.sqlclient.Row;

public record Result(JsonObject body, int statusCode) {
    public static Result of(Row row) {
        return new Result(row.getJsonObject("body"), row.getInteger("status_code"));
    }

    public static Result of( String jsonBody, int statusCode) {
        try {
            return new Result(new JsonObject(jsonBody), statusCode);
        }catch(Exception e){
            e.printStackTrace();
            Log.info("Error parsing json:\n " + jsonBody);
            throw new RuntimeException(e);
        }
    }
}
