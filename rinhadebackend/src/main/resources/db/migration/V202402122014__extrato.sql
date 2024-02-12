CREATE OR REPLACE FUNCTION proc_extrato(p_id integer)
RETURNS json AS $$
DECLARE
    result json;
BEGIN
    SELECT json_build_object(
        'saldo', json_build_object(
            'total', saldo,
            'data_extrato', TO_CHAR(NOW(), 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"'),
            'limite', limite
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
    ) INTO result
    FROM (
        SELECT saldo, limite
        FROM clientes
        WHERE id = p_id
    ) AS subquery;

    RETURN result;
END; $$ LANGUAGE plpgsql;