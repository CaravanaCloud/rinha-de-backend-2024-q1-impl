package caravanacloud;

import static jakarta.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static jakarta.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
import static jakarta.servlet.http.HttpServletResponse.SC_NOT_FOUND;

import java.io.BufferedReader;
import java.io.IOException;
import java.sql.SQLException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.sql.DataSource;

import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.transaction.Transactional;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.narayana.jta.RunOptions;
import io.quarkus.narayana.jta.TransactionExceptionResult;

@WebServlet(value = "/*")
public class PostgreSQLServlet extends HttpServlet {
    private static final String EXTRATO_QUERY = "select * from proc_extrato(?)";
    private static final String TRANSACAO_QUERY = "select * from proc_transacao(?, ?, ?, ?, ?)";
    private static final String WARMUP_QUERY = "select 1+1;";
    private static final String valorPattern = "\"valor\":\\s*(\\d+(\\.\\d+)?)";
    private static final String tipoPattern = "\"tipo\":\\s*\"([^\"]*)\"";
    private static final String descricaoPattern = "\"descricao\":\\s*(?:\"([^\"]*)\"|null)";

    private static final Pattern pValor = Pattern.compile(valorPattern);
    private static final Pattern pTipo = Pattern.compile(tipoPattern);
    private static final Pattern pDescricao = Pattern.compile(descricaoPattern);
    private static final int RETRIES_MAX = 10;

    private Integer shard;

    @Inject
    DataSource ds;

    @PostConstruct
    public void init() {
        this.shard = envInt("RINHA_SHARD", 0);
        Log.info("PostConstruct shard[" + shard + "] 🐔💥");
    }

