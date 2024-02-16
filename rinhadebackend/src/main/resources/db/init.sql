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
    realizada_em DATETIME NOT NULL DEFAULT now(),
    FOREIGN KEY (cliente_id) REFERENCES clientes(id)
);

INSERT INTO clientes (nome, limite) VALUES
    ('o barato sai caro', 1000 * 100),
    ('zan corp ltda', 800 * 100),
    ('les cruders', 10000 * 100),
    ('padaria joia de cocaia', 100000 * 100),
    ('kid mais', 5000 * 100);

CREATE PROCEDURE proc_transacao(IN p_cliente_id INT, IN p_valor INT, IN p_tipo VARCHAR(1), IN p_descricao VARCHAR(255), OUT r_saldo INT, OUT r_limite INT)
BEGIN
    DECLARE diff INT;
    DECLARE n_saldo INT;
    
    START TRANSACTION;

    IF NOT EXISTS (SELECT 1 FROM clientes WHERE id = p_cliente_id) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CLIENTE_NAO_ENCONTRADO';
        ROLLBACK;
    END IF;

    IF p_tipo = 'd' THEN
        SET diff = p_valor * -1;
    ELSE
        SET diff = p_valor;
    END IF;

    SELECT saldo, limite, saldo + diff
        INTO r_saldo, r_limite, n_saldo
        FROM clientes 
        WHERE id = p_cliente_id 
        FOR UPDATE;

    IF (n_saldo) < (-1 * r_limite) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'LIMITE_INDISPONIVEL';
        ROLLBACK;
    ELSE
        UPDATE clientes SET saldo = n_saldo WHERE id = p_cliente_id;
        
        INSERT INTO transacoes (cliente_id, valor, tipo, descricao, realizada_em)
            VALUES (p_cliente_id, p_valor, p_tipo, p_descricao, now(6));

        SELECT n_saldo, r_limite AS resultado;

        COMMIT;
    END IF;
END;

CREATE PROCEDURE proc_extrato(IN p_id INT)
BEGIN
    START TRANSACTION READ ONLY;

    IF NOT EXISTS (SELECT 1 FROM clientes WHERE id = p_id) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CLIENTE_NAO_ENCONTRADO';
        ROLLBACK;
    END IF;

    -- Construct and return the entire JSON in a single query
    SELECT JSON_OBJECT(
        'saldo', (
            SELECT JSON_OBJECT(
                'total', saldo,
                'limite', limite
            )
            FROM clientes
            WHERE id = p_id
        ),
        'ultimas_transacoes', (
            SELECT COALESCE(JSON_ARRAYAGG(
                JSON_OBJECT(
                    'valor', valor,
                    'tipo', tipo,
                    'descricao', descricao,
                    'realizada_em', DATE_FORMAT(realizada_em, '%Y-%m-%dT%H:%i:%sZ')
                )
            ), JSON_ARRAY()) 
            FROM transacoes
            WHERE cliente_id = p_id
            ORDER BY realizada_em DESC
            LIMIT 10
        )
    ) AS extrato;
    COMMIT; 
END;
