CREATE TABLE clientes (
    id INT AUTO_INCREMENT PRIMARY KEY,
    saldo INT NOT NULL DEFAULT 0
);

CREATE TABLE transacoes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    cliente_id INT NOT NULL,
    valor INT NOT NULL,
    tipo CHAR(1) NOT NULL,
    descricao VARCHAR(10) NOT NULL,
    realizada_em DATETIME(6) NOT NULL,
    realizada_em_char VARCHAR(32) NOT NULL
);

CREATE INDEX idx_cliente_id ON transacoes (cliente_id);

INSERT INTO clientes(id) VALUES (NULL), (NULL), (NULL), (NULL), (NULL);

DELIMITER $$

CREATE PROCEDURE proc_transacao(IN p_cliente_id INT, IN p_valor INT, IN p_tipo CHAR(1), IN p_descricao VARCHAR(10))
BEGIN
    -- Example operation: Update saldo based on tipo
    IF p_tipo = 'c' THEN
        UPDATE clientes SET saldo = saldo + p_valor WHERE id = p_cliente_id;
    ELSEIF p_tipo = 'd' THEN
        UPDATE clientes SET saldo = saldo - p_valor WHERE id = p_cliente_id;
    END IF;

    -- Insert into transacoes table
    INSERT INTO transacoes (cliente_id, valor, tipo, descricao, realizada_em, realizada_em_char)
    VALUES (p_cliente_id, p_valor, p_tipo, p_descricao, NOW(), DATE_FORMAT(NOW(), '%Y-%m-%d %H:%i:%s.%f'));
END$$

DELIMITER ;


DELIMITER $$

CREATE PROCEDURE proc_extrato(IN p_cliente_id INT)
BEGIN
    DECLARE v_saldo INT;
    DECLARE v_limite INT;
    DECLARE extrato_json JSON;
    DECLARE transacoes_json JSON;
    
    -- Determine v_limite based on cliente_id (similar logic to your CASE statement)
    SET v_limite = CASE p_cliente_id
        WHEN 1 THEN 100000
        WHEN 2 THEN 80000
        WHEN 3 THEN 1000000
        WHEN 4 THEN 10000000
        WHEN 5 THEN 500000
        ELSE -1
    END;

    -- Get saldo for the cliente_id
    SELECT saldo INTO v_saldo FROM clientes WHERE id = p_cliente_id;

    -- Construct the JSON object for ultimas_transacoes
    SELECT JSON_ARRAYAGG(
        JSON_OBJECT(
            'valor', valor,
            'tipo', tipo,
            'descricao', descricao,
            'realizada_em_char', realizada_em_char
        )
    ) INTO transacoes_json
    FROM (
        SELECT valor, tipo, descricao, realizada_em_char
        FROM transacoes
        WHERE cliente_id = p_cliente_id
        ORDER BY realizada_em DESC
        LIMIT 10
    ) AS subquery;

    -- Construct the final extrato JSON object
    SET extrato_json = JSON_OBJECT(
        'saldo', JSON_OBJECT(
            'total', v_saldo,
            'data_extrato', DATE_FORMAT(NOW(), '%Y-%m-%d %H:%i:%s.%f'),
            'limite', v_limite
        ),
        'ultimas_transacoes', IFNULL(transacoes_json, JSON_ARRAY())
    );
    
    -- Output the extrato_json (in real-world usage, you might need to select or do something with this JSON)
    SELECT extrato_json AS result;
END$$

DELIMITER ;
