CREATE OR REPLACE FUNCTION get_extrato(p_id INTEGER)
RETURNS json AS $$
DECLARE
    result json;
BEGIN
    SELECT json_build_object(
        'total', saldo,
        'data_extrato', TO_CHAR(NOW(), 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"'),
        'limite', limite
    ) INTO result
    FROM (
        SELECT saldo, limite
        FROM clientes
        WHERE id = p_id
    ) AS subquery;

    RETURN result;
END; $$ LANGUAGE plpgsql;