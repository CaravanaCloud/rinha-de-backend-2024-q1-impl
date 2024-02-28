CREATE TABLE transacoes (
	id SERIAL PRIMARY KEY,
	cliente_id INTEGER NOT NULL,
	valor INTEGER NOT NULL,
	tipo CHAR(1) NOT NULL,
	descricao VARCHAR(10) NOT NULL,
	realizada_em TIMESTAMP(6) NOT NULL,
    saldo INTEGER NOT NULL
);

INSERT INTO transacoes (cliente_id, valor, tipo, descricao, realizada_em, saldo) VALUES
    (1 , 0, 'c', 'init', clock_timestamp(), 0),
    (2 , 0, 'c', 'init', clock_timestamp(), 0),
    (3 , 0, 'c', 'init', clock_timestamp(), 0),
    (4 , 0, 'c', 'init', clock_timestamp(), 0),
    (5 , 0, 'c', 'init', clock_timestamp(), 0);


CREATE EXTENSION IF NOT EXISTS pg_prewarm;
SELECT pg_prewarm('transacoes');


CREATE OR REPLACE FUNCTION limite_cliente(p_cliente_id INTEGER)
RETURNS INTEGER AS $$
BEGIN
    RETURN CASE p_cliente_id
        WHEN 1 THEN 100000
        WHEN 2 THEN 80000
        WHEN 3 THEN 1000000
        WHEN 4 THEN 10000000
        WHEN 5 THEN 500000
        ELSE -1
    END;
END;
$$ LANGUAGE plpgsql;

CREATE TYPE json_result AS (
  status_code INT,
  body json
);


CREATE OR REPLACE FUNCTION proc_transacao(p_shard INT, p_cliente_id INT, p_valor INT, p_tipo CHAR, p_descricao CHAR(10))
RETURNS json_result as $$
DECLARE
    diff INT;
    v_saldo INT;
    v_limite INT;
    result json_result;
BEGIN
    PERFORM pg_advisory_xact_lock(p_cliente_id);
    -- PERFORM pg_advisory_lock(p_cliente_id);
    -- PERFORM pg_try_advisory_xact_lock(p_cliente_id);
    -- PERFORM pg_advisory_xact_lock(p_cliente_id);
    -- lock table clientes in ACCESS EXCLUSIVE mode;
    lock table transacoes in ACCESS EXCLUSIVE mode;

    -- invoke limite_cliente into v_limite
    SELECT limite_cliente(p_cliente_id) INTO v_limite;
    
    SELECT saldo 
        FROM transacoes
        WHERE cliente_id = p_cliente_id
        ORDER BY realizada_em DESC, id DESC
        LIMIT 1
        INTO v_saldo;

    IF p_tipo = 'd' THEN
        diff := p_valor * -1;            
        IF (v_saldo + diff) < (-1 * v_limite) THEN
            result.body := '{"erro": "Saldo insuficiente"}';
            result.status_code := 422;
            RETURN result;
        END IF;
    ELSE
        diff := p_valor;
    END IF;

    
    INSERT INTO transacoes 
                     (cliente_id,   valor,   tipo,   descricao,      realizada_em, saldo)
            VALUES (p_cliente_id, p_valor, p_tipo, p_descricao, now(), v_saldo + diff);

    SELECT json_build_object(
        'saldo', v_saldo + diff,
        'limite', v_limite
    ) into result.body;
    result.status_code := 200;
    RETURN result;
EXCEPTION
    WHEN OTHERS THEN
        RAISE 'Error processing transaction: %', SQLERRM;

END;
$$ LANGUAGE plpgsql;


CREATE OR REPLACE FUNCTION proc_extrato(p_cliente_id int)
RETURNS json_result AS $$
DECLARE
    result json_result;
    row_count integer;
    v_saldo numeric;
    v_limite numeric;
BEGIN
    -- PERFORM pg_try_advisory_xact_lock(42);
    -- PERFORM pg_try_advisory_xact_lock(p_cliente_id);
    -- PERFORM pg_try_advisory_lock(p_cliente_id);
    -- PERFORM pg_advisory_xact_lock(p_cliente_id);
    -- lock table clientes in ACCESS EXCLUSIVE mode;
    lock table transacoes in ACCESS SHARE mode;
    PERFORM pg_advisory_xact_lock(p_cliente_id);

    SELECT saldo 
        FROM transacoes
        WHERE cliente_id = p_cliente_id
        ORDER BY realizada_em DESC, id DESC
        LIMIT 1
        INTO v_saldo;
    -- FOR UPDATE;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'CLIENTE_NAO_ENCONTRADO %', p_cliente_id;
    END IF;

    SELECT limite_cliente(p_cliente_id) INTO v_limite;
    SELECT json_build_object(
        'saldo', json_build_object(
            'total', v_saldo,
            'data_extrato', TO_CHAR(clock_timestamp(), 'YYYY-MM-DD HH:MI:SS.US'),
            'limite', v_limite
        ),
        'ultimas_transacoes', COALESCE((
            SELECT json_agg(row_to_json(t)) FROM (
                SELECT valor, tipo, descricao, TO_CHAR(realizada_em, 'YYYY-MM-DD HH:MI:SS.US') as realizada_em
                FROM transacoes
                WHERE cliente_id = p_cliente_id
                ORDER BY realizada_em DESC, id DESC 
                LIMIT 10
            ) t
        ), '[]')
    ) INTO result.body;
    result.status_code := 200;
    RETURN result;
END;
$$ LANGUAGE plpgsql;
