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
            realizada_em TIMESTAMP NOT NULL DEFAULT NOW(),
            saldo INTEGER NOT NULL DEFAULT 0
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
    v_saldo_atual INT;
    v_novo_saldo INT;
    v_limite INT;
    tabela_transacao TEXT := 'transacoes_' || p_cliente_id;
BEGIN
    -- Obtendo o limite para o cliente
    v_limite := obter_limite_cliente(p_cliente_id);

    -- Determinando o valor da transação (positivo para créditos, negativo para débitos)
    IF p_tipo = 'd' THEN
        diff := p_valor * -1;
    ELSE
        diff := p_valor;
    END IF;

    -- Obtendo o saldo atual da última transação
    EXECUTE format('SELECT saldo FROM %I ORDER BY id DESC LIMIT 1', tabela_transacao) INTO v_saldo_atual;
    IF NOT FOUND THEN
        v_saldo_atual := 0;
    END IF;

    -- Calculando o novo saldo
    v_novo_saldo := v_saldo_atual + diff;

    -- Verificando se a nova transação excede o limite
    IF v_novo_saldo < (-1 * v_limite) THEN
        RAISE EXCEPTION 'Limite ultrapassado. Saldo: %, Limite: %', v_novo_saldo, v_limite;
    END IF;

    -- Inserindo a nova transação com o novo saldo atualizado
    EXECUTE format('INSERT INTO %I (valor, tipo, descricao, saldo) VALUES (%L, %L, %L, %L)', tabela_transacao, diff, p_tipo, p_descricao, v_novo_saldo);

    -- Retornando o novo saldo e limite
    RETURN QUERY SELECT v_novo_saldo, v_limite;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION proc_transacao(p_cliente_id INT, p_valor INT, p_tipo VARCHAR, p_descricao VARCHAR)
RETURNS TABLE(saldo INT, limite INT) AS $$
DECLARE
    diff INT;
    v_saldo_atual INT;
    v_novo_saldo INT;
    v_limite INT;
    tabela_transacao TEXT := 'transacoes_' || p_cliente_id;
BEGIN
    -- Obtendo o limite para o cliente
    v_limite := obter_limite_cliente(p_cliente_id);

    -- Determinando o valor da transação (positivo para créditos, negativo para débitos)
    IF p_tipo = 'd' THEN
        diff := p_valor * -1;
    ELSE
        diff := p_valor;
    END IF;

    -- Obtendo o saldo atual da última transação
    EXECUTE format('SELECT saldo FROM %I ORDER BY id DESC LIMIT 1', tabela_transacao) INTO v_saldo_atual;
    IF NOT FOUND THEN
        v_saldo_atual := 0;
    END IF;

    -- Calculando o novo saldo
    v_novo_saldo := v_saldo_atual + diff;

    -- Verificando se a nova transação excede o limite
    IF p_tipo = 'd' AND v_novo_saldo < (-1 * v_limite) THEN
        RAISE EXCEPTION 'Limite ultrapassado. Saldo: %, Limite: %', v_novo_saldo, v_limite;
    END IF;

    -- Inserindo a nova transação com o novo saldo atualizado
    EXECUTE format('INSERT INTO %I (valor, tipo, descricao, saldo) VALUES (%L, %L, %L, %L)', tabela_transacao, diff, p_tipo, p_descricao, v_novo_saldo);

    -- Retornando o novo saldo e limite
    RETURN QUERY SELECT v_novo_saldo, v_limite;
END;
$$ LANGUAGE plpgsql;
