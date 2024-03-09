CREATE UNLOGGED TABLE clientes (
	id SERIAL PRIMARY KEY,
	saldo INTEGER NOT NULL DEFAULT 0,
    exrato jsonb NOT NULL DEFAULT '[]'::jsonb
);

INSERT INTO clientes(id, extrato) VALUES 
    (1,'[]'), 
    (2,'[]'), 
    (3,'[]'), 
    (4,'[]'), 
    (5,'[]'); 

CREATE EXTENSION IF NOT EXISTS pg_prewarm;
SELECT pg_prewarm('clientes');
SELECT pg_prewarm('transacoes');

CREATE TYPE json_result AS (
  status_code INT,
  body json
);

CREATE OR REPLACE FUNCTION proc_transacao(p_shard INT, p_cliente_id INT, p_valor INT, p_tipo CHAR, p_descricao CHAR(10))
RETURNS json_result as $$
DECLARE
    diff INT;
    v_saldo INT;
    n_saldo INT;
    v_limite INT;
    result json_result;
BEGIN
    v_limite := CASE p_cliente_id
        WHEN 1 THEN 100000
        WHEN 2 THEN 80000
        WHEN 3 THEN 1000000
        WHEN 4 THEN 10000000
        WHEN 5 THEN 500000
        ELSE -1
    END;

    SELECT saldo 
        INTO v_saldo
        FROM clientes
        WHERE id = p_cliente_id
        FOR UPDATE;

    IF p_tipo = 'd' THEN
        n_saldo := v_saldo - p_valor;
        IF (n_saldo < (-1 * v_limite)) THEN
            result.body := '{"erro": "Saldo insuficiente"}';
            result.status_code := 422;
            RETURN result;
        END IF;
    ELSE
      n_saldo := v_saldo + p_valor;
    END IF;
    
    INSERT INTO transacoes 
                     (cliente_id,   valor,   tipo,   descricao,      realizada_em)
            VALUES (p_cliente_id, p_valor, p_tipo, p_descricao, now());

    UPDATE clientes 
    SET saldo = n_saldo,
        extrato = (SELECT json_build_object(
                'saldo', json_build_object(
                    'total', n_saldo,
                    'data_extrato', TO_CHAR(now(), 'YYYY-MM-DD HH:MI:SS.US'),
                    'limite', v_limite
                ),
                'ultimas_transacoes', COALESCE((
                    SELECT json_agg(row_to_json(t)) FROM (
                        SELECT valor, tipo, descricao
                        FROM transacoes
                        WHERE cliente_id = p_cliente_id
                        ORDER BY realizada_em DESC
                        LIMIT 10
                    ) t
                ), '[]')
            ))
        WHERE id = p_cliente_id;


    SELECT json_build_object(
        'saldo', n_saldo,
        'limite', v_limite
    ) into result.body;
    result.status_code := 200;
    RETURN result;
END;
$$ LANGUAGE plpgsql;


CREATE OR REPLACE FUNCTION proc_extrato(p_cliente_id int)
RETURNS json_result AS $$
DECLARE
    result json_result;
BEGIN
    SELECT 200, extrato 
        FROM clientes
        WHERE id = p_cliente_id
        INTO result.status_code, result.body;
    RETURN result;
END;
$$ LANGUAGE plpgsql;
