CREATE UNLOGGED TABLE transacoes (
	id SERIAL PRIMARY KEY,
	cliente_id INTEGER NOT NULL,
	valor INTEGER NOT NULL,
	tipo CHAR(1) NOT NULL,
	descricao VARCHAR(255) NOT NULL,
	realizada_em TIMESTAMP NOT NULL DEFAULT NOW(),
);



CREATE EXTENSION IF NOT EXISTS pg_prewarm;
SELECT pg_prewarm('transacoes');



CREATE TYPE transacao_result AS (saldo INT, limite INT);

CREATE OR REPLACE FUNCTION proc_transacao(p_cliente_id INT, p_valor INT, p_tipo VARCHAR, p_descricao VARCHAR)
RETURNS transacao_result as $$
DECLARE
    diff INT;
    v_saldo INT;
    v_limite INT;
    result transacao_result;
BEGIN
    IF p_tipo = 'd' THEN
        diff := p_valor * -1;
    ELSE
        diff := p_valor;
    END IF;
    -- rplace with select
    -- UPDATE clientes 
    --    SET saldo = saldo + diff 
    --    WHERE id = p_cliente_id
    --    RETURNING saldo, limite INTO v_saldo, v_limite;

    IF (v_saldo + diff) < (-1 * v_limite) THEN
        RAISE 'LIMITE_INDISPONIVEL [%, %, %]', v_saldo, diff, v_limite;
    ELSE
        result := (v_saldo, v_limite)::transacao_result;
        INSERT INTO transacoes (cliente_id, valor, tipo, descricao)
            VALUES (p_cliente_id, p_valor, p_tipo, p_descricao);
        RETURN result;
    END IF;
EXCEPTION
    WHEN OTHERS THEN
        RAISE 'Error processing transaction: %', SQLERRM;
        ROLLBACK;
END;
$$ LANGUAGE plpgsql;CREATE OR REPLACE FUNCTION proc_extrato(p_id integer)
RETURNS json AS $$
DECLARE
    result json;
    row_count integer;
    v_saldo numeric;
    v_limite numeric;
BEGIN
    SELECT saldo, limite
    INTO v_saldo, v_limite
    FROM clientes
    WHERE id = p_id;

    GET DIAGNOSTICS row_count = ROW_COUNT;

    IF row_count = 0 THEN
        RAISE EXCEPTION 'CLIENTE_NAO_ENCONTRADO %', p_id;
    END IF;

    SELECT json_build_object(
        'saldo', json_build_object(
            'total', v_saldo,
            'data_extrato', TO_CHAR(NOW(), 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"'),
            'limite', v_limite
        ),
        'ultimas_transacoes', COALESCE((
            SELECT json_agg(row_to_json(t)) FROM (
                SELECT valor, tipo, descricao, TO_CHAR(realizada_em, 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"') as realizada_em
                FROM transacoes
                WHERE cliente_id = p_id
                ORDER BY realizada_em DESC
                LIMIT 10
            ) t
        ), '[]')
    ) INTO result;

    RETURN result;
END;
$$ LANGUAGE plpgsql;
-- SQL init done
