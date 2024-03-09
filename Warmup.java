import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;

public class Warmup {
    public static void main(String[] args) throws IOException, InterruptedException {
        System.out.println("Warming up...");
        HttpClient client = HttpClient.newHttpClient();

        // HTTP GET request
        HttpRequest getRequest = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:9999/clientes/333/extrato"))
                .build();

        HttpResponse<String> getResponse = client.send(getRequest, BodyHandlers.ofString());
        System.out.println("GET Response: " + getResponse.statusCode());

        // HTTP POST request
        HttpRequest postRequest = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:9999/clientes/333/transacoes"))
                .header("Content-Type", "application/json")
                .POST(BodyPublishers.ofString("{\"valor\": 0, \"tipo\": \"d\", \"descricao\": \"warmup\"}"))
                .build();

        HttpResponse<String> postResponse = client.send(postRequest, BodyHandlers.ofString());
        System.out.println("POST Response: " + postResponse.statusCode());
        System.out.println("Warmup DONE!");
    }
}
