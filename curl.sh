

curl -s -X POST \
    -H "Content-Type: application/json" \
    -d '{"valor": 10, "tipo": "d", "descricao": "teste"}' \
        http:///localhost:9999/clientes/1/transacoes | jq

curl -s -X GET \
    http://localhost:9999/clientes/1/extrato | jq

