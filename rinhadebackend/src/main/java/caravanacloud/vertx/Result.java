package caravanacloud.vertx;

import java.util.function.Supplier;

import io.quarkus.logging.Log;
import io.vertx.core.json.JsonObject;
import io.vertx.mutiny.sqlclient.Row;

public record Result(JsonObject body, int statusCode) {
    public static Result of(Row row) {
        var status = row.getInteger("status_code");
        if (status == 200){
            var body = row.getJsonObject("body");
            return new Result(body, status);
        }
        return new Result(new JsonObject("{\"err\": \"nobody\"}"), status);
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
