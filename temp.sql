

CREATE PROCEDURE proc_extrato(IN p_id INT)
BEGIN
    -- Variables to hold the JSON components
    DECLARE saldo_json JSON;
    DECLARE transacoes_json JSON;
    
    -- Get saldo and limite for the cliente
    SELECT JSON_OBJECT(
        'total', saldo,
        'limite', limite
    ) INTO saldo_json
    FROM clientes
    WHERE id = p_id;
    
    -- Get the last 10 transacoes for the cliente
    SELECT COALESCE(JSON_ARRAYAGG(
        JSON_OBJECT(
            'valor', valor,
            'tipo', tipo,
            'descricao', descricao,
            'realizada_em', DATE_FORMAT(realizada_em, '%Y-%m-%dT%T.%fZ')
        )
    ), JSON_ARRAY()) INTO transacoes_json
    FROM transacoes
    WHERE cliente_id = p_id
    ORDER BY realizada_em DESC
    LIMIT 10;
    
    -- Build the final JSON result
    SELECT JSON_OBJECT(
        'saldo', saldo_json,
        'ultimas_transacoes', transacoes_json
    ) AS extrato;
END;

