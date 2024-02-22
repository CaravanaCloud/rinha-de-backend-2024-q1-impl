CREATE table
  clientes (
    id int primary key,
    limite int not null default 0,
    saldo int not null default 0
  );

-- ALTER TABLE clientes ADD CONSTRAINT check_limite CHECK (saldo >= (limite * -1));

CREATE table
  transacoes (
    id int auto_increment primary key,
    cliente_id int not null,
    valor int not null,
    tipo varchar(1) not null,
    descricao varchar(10) not null,
    realizada_em DATETIME(6) not null,
    index (cliente_id) USING HASH
  );

START TRANSACTION;

insert into clientes(id, limite, saldo)
values
  (1, 1000 * 100, 0),
  (2, 800 * 100, 0),
  (3, 10000 * 100, 0),
  (4, 100000 * 100, 0),
  (5, 5000 * 100, 0);

-- insert into transacoes (cliente_id, valor, tipo, descricao, realizada_em)
-- values
--  (1, 0, 'c', 'init', now(6)),
--  (2, 0, 'c', 'init', now(6)),
--  (3, 0, 'c', 'init', now(6)),
--  (4, 0, 'c', 'init', now(6)),
--  (5, 0, 'c', 'init', now(6));

COMMIT;

DELIMITER //

CREATE PROCEDURE proc_transacao (
  IN p_cliente_id int,
  IN valor int,
  IN tipo varchar(1),
  IN descricao varchar(10),
  IN realizada_em TIMESTAMP(6),
  OUT json_body TEXT,
  OUT status_code INT
) BEGIN 

DECLARE v_saldo INT;
DECLARE v_limite INT;
DECLARE v_error_message VARCHAR(255) DEFAULT 'An error occurred during the transaction';

DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN
  GET DIAGNOSTICS CONDITION 1 v_error_message = MESSAGE_TEXT;
  SET json_body = JSON_OBJECT ('error', v_error_message);
  SET status_code = 422;
  ROLLBACK;
END;

SET autocommit=0;
START TRANSACTION READ WRITE;

SELECT saldo, limite 
  INTO v_saldo, v_limite
  FROM clientes
  WHERE id = p_cliente_id
  FOR UPDATE;

IF tipo = 'c' THEN
  UPDATE clientes
    SET saldo = v_saldo + valor
    WHERE id = p_cliente_id;
  INSERT INTO transacoes (cliente_id, valor, tipo, descricao, realizada_em)
  VALUES (p_cliente_id, valor, tipo, descricao, now(6));
    SET json_body = JSON_OBJECT ('saldo', CAST(v_saldo + valor as INT), 'limite', CAST(v_limite as INT));
    SET status_code = 200;
ELSE
  IF v_saldo - valor < -1 * v_limite THEN
    SET json_body = JSON_OBJECT ('error', 'Saldo insuficiente');
    SET status_code = 422;
    ROLLBACK;
  ELSE
    UPDATE clientes
      SET saldo = v_saldo - valor
      WHERE id = p_cliente_id;
    INSERT INTO transacoes (cliente_id, valor, tipo, descricao, realizada_em)
      VALUES (p_cliente_id, valor, tipo, descricao, now(6));
    SET json_body = JSON_OBJECT ('saldo', CAST(v_saldo - valor as INT), 'limite', CAST(v_limite as INT));
    SET status_code = 200;
  END IF;
END IF;
COMMIT;
END//

DELIMITER //

CREATE PROCEDURE proc_extrato (
  IN p_cliente_id INT,
  OUT json_body TEXT,
  OUT status_code INT
) BEGIN 

DECLARE v_saldo INT DEFAULT 0;
DECLARE v_limit INT DEFAULT -1;

SET autocommit=0;
START TRANSACTION READ ONLY;

SELECT saldo, limite 
  INTO v_saldo, v_limit
  FROM clientes
  WHERE id = p_cliente_id;

SET json_body = JSON_OBJECT(
    'saldo', JSON_OBJECT(
        'total', CAST(v_saldo as INT),
        'limite', CAST(v_limit as INT),
        'data_extrato', DATE_FORMAT(NOW(6), '%Y-%m-%d %H:%i:%s.%f')
    ),
    'ultimas_transacoes', (
        SELECT IFNULL(
            JSON_ARRAYAGG(
                JSON_OBJECT(
                    'valor', CAST(valor as INT),
                    'tipo', tipo,
                    'descricao', descricao,
                    'realizada_em', DATE_FORMAT(realizada_em, '%Y-%m-%d %H:%i:%s.%f')
                )
            ),
            JSON_ARRAY()
        )
        FROM (
            SELECT valor, tipo, descricao, realizada_em
            FROM transacoes
            WHERE cliente_id = p_cliente_id
            ORDER BY realizada_em DESC
            LIMIT 10
        ) AS limited_transacoes
    )
);


  SET status_code = 200;
  COMMIT;
END//


