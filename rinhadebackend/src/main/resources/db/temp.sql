
DELIMITER ;

DELIMITER $$

CREATE PROCEDURE proc_transacao(
    IN p_cliente_id INT, 
    IN p_valor INT, 
    IN p_tipo CHAR(1), 
    IN p_descricao VARCHAR(10),
    OUT result_body TEXT, 
    OUT result_status_code INT
)
BEGIN
    DECLARE v_saldo INT;
    DECLARE v_limite INT;
    
    -- Determine the limit based on cliente_id
    SET v_limite = CASE p_cliente_id
        WHEN 1 THEN 100000
        WHEN 2 THEN 80000
        WHEN 3 THEN 1000000
        WHEN 4 THEN 10000000
        WHEN 5 THEN 500000
        ELSE -1 -- Default case if cliente_id is not between 1 and 5
    END;
    
    -- Fetch current balance and lock the row
    SELECT saldo INTO v_saldo FROM clientes WHERE id = p_cliente_id FOR UPDATE;
    
    -- Check if the transaction exceeds the limit for debits
    IF p_tipo = 'd' AND (v_saldo - p_valor) < (-1 * v_limite) THEN
        SET result_body = JSON_OBJECT('error', 'LIMITE_INDISPONIVEL');
        SET result_status_code = 422; -- Unprocessable Entity
    ELSE
        -- Proceed with inserting the transaction
        INSERT INTO transacoes (cliente_id, valor, tipo, descricao, realizada_em, realizada_em_char)
        VALUES (p_cliente_id, p_valor, p_tipo, p_descricao, NOW(), DATE_FORMAT(NOW(), '%Y-%m-%d %H:%i:%s.%f'));
        
        -- Update the balance
        UPDATE clientes 
        SET saldo = CASE WHEN p_tipo = 'c' THEN saldo + p_valor
                         WHEN p_tipo = 'd' THEN saldo - p_valor
                    END 
        WHERE id = p_cliente_id;
        
        -- Fetch the updated balance
        SELECT saldo INTO v_saldo FROM clientes WHERE id = p_cliente_id;
        
        -- Prepare the success response
        SET result_body = JSON_OBJECT('saldo', v_saldo, 'limite', v_limite);
        SET result_status_code = 200; -- OK
    END IF;
END$$

DELIMITER ;
