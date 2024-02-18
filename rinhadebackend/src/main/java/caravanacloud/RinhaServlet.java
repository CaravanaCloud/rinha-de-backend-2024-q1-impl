package caravanacloud;

import java.io.BufferedReader;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Map;
import java.util.regex.Pattern;

import javax.sql.DataSource;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkus.logging.Log;
import jakarta.inject.Inject;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@WebServlet("/*")
public class RinhaServlet extends HttpServlet {
    private static final String EXTRATO_QUERY = "select * from proc_extrato(?)";
    private static final String TRANSACAO_QUERY =  "select * from proc_transacao(?, ?, ?, ?)";

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Inject
    DataSource ds;

    @Override
    public void init() throws ServletException {        
        super.init();
        Log.info("Warming up....");
        try {
            for (int i = 0; i < 6; i++){
                processExtrato(1,null);
                postTransacao(1, Map.of(
                    "valor", "0",
                    "tipo", "c",
                    "descricao", "warmup"
                ), 
                null);
            }
        }catch(Exception e){
            Log.errorf(e, "Warmup failed");
        }
    }
    
    // curl -v -X GET http://localhost:9999/clientes/1/extrato
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        var pathInfo = req.getPathInfo();
        var id = pathInfo.substring(12,13);
        processExtrato(Integer.valueOf(id), resp);
    }

    private void processExtrato(Integer id, HttpServletResponse resp) throws IOException{
        try (var conn = ds.getConnection();
             var stmt = conn.prepareStatement(EXTRATO_QUERY)) {
            stmt.setInt(1, id);
            stmt.execute();
            try (var rs = stmt.getResultSet()) {
                if (resp == null) return;
                if (rs.next()) {
                    var result = rs.getString(1);
                    resp.setContentType("application/json");
                    resp.setCharacterEncoding("UTF-8");
                    resp.getWriter().write(result);
                } else {
                    resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Extrato nao encontrado");
                }
            }
        } catch (SQLException e) {
            var msg = e.getMessage();
            if (msg.contains("CLIENTE_NAO_ENCONTRADO")) {
                resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Cliente nao encontrado");
            } else {
                e.printStackTrace();
                resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Erro SQL ao processar a transacao");
            }
        } catch (Exception e) {
            e.printStackTrace();
            resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Erro ao processar a transacao");
        }
    }

    // curl -v -X POST -H "Content-Type: application/json" -d '{"valor": 100, "tipo": "c", "descricao": "Deposito"}' http:///localhost:9999/clientes/1/transacoes
    @Override
    public void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        var pathInfo = req.getPathInfo();
        var id = pathInfo.substring(12,13);
        Map<String, Object> t;
        try (BufferedReader reader = req.getReader()) {
            t = objectMapper.readValue(reader, Map.class);
        } catch (Exception e) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid request body");
            return;
        }
        postTransacao(Integer.valueOf(id), t, resp);
        return;
    }

    private void postTransacao(Integer id, Map<String, Object> t, HttpServletResponse resp) throws IOException {
        // Validate and process the transaction as in the original resource
        var valorNumber = (Object) t.get("valor");
        if (valorNumber == null || !( valorNumber instanceof Integer)) {
            resp.sendError(422, "Valor invalido");
            return;
        }
        var valor = (Integer) valorNumber;

        var tipo = (String) t.get("tipo");
        if (tipo == null || !("c".equals(tipo) || "d".equals(tipo))) {
            resp.sendError(422, "Tipo invalido");
            return;
        }

        var descricao = (String) t.get("descricao");
        if (descricao == null || descricao.isEmpty() || descricao.length() > 10) {
            resp.sendError(422, "Descricao invalida");
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
                if (resp == null) return;
                if (rs.next()) {
                    Integer saldo = rs.getInt("saldo");
                    Integer limite = rs.getInt("limite");

                    var body = Map.of("limite", limite, "saldo", saldo);
                    resp.setContentType("application/json");
                    resp.setCharacterEncoding("UTF-8");
                    var output = objectMapper.writeValueAsString(body);
                    resp.getWriter().write(output);
                } else {
                    resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Erro ao processar a transacao");
                }
            }
        } catch (SQLException e) {
            handleSQLException(e, resp);
        } catch (Exception e) {
            resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Erro ao processar a transacao");
        }
    }

    private void handleSQLException(SQLException e, HttpServletResponse resp) throws IOException {
        var msg = e.getMessage();
        if (msg.contains("LIMITE_INDISPONIVEL")) {
            resp.sendError(422, "Erro: Limite indisponivel");
        } else if (msg.contains("fk_clientes_transacoes_id")) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Erro: Cliente inexistente");
        } else {
            resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Erro SQL ao processar a transacao");
        }
    }
}