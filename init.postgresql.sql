CREATE EXTENSION IF NOT EXISTS pg_prewarm;

-- Criando tabelas de transações individuais para cada cliente usando um bloco DO
DO $$
DECLARE
    i INT;
BEGIN
    FOR i IN 1..5 LOOP
        EXECUTE format('CREATE UNLOGGED TABLE IF NOT EXISTS transacoes_%s (
            id SERIAL PRIMARY KEY,
            valor INTEGER NOT NULL,
            tipo CHAR(1) NOT NULL,
            descricao VARCHAR(255) NOT NULL,
            realizada_em TIMESTAMP NOT NULL DEFAULT NOW()
        )', i);
        EXECUTE format('SELECT pg_prewarm(''transacoes_%s'')', i);
    END LOOP;
END $$;

CREATE OR REPLACE FUNCTION obter_limite_cliente(p_cliente_id INTEGER)
RETURNS INTEGER AS $$
BEGIN
    RETURN CASE p_cliente_id
        WHEN 1 THEN 100000
        WHEN 2 THEN 80000
        WHEN 3 THEN 1000000
        WHEN 4 THEN 10000000
        WHEN 5 THEN  500000
        ELSE -1 
    END;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION proc_transacao(p_cliente_id INT, p_valor INT, p_tipo VARCHAR, p_descricao VARCHAR)
RETURNS TABLE(saldo INT, limite INT) AS $$
DECLARE
    diff INT;
    v_saldo INT;
    v_limite INT;
    tabela_transacao TEXT := 'transacoes_' || p_cliente_id;
    ultima_transacao RECORD;
BEGIN
    -- Obtendo o limite para o cliente
    v_limite := obter_limite_cliente(p_cliente_id);

    -- Determinando o valor da transação (positivo para créditos, negativo para débitos)
    IF p_tipo = 'd' THEN
        diff := p_valor * -1;
    ELSE
        diff := p_valor;
    END IF;
    
    EXECUTE format('LOCK TABLE %I IN EXCLUSIVE MODE', tabela_transacao);

    -- Buscando a última transação para calcular o saldo atual
    EXECUTE format('SELECT valor FROM %I ORDER BY id DESC LIMIT 1', tabela_transacao) INTO ultima_transacao;
    IF NOT FOUND THEN
        v_saldo := 0;
    ELSE
        v_saldo := ultima_transacao.valor;
    END IF;

    -- Calculando o novo saldo
    v_saldo := v_saldo + diff;

    -- Verificando se a nova transação excede o limite
    IF v_saldo < (-1 * v_limite) THEN
        RAISE EXCEPTION 'Limite ultrapassado. Saldo: %, Limite: %', v_saldo, v_limite;
    END IF;

    -- Inserindo a nova transação se não exceder o limite
    EXECUTE format('INSERT INTO %I (valor, tipo, descricao) VALUES (%L, %L, %L)', tabela_transacao, diff, p_tipo, p_descricao);

    -- Retornando o saldo e limite atualizados
    RETURN QUERY SELECT v_saldo, v_limite;
END;
$$ LANGUAGE plpgsql;


CREATE OR REPLACE FUNCTION proc_extrato(p_cliente_id INTEGER)
RETURNS json AS $$
DECLARE
    v_saldo INTEGER;
    v_limite INTEGER; -- Variável para armazenar o limite
    tabela_transacao TEXT := 'transacoes_' || p_cliente_id;
    transacoes json := '[]';
BEGIN
    -- Obtendo o limite para o cliente usando a função obter_limite_cliente
    v_limite := obter_limite_cliente(p_cliente_id);

    -- Obtendo o saldo atual baseado na última transação
    EXECUTE format('SELECT valor FROM %I ORDER BY id DESC LIMIT 1', tabela_transacao) INTO v_saldo;
    IF NOT FOUND THEN
        v_saldo := 0; -- Considera saldo zero se não houver transações
    END IF;

    -- Obtendo as últimas transações para o cliente
    EXECUTE format('SELECT json_agg(row_to_json(t.*)) FROM (SELECT valor, tipo, descricao, TO_CHAR(realizada_em, ''YYYY-MM-DD"T"HH24:MI:SS.MS"Z"'') AS realizada_em FROM %I ORDER BY realizada_em DESC LIMIT 10) t', tabela_transacao) INTO transacoes;

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
