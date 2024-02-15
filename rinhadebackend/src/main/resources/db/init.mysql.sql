-- Create tables
CREATE TABLE clientes (
    id INT AUTO_INCREMENT PRIMARY KEY,
    nome VARCHAR(255) NOT NULL,
    limite INT NOT NULL,
    saldo INT NOT NULL DEFAULT 0
);

CREATE TABLE transacoes (
    id INT AUTO_INCREMENT PRIMARY KEY,
    cliente_id INT NOT NULL,
    valor INT NOT NULL,
    tipo CHAR(1) NOT NULL,
    descricao VARCHAR(255) NOT NULL,
    realizada_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (cliente_id) REFERENCES clientes(id)
);

-- Insert initial data into clientes
INSERT INTO clientes (nome, limite) VALUES
    ('o barato sai caro', 1000 * 100),
    ('zan corp ltda', 800 * 100),
    ('les cruders', 10000 * 100),
    ('padaria joia de cocaia', 100000 * 100),
    ('kid mais', 5000 * 100);

-- Procedure for transactions
DELIMITER //
CREATE PROCEDURE proc_transacao(IN p_cliente_id INT, IN p_valor INT, IN p_tipo VARCHAR(1), IN p_descricao VARCHAR(255))
BEGIN
    DECLARE v_saldo INT;
    DECLARE v_limite INT;
    DECLARE diff INT;
    
    -- Determine transaction effect
    IF p_tipo = 'd' THEN
        SET diff = p_valor * -1;
    ELSE
        SET diff = p_valor;
    END IF;

    -- Lock the clientes row
    SELECT saldo, limite INTO v_saldo, v_limite FROM clientes WHERE id = p_cliente_id FOR UPDATE;

    -- Check if the new balance would exceed the limit
    IF v_saldo + diff < -v_limite THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'LIMITE_INDISPONIVEL';
    ELSE
        -- Update clientes saldo
        UPDATE clientes SET saldo = saldo + diff WHERE id = p_cliente_id;
        
        -- Insert into transacoes
        INSERT INTO transacoes (cliente_id, valor, tipo, descricao)
        VALUES (p_cliente_id, p_valor, p_tipo, p_descricao);
    END IF;
END //
DELIMITER ;

-- Procedure for account statement
DELIMITER //
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
END //
DELIMITER ;
