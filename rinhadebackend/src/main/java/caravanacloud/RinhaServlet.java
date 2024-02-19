package caravanacloud;

import static jakarta.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static jakarta.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
import static jakarta.servlet.http.HttpServletResponse.SC_NOT_FOUND;

import java.io.BufferedReader;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Map;
import java.util.Set;

import javax.sql.DataSource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@WebServlet(value = "/*")
public class RinhaServlet extends HttpServlet {
    private static final String EXTRATO_QUERY = "select * from proc_extrato(?)";
    private static final String TRANSACAO_QUERY = "select * from proc_transacao(?, ?, ?, ?)";
    private static final String WARMUP_QUERY =  "SELECT pg_prewarm('transacoes');";
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final Set<String> validIds = Set.of("1", "2", "3", "4", "5");

    @Inject
    DataSource ds;

    public void onStartup(@Observes StartupEvent event) {
        Log.info("Warming up....");
        var ready = false;
        // create json node
        var txx = objectMapper.createObjectNode()
                .put("valor", 0)
                .put("tipo", "c")
                .put("descricao", "warmup");
        do {
            try {
                warmup();
                processExtrato(1, null);
                postTransacao(1, txx,
                        null);
                ready = true;
            } catch (Exception e) {
                Log.errorf(e, "Warmup failed [%s], waiting for db", e.getMessage());
                ready = false;
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e1) {
                    // TODO Auto-generated catch block
                    e1.printStackTrace();
                }
            }
        } while (!ready);
    }

    private void warmup() {
        try (var conn = ds.getConnection();
                var stmt = conn.prepareStatement(WARMUP_QUERY)) {
            stmt.execute();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // curl -v -X GET http://localhost:9999/clientes/1/extrato
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        var pathInfo = req.getPathInfo();
        // var id = pathInfo.substring(10,11);
        var id = pathInfo.split("/")[2];
        if  (! validIds.contains(id)) {
            sendError(resp, SC_NOT_FOUND, "Cliente nao encontrado");
            return;
        }
        // Log.info(pathInfo + " => " + id);
        processExtrato(Integer.valueOf(id), resp);
    }

    private void processExtrato(Integer id, HttpServletResponse resp) throws IOException {
        try (var conn = ds.getConnection();
                var stmt = conn.prepareStatement(EXTRATO_QUERY)) {
            stmt.setInt(1, id);
            stmt.execute();
            try (var rs = stmt.getResultSet()) {
                if (resp == null)
                    return;
                if (rs.next()) {
                    var result = rs.getString(1);
                    resp.setContentType("application/json");
                    resp.getWriter().write(result);
                } else {
                    sendError(resp, SC_NOT_FOUND, "Extrato nao encontrado");
                }
            }
        } catch (SQLException e) {
            var msg = e.getMessage();
            if (msg.contains("CLIENTE_NAO_ENCONTRADO")) {
                sendError(resp, SC_NOT_FOUND, "Cliente nao encontrado");
            } else {
                e.printStackTrace();
                sendError(resp, SC_INTERNAL_SERVER_ERROR, "Erro SQL ao processar a transacao" + e.getMessage());
            }
        } catch (Exception e) {
            e.printStackTrace();
            sendError(resp, SC_INTERNAL_SERVER_ERROR, "Erro ao processar a transacao: " + e.getMessage());
        }
    }

    private void sendError(HttpServletResponse resp, int sc, String msg) throws IOException {
        if (sc == 500)
            Log.warn(msg);
        if (resp != null)
            resp.sendError(sc, msg);
        else
            Log.warnf("[%s] %s", sc, msg);
    }

    // curl -v -X POST -H "Content-Type: application/json" -d '{"valor": 100,
    // "tipo": "c", "descricao": "Deposito"}'
    // http:///localhost:9999/clientes/1/transacoes
    @Override
    public void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        var pathInfo = req.getPathInfo();
        // var id = pathInfo.substring(10,11);
        var id = pathInfo.split("/")[2];
        if  (! validIds.contains(id)) {
            sendError(resp, SC_NOT_FOUND, "Cliente nao encontrado");
            return;
        }
        // Log.info(pathInfo + " => " + id);
        JsonNode json;
        try (BufferedReader reader = req.getReader()) {
            json = objectMapper.readTree(reader);
        } catch (Exception e) {
            sendError(resp, SC_BAD_REQUEST, "Invalid request body " + e.getMessage());
            return;
        }
        postTransacao(Integer.valueOf(id), json, resp);
        return;
    }

    private void postTransacao(Integer id, JsonNode t, HttpServletResponse resp) throws IOException {
        // Validate and process the transaction as in the original resource
        var valorNumber = t.get("valor").asText();
        if (valorNumber == null || valorNumber.contains(".")) {
            if (resp != null)
                sendError(resp, 422, "Valor invalido");
            return;
        }

        Integer valor = null;
        try {
            valor = Integer.parseInt((String) valorNumber);
        } catch (NumberFormatException e) {
            if (resp != null)
                sendError(resp, 422, "Valor invalido");
            return;
        }

        var tipo = (String) t.get("tipo").asText();
        if (tipo == null || !("c".equals(tipo) || "d".equals(tipo))) {
            if (resp != null)
                sendError(resp, 422, "Tipo invalido");
            return;
        }

        var descricao = (String) t.get("descricao").asText();
        if (descricao == null || descricao.isEmpty() || descricao.length() > 10 || "null".equals(descricao)) {
            if (resp != null)
                sendError(resp, 422, "Descricao invalida");
            return;
        }

        try (var conn = ds.getConnection();
                var stmt = conn.prepareStatement(TRANSACAO_QUERY)) {
            stmt.setInt(1, id);
            stmt.setInt(2, valor);
            stmt.setString(3, tipo);
            stmt.setString(4, descricao);
            stmt.execute();

            try (var rs = stmt.getResultSet()) {
                if (resp == null)
                    return;
                if (rs.next()) {
                    Integer saldo = rs.getInt("saldo");
                    Integer limite = rs.getInt("limite");

                    var body = Map.of("limite", limite, "saldo", saldo);
                    if (resp == null)
                        return;
                    resp.setContentType("application/json");
                    resp.setCharacterEncoding("UTF-8");
                    var output = objectMapper.writeValueAsString(body);
                    resp.getWriter().write(output);
                } else {
                    sendError(resp, SC_INTERNAL_SERVER_ERROR, "No next result from transacao query");
                }
            }
        } catch (SQLException e) {
            handleSQLException(e, resp);
        } catch (Exception e) {
            sendError(resp, SC_INTERNAL_SERVER_ERROR, "Erro ao processar a transacao " + e.getMessage());
        }
    }

    private void handleSQLException(SQLException e, HttpServletResponse resp) throws IOException {
        var msg = e.getMessage();
        if (msg.contains("LIMITE_INDISPONIVEL")) {
            sendError(resp, 422, "Erro: Limite indisponivel");
        } else if (msg.contains("fk_clientes_transacoes_id")) {
            sendError(resp, SC_NOT_FOUND, "Erro: Cliente inexistente");
        } else {
            sendError(resp, SC_INTERNAL_SERVER_ERROR, "Erro SQL ao manipular a transacao: " + e.getMessage());
        }
    }
}