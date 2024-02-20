-- Criando a tabela de transações com a coluna adicional saldo
CREATE UNLOGGED TABLE IF NOT EXISTS transacoes (
    id SERIAL PRIMARY KEY,
    cliente_id INTEGER NOT NULL,
    valor INTEGER NOT NULL,
    tipo CHAR(1) NOT NULL,
    descricao VARCHAR(255) NOT NULL,
    realizada_em TIMESTAMP NOT NULL DEFAULT NOW(),
    saldo INTEGER NOT NULL DEFAULT 0
);

-- Inserções iniciais
INSERT INTO transacoes (cliente_id, valor, tipo, descricao, saldo)
VALUES 
    (1, 0, 'c', 'Deposito inicial', 0),
    (2, 0, 'c', 'Deposito inicial', 0),
    (3, 0, 'c', 'Deposito inicial', 0),
    (4, 0, 'c', 'Deposito inicial', 0),
    (5, 0, 'c', 'Deposito inicial', 0);

-- Preparando o ambiente
CREATE EXTENSION IF NOT EXISTS pg_prewarm;
SELECT pg_prewarm('transacoes');

-- Definindo o tipo para o resultado da transação
CREATE TYPE transacao_result AS (saldo INT, limite INT);

-- Função para obter o limite do cliente
CREATE OR REPLACE FUNCTION limite_cliente(p_cliente_id INTEGER)
RETURNS INTEGER AS $$
BEGIN
    RETURN CASE p_cliente_id
        WHEN 1 THEN 100000
        WHEN 2 THEN 80000
        WHEN 3 THEN 1000000
        WHEN 4 THEN 10000000
        WHEN 5 THEN 500000
        ELSE -1 -- Valor padrão caso o id do cliente não esteja entre 1 e 5
    END;
END;
$$ LANGUAGE plpgsql;

-- Procedure para realizar transações com lógica de limite
CREATE OR REPLACE PROCEDURE proc_transacao(p_cliente_id INT, p_valor INT, p_tipo VARCHAR, p_descricao VARCHAR)
LANGUAGE plpgsql AS $$
DECLARE
    diff INT;
    v_saldo_atual INT;
    v_novo_saldo INT;
    v_limite INT;
BEGIN
    PERFORM pg_advisory_xact_lock(p_cliente_id);

    IF p_tipo = 'd' THEN
        diff := -p_valor;
    ELSE
        diff := p_valor;
    END IF;

    -- Chamada para obter o limite do cliente
    v_limite := limite_cliente(p_cliente_id);

    SELECT saldo 
        INTO v_saldo_atual 
        FROM transacoes 
        WHERE cliente_id = p_cliente_id 
        ORDER BY id 
        DESC LIMIT 1;

    IF NOT FOUND THEN
        v_saldo_atual := 0;
    END IF;

    v_novo_saldo := v_saldo_atual + diff;

    IF p_tipo = 'd' AND v_novo_saldo < (-1 * v_limite) THEN
        RAISE EXCEPTION 'LIMITE_INDISPONIVEL';
    END IF;

    INSERT INTO transacoes (cliente_id, valor, tipo, descricao, saldo)
    VALUES (p_cliente_id, valor, p_tipo, p_descricao, v_novo_saldo);

    
END;
$$;

-- Procedure para obter extrato do cliente
CREATE OR REPLACE PROCEDURE proc_extrato(p_cliente_id INTEGER)
LANGUAGE plpgsql AS $$
DECLARE
    v_saldo INTEGER;
    v_limite INTEGER;
    transacoes json;
BEGIN
    PERFORM pg_advisory_xact_lock(p_cliente_id);

    -- Chamada para obter o limite do cliente
    v_limite := limite_cliente(p_cliente_id);

    SELECT saldo INTO v_saldo FROM transacoes WHERE cliente_id = p_cliente_id ORDER BY realizada_em DESC LIMIT 1;
    IF NOT FOUND THEN
        v_saldo := 0;
    END IF;

    SELECT json_agg(row_to_json(t.*)) INTO transacoes FROM (
        SELECT valor, tipo, descricao, TO_CHAR(realizada_em, 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"') AS realizada_em
        FROM transacoes
        WHERE cliente_id = p_cliente_id
        ORDER BY id DESC
        LIMIT 10
    ) t;

    -- Nota: A exibição do resultado para o cliente deve ser feita por meio de uma aplicação ou consulta que chame esta procedure.
    -- Este script SQL não retorna diretamente o JSON, mas prepara os dados para serem consumidos.
END;
$$;
