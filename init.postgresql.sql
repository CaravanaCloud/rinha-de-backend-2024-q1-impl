CREATE UNLOGGED TABLE clientes (
	id SERIAL PRIMARY KEY,
	nome VARCHAR(255) NOT NULL,
	limite INTEGER NOT NULL,
	saldo INTEGER NOT NULL DEFAULT 0
);

CREATE UNLOGGED TABLE transacoes (
	id SERIAL PRIMARY KEY,
	cliente_id INTEGER NOT NULL,
	valor INTEGER NOT NULL,
	tipo CHAR(1) NOT NULL,
	descricao CHAR(12) NOT NULL,
	realizada_em TIMESTAMP(6) NOT NULL DEFAULT NOW(),
	CONSTRAINT fk_clientes_transacoes_id
		FOREIGN KEY (cliente_id) REFERENCES clientes(id)
);

INSERT INTO clientes (nome, limite) VALUES
	('o barato sai caro', 1000 * 100),
	('zan corp ltda', 800 * 100),
	('les cruders', 10000 * 100),
	('padaria joia de cocaia', 100000 * 100),
	('kid mais', 5000 * 100);

CREATE TYPE fn_result AS (
    status_code INT,
    json_text TEXT
);

CREATE OR REPLACE FUNCTION proc_transacao(p_cliente_id INT, txx json)
RETURNS fn_result AS $$
DECLARE
    result fn_result;    
    p_valor_txt VARCHAR;
    p_valor INT;
    p_tipo CHAR(1);
    p_descricao VARCHAR;
    diff INT;
    v_saldo INT;
    v_limite INT;

BEGIN
    p_valor_txt := (txx->>'valor');
    p_tipo := txx->>'tipo';
    p_descricao := txx->>'descricao';

    -- Validate valor
    IF p_valor_txt IS NULL OR p_valor_txt !~ '^\-?\d+$' THEN
        result.status_code := 422;
        result.json_text   := json_build_object('error', 'valor invalida');
        RETURN result;
    END IF;
    SELECT CAST(p_valor_txt AS INT) into p_valor;


    -- Validate tipo
    IF p_tipo IS NULL OR (p_tipo != 'c' AND p_tipo != 'd') THEN
        result.status_code := 422;
        result.json_text   := json_build_object('error', 'tipo invalida');
        RETURN result;    
    END IF;

    IF p_tipo = 'd' THEN
        diff := p_valor * -1;
    ELSE
        diff := p_valor;
    END IF;

    -- Validate descricao
    IF p_descricao IS NULL OR LENGTH(p_descricao) = 0 OR LENGTH(p_descricao) > 10 THEN
        result.status_code := 422;
        result.json_text   := json_build_object('error', 'descricao invalida');
        RETURN result;
    END IF;

    PERFORM * FROM clientes WHERE id = p_cliente_id FOR UPDATE;

    UPDATE clientes 
        SET saldo = saldo + diff 
        WHERE id = p_cliente_id
        RETURNING saldo, limite INTO v_saldo, v_limite;

    IF (v_saldo + diff) < (-1 * v_limite) THEN
        result.status_code := 422;
        result.json_text   := json_build_object('error', 'limite ultrapassado');
        RETURN result;   
    ELSE
        INSERT INTO transacoes (cliente_id, valor, tipo, descricao)
            VALUES (p_cliente_id, p_valor, p_tipo, p_descricao);

        result.status_code := 200;
        result.json_text   := json_build_object('saldo', v_saldo, 'limite', v_limite);
        RETURN result;
    END IF;

EXCEPTION
    WHEN OTHERS THEN
        RAISE 'Error processing transaction: %', SQLERRM;
        ROLLBACK;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION proc_extrato(p_id integer)
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
