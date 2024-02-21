curl -v -X GET http://localhost:9999/clientes/1/extrato

curl -v -X POST -H "Content-Type: application/json" -d '{"valor": 0,"tipo": "c", "descricao": "teste"}' http:///localhost:9999/clientes/1/transacoes

curl -v -X POST -H "Content-Type: application/json" -d '{"valor": 0,"tipo": "c", "descricao": ""}' http:///localhost:9999/clientes/1/transacoes
curl -v -X POST -H "Content-Type: application/json" -d '{"valor": 0,"tipo": "c", "descricao": null}' http:///localhost:9999/clientes/1/transacoes
curl -v -X POST -H "Content-Type: application/json" -d '{"valor": 1.2,"tipo": "c", "descricao": "teste"}' http:///localhost:9999/clientes/1/transacoes
