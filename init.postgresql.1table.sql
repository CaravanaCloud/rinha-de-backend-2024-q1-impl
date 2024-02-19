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

insert into transacoes (cliente_id, valor, tipo, descricao, saldo)
    values 
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
CREATE OR REPLACE FUNCTION obter_limite_cliente(p_cliente_id INTEGER)
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

-- Atualizando a função proc_transacao para incluir a lógica de limite
CREATE OR REPLACE FUNCTION proc_transacao(p_cliente_id INT, p_valor INT, p_tipo VARCHAR, p_descricao VARCHAR)
RETURNS transacao_result AS $$
DECLARE
    diff INT; -- Diferença a ser aplicada no saldo, baseada no tipo da transação
    v_saldo_atual INT; -- Saldo atual antes da transação
    v_novo_saldo INT; -- Novo saldo após aplicar a transação
    v_limite INT; -- Limite do cliente
BEGIN
    -- Determinando o valor da transação (negativo para débitos, positivo para créditos)
    IF p_tipo = 'd' THEN
        diff := -p_valor;
    ELSE
        diff := p_valor;
    END IF;

    -- Obtendo o limite do cliente
    v_limite := obter_limite_cliente(p_cliente_id);

    lock table transacoes in ACCESS EXCLUSIVE mode;
    -- PERFORM id FROM transacoes WHERE cliente_id = p_cliente_id ORDER BY id DESC FOR UPDATE;

    -- Obtendo o saldo atual da última transação registrada para o cliente
    SELECT saldo INTO v_saldo_atual FROM transacoes WHERE cliente_id = p_cliente_id ORDER BY realizada_em DESC LIMIT 1;
    IF NOT FOUND THEN
        v_saldo_atual := 0; -- Se não existirem transações, o saldo inicial é 0
    END IF;

    -- Calculando o novo saldo após a transação
    v_novo_saldo := v_saldo_atual + diff;

    -- Se for uma transação de débito, verificar se excede o limite do cliente
    IF p_tipo = 'd' AND v_novo_saldo < (-1 * v_limite) THEN
        RAISE EXCEPTION 'LIMITE_INDISPONIVEL';
    END IF;

    -- Inserindo a nova transação na tabela
    INSERT INTO transacoes (cliente_id, valor, tipo, descricao, saldo)
    VALUES (p_cliente_id, diff, p_tipo, p_descricao, v_novo_saldo);

    -- Retornando o novo saldo e o limite
    RETURN (v_novo_saldo, v_limite)::transacao_result;
END;
$$ LANGUAGE plpgsql;


-- Atualizando a função proc_extrato para usar a função de obter limite
CREATE OR REPLACE FUNCTION proc_extrato(p_cliente_id INTEGER)
RETURNS json AS $$
DECLARE
    v_saldo INTEGER;
    v_limite INTEGER;
    transacoes json;
BEGIN
    -- Obtendo o limite para o cliente
    v_limite := obter_limite_cliente(p_cliente_id);

    lock table transacoes in ACCESS EXCLUSIVE mode;
    -- PERFORM id FROM transacoes WHERE cliente_id = p_cliente_id ORDER BY id DESC FOR UPDATE;

    -- Obtendo o saldo atual da última transação
    -- faltour order by :)
    SELECT saldo INTO v_saldo FROM transacoes WHERE cliente_id = p_cliente_id ORDER BY realizada_em DESC LIMIT 1;
    IF NOT FOUND THEN
        v_saldo := 0; -- Considera saldo zero se não houver transações
    END IF;

    -- Obtendo as últimas transações para o cliente
    SELECT json_agg(row_to_json(t.*)) INTO transacoes FROM (
        SELECT valor, tipo, descricao, TO_CHAR(realizada_em, 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"') AS realizada_em
        FROM transacoes
        WHERE cliente_id = p_cliente_id
        ORDER BY realizada_em DESC
        LIMIT 10
    ) t;

    -- Construindo e retornando o JSON de resultado
    RETURN json_build_object(
        'saldo', json_build_object(
            'total', v_saldo,
            'data_extrato', TO_CHAR(NOW(), 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"'),
            'limite', v_limite
        ),
        'ultimas_transacoes', COALESCE(transacoes, '[]')
    );
END;
$$ LANGUAGE plpgsql;

