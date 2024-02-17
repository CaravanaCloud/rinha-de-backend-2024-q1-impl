package caravanacloud;

import java.io.BufferedReader;
import java.io.IOException;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.regex.Pattern;

import javax.sql.DataSource;

import com.aayushatharva.brotli4j.common.annotations.Local;
import com.fasterxml.jackson.databind.ObjectMapper;

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

import org.infinispan.Cache;

@WebServlet("/*")
@Transactional
public class RinhaServlet extends HttpServlet {
    private static final Pattern EXTRATO_PATTERN = Pattern.compile("^/clientes/(\\d+)/(extrato)$");
    private static final Pattern TRANSACAO_PATTERN = Pattern.compile("^/clientes/(\\d+)/(transacoes)$");
    private static final String EXTRATO_QUERY = "select * from proc_extrato(?)";
    private static final String TRANSACAO_QUERY = "select * from proc_transacao(?, ?, ?, ?)";
    private static final String TRANSACOES_QUERY = "select * from transacoes where cliente_id = ? order by realizada_em desc limit 10";
    private static final String CLIENTE_QUERY = "select * from clientes where id = ?";
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Inject
    DataSource ds;

    @Inject
    Cache<Integer,Cliente> cache;

    @Override
    public void init() throws ServletException {
        super.init();
        Log.info("Warmimg....");
        try {
            processExtrato(1, null);
            postTransacao(1, Map.of(
                    "valor", 0,
                    "tipo", "c",
                    "descricao", "warmup"),
                    null);
        } catch (Exception e) {
            Log.errorf(e, "Warmuyp failed");
        }
    }

    // curl -v -X GET http://localhost:9999/clientes/1/extrato
    @Override
    protected synchronized void  doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
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

    private LinkedList<Transacao> loadTransacoes(Integer id) throws SQLException{
        var result = new LinkedList<Transacao>();
        try (var conn = ds.getConnection();
                var stmt = conn.prepareStatement(TRANSACOES_QUERY)){
            stmt.setInt(1, id);
            try (var rs = stmt.executeQuery()) {
                while (rs.next()) {
                    var valor = rs.getInt("valor");
                    var tipo = rs.getString("tipo");
                    var descricao = rs.getString("descricao");
                    var txx = Transacao.of(valor, tipo, descricao);
                    result.add(txx);
                }
            }
        }
        return result;
    }

    private Cliente loadCliente(Integer id) throws SQLException{
        try (var conn = ds.getConnection();
                var stmt = conn.prepareStatement(CLIENTE_QUERY)) {
            stmt.setInt(1, id);
            try (var rs = stmt.executeQuery()) {
                if (rs.next()) {
                    var saldo = rs.getInt("saldo");
                    var limite = rs.getInt("limite");
                    var cliente = Cliente.of(id, saldo, limite);
                    return cliente;
                } else {
                    Log.warn("No resultset resturned from proc_extrato");
                    return null;
                }
            }
        }   
    }

    private Cliente loadExtrato(Integer id) throws SQLException {
        var cliente = loadCliente(id);
        if (cliente == null) return null;
        var extrato = loadTransacoes(id);
        cliente.transacoes = extrato;
        return cliente;
    }
    

    private Cliente getExtrato(Integer id )  throws SQLException {
        var extrato = cache.get(id);
        if(extrato == null){
            Log.debugf("Cliente [%s] cache MISS, loading from database",id);
            extrato = loadExtrato(id);
            if (extrato == null){
                Log.infof("Failed to load extrato for cliente [%s]",id);
                return null;
            }
            cache.put(id, extrato);
        }else {
            Log.debugf("Cliente [%s] cache HIT, loading from database",id);

        }
        return extrato;
    } 

    private void processExtrato(Integer id, HttpServletResponse resp) throws IOException {
        try {
            var extrato = getExtrato(id);
             // Log.info("Loading database extrato");
            if (resp == null)
                return;
            if (extrato == null) {
                resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Cliente nao encontrado");
                return;
            }
            var map = Map.of(
                "saldo", Map.of(
                    "total", extrato.saldo,
                    "data_extrato", LocalDateTime.now().toString(),
                    "limite", extrato.limite
                ),
                "ultimas_transacoes", extrato.transacoes
            );
            var result = objectMapper.writeValueAsString(map);
            resp.setContentType("application/json");
            resp.setCharacterEncoding("UTF-8");
            resp.getWriter().write(result);
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
            if (resp == null)
                return;
            resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Erro ao processar a transacao");
        }
    }

    // curl -v -X POST -H "Content-Type: application/json" -d '{"valor": 100, "tipo": "c", "descricao": "Deposito"}' http:///localhost:9999/clientes/1/transacoes
    @Override
    public synchronized void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        var pathInfo = req.getPathInfo();
        if (pathInfo == null) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Resource not found");
            return;
        }

        // Use the static pattern to match the path info
        var matcher = TRANSACAO_PATTERN.matcher(pathInfo);

        Map<String, Object> t;
        try (BufferedReader reader = req.getReader()) {
            t = objectMapper.readValue(reader, Map.class);
        } catch (Exception e) {
            e.printStackTrace();
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid request body");
            return;
        }

        if (matcher.matches()) {
            var id = Integer.valueOf(matcher.group(1));
            var action = matcher.group(2);

            if ("transacoes".equals(action)) {
                postTransacao(id, t, resp);
                return;
            }
        }

        resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Invalid URL format or resource not found");
    }

    private void postTransacao(Integer id, Map<String, Object> t, HttpServletResponse resp) throws IOException {
        // Validate and process the transaction as in the original resource
        var valorNumber = (Number) t.get("valor");
        if (valorNumber == null || !Integer.class.equals(valorNumber.getClass())) {
            resp.sendError(422, "Valor invalido");
            return;
        }
        var valor = valorNumber.intValue();

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

        var cliente = cache.get(id);

        if (cliente == null) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Cliente nao encontrado");
            return;
        }
        
        if ("d".equals(tipo)) {
            cliente.saldo -= valor;
        } else {
            cliente.saldo += valor;
        }
        
        Integer saldo = cliente.saldo;
        Integer limite = cliente.limite;

        cliente.transacoes.addFirst(Transacao.of(valor, tipo, descricao));
        if (cliente.transacoes.size() > 10) {
            cliente.transacoes.removeLast();
        }
        cache.put(id, cliente);

        var body = Map.of("limite", limite, "saldo", saldo);
        //Log.info(body);
        if (resp == null)
            return;
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        var output = objectMapper.writeValueAsString(body);
        resp.getWriter().write(output);
    }

    private void callTransacao(Integer id, Map<String, Object> t, HttpServletResponse resp) throws IOException {
    try (var conn = ds.getConnection();
                var stmt = conn.prepareStatement(TRANSACAO_QUERY)) {
            //stmt.setInt(1, id);
            //stmt.setInt(2, valor);
            //stmt.setString(3, tipo);
            //stmt.setString(4, descricao);
            stmt.execute();

            try (var rs = stmt.getResultSet()) {
                if (resp == null)
                    return;
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