    public void onStartup(@Observes StartupEvent event) {
        Log.info("StartupEvent shard[" + shard + "] 🐔💥");
        var ready = false;
        // create json node
        do {
            try {
                warmup();
                tryTransacao(333, "0", "c", "onStartup", null);
                tryExtrato(333, null);
                ready = true;
            } catch (Exception e) {
                Log.errorf(e, "Warmup failed [%s], waiting for db", e.getMessage());
                ready = false;
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e1) {
                    e1.printStackTrace();
                }
            }
        } while (!ready);
    }

    private static Integer envInt(String varName, int defaultVal) {
        var result = System.getenv(varName);
        if (result == null) {
            Log.info("Env var " + varName + " not found, using default " + defaultVal);
            return defaultVal;
        }
        try {
            var inte = Integer.valueOf(result);
            Log.info("Env var " + varName + " found, using " + result);
            return inte;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private void warmup() throws SQLException {
        var query = getEnv("RINHA_WARMUP_QUERY", WARMUP_QUERY);
        try (var conn = ds.getConnection();
                var stmt = conn.prepareStatement(query)) {
            stmt.execute();
        }
    }

    private static String getEnv(String varName, String defaultVal) {
        var result = System.getenv(varName);
        if (result == null) {
            result = defaultVal;
        }
        return result;
    }

    // curl -v -X GET http://localhost:9999/clientes/1/extrato
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        var id = getId(req, resp);
        if (id != null) {
            tryExtrato(id, resp);
        } else {
            sendError(resp, SC_NOT_FOUND, "Cliente nao encontrado");
        }
    }

    private Integer getId(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        var pathInfo = req.getPathInfo();
        if (pathInfo == null || pathInfo.length() < 10) {
            return null;
        }
        var id = pathInfo.substring(10, 11);
        // var id = pathInfo.split("/")[2];
        var validId = "1".equals(id) || "2".equals(id) || "3".equals(id) || "4".equals(id) || "5".equals(id);
        if (!validId) {
            return null;
        }
        return Integer.valueOf(id);
    }

    private void tryExtrato(Integer id, HttpServletResponse resp) {
        var retries = 0;
        do {
            try {
                int result = QuarkusTransaction.requiringNew()
                        .call(() -> {
                            doExtrato(id, resp);
                            return 0;
                        });
                if (result == 0)
                    return;
            } catch (Exception e) {
                Log.warnf(e, "Error on transacao [%s], retrying", e.getMessage());
                nap();
            }
            retries++;
        } while (retries < RETRIES_MAX);
        sendError(resp, SC_INTERNAL_SERVER_ERROR, "Retry limit exceeded");
    }

    private void nap() {
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    private void doExtrato(Integer id, HttpServletResponse resp) throws SQLException, IOException {
        try (var conn = ds.getConnection();
                // var lock = conn.prepareStatement("SELECT pg_advisory_xact_lock(?)");
                var stmt = conn.prepareStatement(EXTRATO_QUERY)) {

            // var isolation = conn.getTransactionIsolation();
            // Log.infof("Isolation level: %s on extrato", isolation);

            // lock.setInt(1, id);
            // lock.execute();
            stmt.setInt(1, id);
            // debug();
            stmt.execute();
            try (var rs = stmt.getResultSet()) {
                if (resp == null)
                    return;
                if (rs.next()) {
                    var result = rs.getString("body");
                    var status = rs.getInt("status_code");
                    resp.setStatus(status);
                    resp.setContentType("application/json");
                    resp.getWriter().write(result);
                } else {
                    sendError(resp, SC_NOT_FOUND, "Extrato nao encontrado");
                }
            }
        }
    }

    private void sendError(HttpServletResponse resp, int sc, String msg) {
        if (sc == 500)
            Log.warn(msg);
        if (resp != null)
            try {
                resp.sendError(sc, msg);
            } catch (Exception e) {
                Log.errorf(e, "Error sending error %s", e.getMessage());
            }

        else
            Log.warnf("[%s] %s", sc, msg);
    }

    // curl -v -X POST -H "Content-Type: application/json" -d '{"valor": 100,
    // "tipo": "c", "descricao": "Deposito"}'
    // http:///localhost:9999/clientes/1/transacoes
    @Override
    public void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        var id = getId(req, resp);
        if (id == null) {
            sendError(resp, SC_NOT_FOUND, "Cliente nao encontrado");
            return;
        }
        StringBuilder requestBody = new StringBuilder();
        String line;
        try (BufferedReader reader = req.getReader()) {
            while ((line = reader.readLine()) != null) {
                requestBody.append(line);
            }
        } catch (Exception e) {
            sendError(resp, SC_BAD_REQUEST, "Invalid request body " + e.getMessage());
            return;
        }

        String json = requestBody.toString();

        Matcher mValor = pValor.matcher(json);
        Matcher mTipo = pTipo.matcher(json);
        Matcher mDescricao = pDescricao.matcher(json);

        if (mValor.find() && mTipo.find() && mDescricao.find()) {
            // Os valores foram extraídos com sucesso
            String valor = mValor.group(1);
            String tipo = mTipo.group(1);
            String descricao = mDescricao.group(1);

            // QuarkusTransaction.requiringNew().run(() -> doTransacao(id, valor, tipo,
            // descricao, resp));
            tryTransacao(id, valor, tipo, descricao, resp);

        } else {
            sendError(resp, 422, "Corpo da requisição JSON inválido ou incompleto.");
        }
        return;
    }

    private void tryTransacao(Integer id, String valorNumber, String tipo, String descricao, HttpServletResponse resp) {
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
        int valorFinal = valor;

        if (tipo == null || !("c".equals(tipo) || "d".equals(tipo))) {
            if (resp != null)
                sendError(resp, 422, "Tipo invalido");
            return;
        }

        if (descricao == null || descricao.isEmpty() || descricao.length() > 10 || "null".equals(descricao)) {
            if (resp != null)
                sendError(resp, 422, "Descricao invalida");
            return;
        }

        var retries = 0;
        do {
            try {
                int result = QuarkusTransaction.requiringNew()
                        .call(() -> {
                            doTransacao(id, valorFinal, tipo, descricao, resp);
                            return 0;
                        });
                if (result == 0)
                    return;
            } catch (Exception e) {
                Log.warnf("Error on transacao [%s], retrying: %s", e.getMessage());
                nap();
            }
            retries++;
        } while (retries < RETRIES_MAX);
        sendError(resp, SC_INTERNAL_SERVER_ERROR, "Retry limit exceeded");
    }

    private void doTransacao(Integer id, int valor, String tipo, String descricao, HttpServletResponse resp)
            throws SQLException, IOException {
        try (var conn = ds.getConnection();
                /// var lock = conn.prepareStatement("SELECT pg_advisory_xact_lock(?)");
                var stmt = conn.prepareStatement(TRANSACAO_QUERY)) {

            //
            // lock.setInt(1, id);
            // lock.execute();

            stmt.setInt(1, shard);
            stmt.setInt(2, id);
            stmt.setInt(3, valor);
            stmt.setString(4, tipo);
            stmt.setString(5, descricao);
            stmt.execute();
            try (var rs = stmt.getResultSet()) {
                if (rs.next()) {
                    var body = rs.getString("body");
                    var status = rs.getInt("status_code");
                    if (resp == null)
                        return;
                    resp.setStatus(status);
                    resp.setHeader("x-rinha-shard", shard.toString());
                    resp.setContentType("application/json");
                    resp.setCharacterEncoding("UTF-8");
                    resp.getWriter().write(body);
                } else {
                    sendError(resp, SC_INTERNAL_SERVER_ERROR, "No next result from transacao query");
                }
            }
        }
    }
}
