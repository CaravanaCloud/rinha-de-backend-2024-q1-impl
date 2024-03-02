package caravanacloud.netty;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;
import io.netty.util.CharsetUtil;

import javax.sql.DataSource;

import com.oracle.svm.core.annotate.Inject;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HttpNettyHandler extends SimpleChannelInboundHandler<HttpObject> {
    private static final String valorPattern = "\"valor\":\\s*(\\d+(\\.\\d+)?)";
    private static final String tipoPattern = "\"tipo\":\\s*\"([^\"]*)\"";
    private static final String descricaoPattern = "\"descricao\":\\s*(?:\"([^\"]*)\"|null)";

    private static final Pattern pValor = Pattern.compile(valorPattern);
    private static final Pattern pTipo = Pattern.compile(tipoPattern);
    private static final Pattern pDescricao = Pattern.compile(descricaoPattern);
    private static final Pattern PATTERN_ID = Pattern.compile("/clientes/(\\d+)/.*");
    private static final String EXTRATO_QUERY = "select * from proc_extrato(?)";
    private static final String TRANSACAO_QUERY = "select * from proc_transacao(?, ?, ?, ?, ?)";
    
    @Inject
    private DataSource dataSource; // Assume dataSource is initialized properly

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, HttpObject msg) {
        if (msg instanceof HttpRequest) {
            HttpRequest request = (HttpRequest) msg;
            QueryStringDecoder queryStringDecoder = new QueryStringDecoder(request.uri());
            String path = queryStringDecoder.path();
            Matcher matcher = PATTERN_ID.matcher(path);
            if (!matcher.matches()) {
                sendError(ctx, HttpResponseStatus.NOT_FOUND);
                return;
            }

            int id = Integer.parseInt(matcher.group(1)); // Simplified, add validation as needed

            if (HttpMethod.GET.equals(request.method())) {
                processExtrato(ctx, id);
            } else if (HttpMethod.POST.equals(request.method()) && path.endsWith("/transacoes")) {
                // For POST, we need to aggregate the content in a subsequent message.
                if (msg instanceof HttpContent) {
                    HttpContent content = (HttpContent) msg;
                    processTransacao(ctx, id, content);
                }
            } else {
                sendError(ctx, HttpResponseStatus.METHOD_NOT_ALLOWED);
            }
        }
    }

    private void processExtrato(ChannelHandlerContext ctx, int id) {
        // Simplified DB interaction, add try-catch as needed
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(EXTRATO_QUERY)) {
            stmt.setInt(1, id);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                String result = rs.getString("body");
                int status_code = rs.getInt("status_code");
                sendResponse(ctx, result, HttpResponseStatus.valueOf(status_code));
            } else {
                sendError(ctx, HttpResponseStatus.NOT_FOUND);
            }
        } catch (Exception e) {
            sendError(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private void processTransacao(ChannelHandlerContext ctx, int id, HttpContent content) {
        ByteBuf jsonBuf = content.content();
        String json = jsonBuf.toString(CharsetUtil.UTF_8);
    
        Matcher mValor = pValor.matcher(json);
        Matcher mTipo = pTipo.matcher(json);
        Matcher mDescricao = pDescricao.matcher(json);
    
        if (mValor.find() && mTipo.find() && mDescricao.find()) {
            String valor = mValor.group(1);
            String tipo = mTipo.group(1);
            String descricao = mDescricao.group(1);
    
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(TRANSACAO_QUERY)) {
                // Assuming shard is determined or set elsewhere in your Netty handler
                int shard = getShard(); // Implement this based on your logic
                stmt.setInt(1, shard);
                stmt.setInt(2, id);
                stmt.setBigDecimal(3, new java.math.BigDecimal(valor));
                stmt.setString(4, tipo);
                stmt.setString(5, descricao);
                
                boolean executed = stmt.execute();
                if (executed) {
                    sendResponse(ctx, "{\"message\": \"Transaction processed successfully.\"}", HttpResponseStatus.OK);
                } else {
                    sendError(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR);
                }
            } catch (Exception e) {
                // Log the exception and send a 500 Internal Server Error response
                sendError(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR);
            }
        } else {
            sendError(ctx, HttpResponseStatus.BAD_REQUEST, "Invalid JSON payload");
        }
    }

    private int getShard() {
        return 0;
    }

    private void sendResponse(ChannelHandlerContext ctx, String body, HttpResponseStatus status) {
        FullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, status, Unpooled.copiedBuffer(body, CharsetUtil.UTF_8));
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json; charset=UTF-8");
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    private void sendError(ChannelHandlerContext ctx, HttpResponseStatus status) {
        sendError(ctx, status, "Error: " + status.toString());
    }
    
    private void sendError(ChannelHandlerContext ctx, HttpResponseStatus status, String message) {
        FullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, status, Unpooled.copiedBuffer("{\"error\": \"" + message + "\"}", CharsetUtil.UTF_8));
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json; charset=UTF-8");
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }}
