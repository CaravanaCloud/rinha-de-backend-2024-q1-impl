package caravanacloud;

import java.io.BufferedReader;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.regex.Pattern;

import javax.management.RuntimeErrorException;
import javax.sql.DataSource;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.inject.Inject;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@WebServlet("/*")
public class RinhaServlet extends HttpServlet {
    private static final Pattern EXTRATO_PATTERN = Pattern.compile("^/clientes/(\\d+)/(extrato)$");
    private static final Pattern TRANSACAO_PATTERN = Pattern.compile("^/clientes/(\\d+)/(transacoes)$"); 
    private static final String EXTRATO_QUERY = "select * from proc_extrato(?)";
    private static final String TRANSACAO_QUERY =  "select * from proc_transacao(?, ?::json)";

    @Inject
    DataSource ds;

    // curl -v -X GET http://localhost:9999/clientes/1/extrato
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        var pathInfo = req.getPathInfo();
        if (pathInfo == null) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Resource not found");
            return;
        }

        // Use the static pattern to match the path info
        var matcher = EXTRATO_PATTERN.matcher(pathInfo);

        if (matcher.matches()) {
            var id = Integer.valueOf(matcher.group(1));
            var action = matcher.group(2);

            if ("extrato".equals(action)) {
                processExtrato(id, resp);
                return;
            }
        }
        // If the request does not match the expected format
        resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Invalid URL format or resource not found");
    }

    private void processExtrato(Integer id, HttpServletResponse resp) throws IOException{
        try (var conn = ds.getConnection();
             var stmt = conn.prepareStatement(EXTRATO_QUERY)) {
            conn.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
            conn.setAutoCommit(false);
            stmt.setInt(1, id);
            stmt.execute();
            try (var rs = stmt.getResultSet()) {
                if (rs.next()) {
                    var result = rs.getString(1);
                    resp.setContentType("application/json");
                    resp.setCharacterEncoding("UTF-8");
                    resp.getWriter().write(result);
                    conn.commit();
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
                resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Erro SQL ao processar a transacao: "+e.getMessage());
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
        if (pathInfo == null) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Resource not found");
            return;
        }

        // Use the static pattern to match the path info
        var matcher = TRANSACAO_PATTERN.matcher(pathInfo);
        
        
        StringBuilder buf = new StringBuilder();
        String line;
        try (BufferedReader reader = req.getReader()) {
            while ((line = reader.readLine()) != null) {
                buf.append(line).append('\n');
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        
        // Convert StringBuilder to String
        String txx = buf.toString();

        if (matcher.matches()) {
            var id = Integer.valueOf(matcher.group(1));
            var action = matcher.group(2);

            if ("transacoes".equals(action)) {
                postTransacao(id, txx, resp);
                return;
            }
        }

        resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Invalid URL format or resource not found");
    }

    private void postTransacao(Integer id, String txx, HttpServletResponse resp) throws IOException {
        try (var conn = ds.getConnection();
             var stmt = conn.prepareStatement(TRANSACAO_QUERY)) {
            conn.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
            conn.setAutoCommit(false);
            stmt.setInt(1, id);
            stmt.setString(2, txx);
            stmt.execute();

            try (var rs = stmt.getResultSet()) {
                if (rs.next()) {
                    var status = rs.getInt(1);
                    if (status == 200){
                        var body = rs.getString(2);                    
                        resp.setContentType("application/json");
                        resp.setCharacterEncoding("UTF-8");
                        resp.getWriter().write(body);
                        conn.commit();
                    }else {
                        resp.sendError(status, "Erro ao processar a transacao " + status);    
                    }
                } else {
                    resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Erro ao processar a transacao");
                }
            }catch(Exception e){
                e.printStackTrace();
                conn.rollback();
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
            e.printStackTrace();
            resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Erro SQL ao processar a transacao "+e.getMessage());
        }
    }
}