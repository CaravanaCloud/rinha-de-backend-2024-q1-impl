

curl -v -X POST \
    -H "Content-Type: application/json" \
    -d '{"valor": 10, "tipo": "d", "descricao": "teste"}' \
        http:///localhost:9999/clientes/333/transacoes | jq

curl -v -X GET \
    http://localhost:9999/clientes/333/extrato | jq

