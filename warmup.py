# make_requests.py
import requests

print("Warming up")

for i in range(1, 43):
    print(f"Sending request {i}")
    try:
        # Replace the URLs with your actual endpoints
        requests.get("http://your_service:9999/clientes/333/extrato")
        requests.post("http://your_service:9999/clientes/333/transacoes",
                      json={"valor": 0, "tipo": "d", "descricao": "warmup"})
    except requests.RequestException as e:
        print(f"Request {i} failed: {e}")

print("Warmup done")
