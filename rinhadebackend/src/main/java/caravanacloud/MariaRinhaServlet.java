package caravanacloud;

import static jakarta.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static jakarta.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
import static jakarta.servlet.http.HttpServletResponse.SC_NOT_FOUND;

import java.io.BufferedReader;
import java.io.IOException;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.sql.*;
import java.time.*;


import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.transaction.Transactional;

@WebServlet(value = "/*")
@Transactional
public class MariaRinhaServlet extends HttpServlet {
    private static final String EXTRATO_CALL = "{CALL proc_extrato(?, ?, ?)}";
    private static final String TRANSACAO_CALL = "{CALL proc_transacao(?, ?, ?, ?, ?, ?, ?)}";
    private static final String WARMUP_QUERY = "SELECT 1+1";
    private static final String valorPattern = "\"valor\":\\s*(\\d+(\\.\\d+)?)";
    private static final String tipoPattern = "\"tipo\":\\s*\"([^\"]*)\"";
    private static final String descricaoPattern = "\"descricao\":\\s*(?:\"([^\"]*)\"|null)";

    private static final Pattern pValor = Pattern.compile(valorPattern);
    private static final Pattern pTipo = Pattern.compile(tipoPattern);
    private static final Pattern pDescricao = Pattern.compile(descricaoPattern);

    @Inject
    DataSource ds;

    public void onStartup(@Observes StartupEvent event) {
        Log.info("Pocó 🐔💥");
        var ready = false;
        do {
            try {
                warmup();
                processExtrato(1, null);
                //postTransacao(1, 0, "c", "onStartup", null);
                ready = true;
            } catch (Exception e) {
                Log.errorf(e, "Warmup failed [%s], waiting for db", e.getMessage());
                ready = false;
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException ie) {
                    ie.printStackTrace();
                }
            }
        } while (!ready);
    }

    private void warmup() throws SQLException {
        try (var conn = ds.getConnection();
             var stmt = conn.prepareStatement(WARMUP_QUERY)) {
            stmt.execute();
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        var id = getId(req, resp);
        if (id != null) {
            processExtrato(id, resp);
        } else {
            sendError(resp, SC_NOT_FOUND, "Cliente nao encontrado");
        }
    }

    private Integer getId(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        var pathInfo = req.getPathInfo();
        var id = pathInfo.split("/")[2];
        try {
            var result = Integer.valueOf(id);
            if (result <= 0 || result > 5) {
                return null;
            }
            return result;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void processExtrato(Integer id, HttpServletResponse resp) throws IOException {
        try (var conn = ds.getConnection();
             var cstmt = conn.prepareCall(EXTRATO_CALL)) {
            //conn.setAutoCommit(false);
            //conn.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
            cstmt.setInt(1, id);
            cstmt.registerOutParameter(2, Types.VARCHAR); // For the JSON result
            cstmt.registerOutParameter(3, Types.INTEGER); // For the status code
            cstmt.execute();

            var result = cstmt.getString(2);
            var status = cstmt.getInt(3);

            if (resp != null && result != null) {
                Log.infof("EXTRATO %s \n %s",id, result);
                resp.setStatus(status);
                resp.setContentType("application/json");
                if (! result.contains("total")){
                    Log.error("****** TOTAL NAO ENCONTRADO NA RESPOSTA *****");
                    throw new RuntimeException("****** TOTAL NAO ENCONTRADO NA RESPOSTA *****");
                }
                resp.getWriter().write(result);
            } else {
                sendError(resp, SC_NOT_FOUND, "Extrato nao encontrado");
            }
        } catch (SQLException e) {
            handleSQLException(e, resp);
        }
    }

    @Override
    public void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        var id = getId(req, resp);
        if (id == null) {
            sendError(resp, SC_NOT_FOUND, "Cliente nao encontrado");
            return;
        }
        StringBuilder requestBody = new StringBuilder();
        try (BufferedReader reader = req.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) {
                requestBody.append(line);
            }
        }

        String json = requestBody.toString();
        Matcher mValor = pValor.matcher(json);
        Matcher mTipo = pTipo.matcher(json);
        Matcher mDescricao = pDescricao.matcher(json);

        if (mValor.find() && mTipo.find() && mDescricao.find()) {
            String valorStr = mValor.group(1);
            Integer valor = null;
            try {
                valor = Integer.parseInt(valorStr);
            }catch (NumberFormatException e){
                sendError(resp, 422 , "Valor invalido");
                return;
            }
            String tipo = mTipo.group(1);
            if (! tipo.equals("c") && ! tipo.equals("d")) {
                sendError(resp, 422 , "Tipo invalido");
                return;
            }
            String descricao = mDescricao.group(1);
            if (descricao == null || descricao.isEmpty() || descricao.length() > 10) {
                sendError(resp, 422 , "Descricao invalido");
                return;
            }
            postTransacao(id, valor, tipo, descricao, resp);
        } else {
            sendError(resp, SC_BAD_REQUEST, "Corpo da requisição JSON inválido ou incompleto.");
        }
    }

    private void postTransacao(Integer id, Integer valor, String tipo, String descricao, HttpServletResponse resp)
            throws IOException {
        try (var conn = ds.getConnection();
             var cstmt = conn.prepareCall(TRANSACAO_CALL)) {
            //conn.setAutoCommit(false);
            //conn.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
            var realizada_em = Timestamp.valueOf(LocalDateTime.now());
            cstmt.setInt(1, id);
            cstmt.setInt(2, valor);
            cstmt.setString(3, tipo);
            cstmt.setString(4, descricao);
            cstmt.setTimestamp(5, realizada_em);
            cstmt.registerOutParameter(6, Types.VARCHAR); // For the JSON body
            cstmt.registerOutParameter(7, Types.INTEGER); // For the status code
            cstmt.execute();

            var body = cstmt.getString(6);
            var status = cstmt.getInt(7);

            if (resp != null) {
                Log.infof("TRANSACAO %s %s %s %s %s \n %s",id,valor,tipo,descricao,realizada_em, body);
                resp.setStatus(status);
                resp.setContentType("application/json");
                resp.getWriter().write(body);
            }
        } catch (SQLException e) {
            handleSQLException(e, resp);
        }
    }

    private void sendError(HttpServletResponse resp, int sc, String msg) throws IOException {
        Log.tracef("[%s] %s", sc, msg);
        if (resp != null) {
            resp.sendError(sc, msg);
        }
    }

    private void handleSQLException(SQLException e, HttpServletResponse resp) throws IOException {
        var msg = e.getMessage();
        if (msg.contains("LIMITE_INDISPONIVEL")) {
            sendError(resp, SC_BAD_REQUEST, "Erro: Limite indisponivel");
        } else if (msg.contains("CLIENTE_NAO_ENCONTRADO")) {
            sendError(resp, SC_NOT_FOUND, "Erro: Cliente inexistente");
        } else {
            sendError(resp, SC_INTERNAL_SERVER_ERROR, "Erro SQL ao manipular a transacao: " + e.getMessage());
        }
    }
}
