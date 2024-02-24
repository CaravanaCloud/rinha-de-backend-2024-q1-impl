curl -v -X GET http://localhost:9001/clientes/1/extrato
curl -v -X POST -H "Content-Type: application/json" -d '{"valor": 1, "tipo": "c", "descricao": "teste"}' http:///localhost:9999/clientes/1/transacoes

curl -v -X POST -H "Content-Type: application/json" -d '{"valor": 1, "tipo": "c", "descricao": "teste"}' http:///localhost:9001/clientes/1/transacoes

curl -v -X GET http://localhost:9999/clientes/1/extrato


curl -v -X GET http://localhost:9999/clientes/2/extrato
curl -v -X GET http://localhost:9999/clientes/3/extrato
curl -v -X GET http://localhost:9999/clientes/4/extrato
curl -v -X GET http://localhost:9999/clientes/5/extrato

curl -v -X POST -H "Content-Type: application/json" -d '{"valor": 0, "tipo": "c", "descricao": "teste"}' http:///localhost:9999/clientes/1/transacoes
curl -v -X POST -H "Content-Type: application/json" -d '{"valor": 1, "tipo": "c", "descricao": "teste"}' http:///localhost:9999/clientes/1/transacoes

curl -v -X POST -H "Content-Type: application/json" -d '{"valor": 1,"tipo": "d", "descricao": "teste"}' http:///localhost:9999/clientes/1/transacoes

curl -v -X POST -H "Content-Type: application/json" -d '{"valor": 999999999,"tipo": "d", "descricao": "teste"}' http:///localhost:9999/clientes/1/transacoes

curl -v -X POST -H "Content-Type: application/json" -d '{"valor": 0,"tipo": "c", "descricao": ""}' http:///localhost:9999/clientes/1/transacoes

curl -v -X POST -H "Content-Type: application/json" -d '{"valor": 0,"tipo": "c", "descricao": null}' http:///localhost:9999/clientes/1/transacoes

curl -v -X POST -H "Content-Type: application/json" -d '{"valor": 0,"tipo": "c", "descricao": "tesdasdasdasdste"}' http:///localhost:9999/clientes/1/transacoes
