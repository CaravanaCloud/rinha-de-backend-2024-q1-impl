CREATE table
  clientes (
    id int primary key,
    limite int not null default 0,
    saldo int not null default 0
  );

ALTER TABLE clientes ADD CONSTRAINT check_limite CHECK (saldo >= (limite * -1));

CREATE table
  transacoes (
    id int auto_increment primary key,
    cliente_id int not null,
    valor int not null,
    tipo varchar(1) not null,
    descricao varchar(10) not null,
    realizada_em TIMESTAMP(6) not null default now(6),
    index (realizada_em DESC),
    index (cliente_id) USING HASH
  );

insert into
  clientes
values
  (1, 100000, 0),
  (2, 80000, 0),
  (3, 1000000, 0),
  (4, 10000000, 0),
  (5, 500000, 0);



CREATE PROCEDURE proc_transacao (
  IN cliente_id int,
  IN valor int,
  IN tipo varchar(1),
  IN descricao varchar(10),
  OUT json_body LONGTEXT,
  OUT status_code INT
) BEGIN DECLARE v_saldo INT;

DECLARE v_limite INT;

DECLARE v_error_message VARCHAR(255) DEFAULT 'An error occurred during the transaction';

DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN
-- Set the error response and status code
GET DIAGNOSTICS CONDITION 1 v_error_message = MESSAGE_TEXT;

SET
  json_body = JSON_OBJECT ('error', v_error_message);

SET
  status_code = 422;

END;

IF tipo = 'c' THEN
UPDATE clientes
set
  saldo = saldo + valor
where
  id = cliente_id;

ELSE
UPDATE clientes
set
  saldo = saldo - valor
where
  id = cliente_id;

END IF;

INSERT INTO
  transacoes (cliente_id, valor, tipo, descricao, realizada_em)
VALUES
  (cliente_id, valor, tipo, descricao, now(6));

-- Retrieve the updated balance
SELECT
  saldo,
  limite INTO v_saldo,
  v_limite
FROM
  clientes
WHERE
  id = cliente_id;

-- Construct the JSON response with the current balance and limit
SET
  json_body = JSON_OBJECT ('saldo', v_saldo, 'limite', v_limite);

-- Set the status code to indicate success
SET
  status_code = 200;

-- OK
-- OK
END;

CREATE PROCEDURE proc_extrato (
  IN cliente_id INT,
  OUT json_body LONGTEXT,
  OUT status_code INT
) BEGIN DECLARE v_saldo INT DEFAULT 0;

DECLARE v_limit INT DEFAULT -1;

-- Example limit, adjust as needed
DECLARE v_transacoes TEXT;

-- Check if the client exists and get the current balance
SELECT
  saldo,
  limite INTO v_saldo,
  v_limit
FROM
  clientes
WHERE
  id = cliente_id;

IF v_saldo IS NULL THEN
SET
  json_body = '{"error": "Client not found"}';

SET
  status_code = 404;

-- Client not found
ELSE
-- Construct the JSON response
SET
  json_body = JSON_OBJECT (
    'saldo',
    JSON_OBJECT (
      'total',
      v_saldo,
      'limite',
      v_limit, -- Fixed: Added missing comma here
      'data_extrato',
      DATE_FORMAT (NOW (6), '%Y-%m-%d %H:%i:%s.%f')
    ),
    'ultimas_transacoes',
    (
      SELECT
        IFNULL (
          JSON_ARRAYAGG (
            JSON_OBJECT (
              'valor',
              valor,
              'tipo',
              tipo,
              'descricao',
              descricao,
              'data',
              DATE_FORMAT (realizada_em, '%Y-%m-%d %H:%i:%s.%f')
            )
          ),
          JSON_ARRAY ()
        )
      FROM
        transacoes
      WHERE
        cliente_id = cliente_id
      ORDER BY
        realizada_em DESC
      LIMIT
        10
    )
  );

SET
  status_code = 200;

END IF;

END;