# Instructions for candidates

This is the Java version of the Payment Gateway challenge. If you haven't already read this [README.md](https://github.com/cko-recruitment/) on the details of this exercise, please do so now.

## Requirements
- JDK 17
- Docker

## Template structure

src/ - A skeleton SpringBoot Application

test/ - Some simple JUnit tests

imposters/ - contains the bank simulator configuration. Don't change this

.editorconfig - don't change this. It ensures a consistent set of rules for submissions when reformatting code

docker-compose.yml - configures the bank simulator


## API Documentation
For documentation openAPI is included, and it can be found under the following url: **http://localhost:8090/swagger-ui/index.html**

**Feel free to change the structure of the solution, use a different library etc.**

## Solution

### Running
```bash
docker compose up -d          # bank simulator on :8080
```
```bash
./gradlew bootRun             # gateway on :8090
```
```bash
./gradlew test
```

### API
| Request | Result |
|---|---|
| `POST /payment` valid, bank authorizes | `200` `status: Authorized` |
| `POST /payment` valid, bank declines | `200` `status: Declined` |
| `POST /payment` invalid | `400` `{"status": "Rejected", "errors": [...]}` — bank is not called, nothing stored |
| `POST /payment` bank unavailable / error | `502` `{"message": ...}` — nothing stored; outcome unknown, see [Idempotency and retries](#idempotency-and-retries) |
| `GET /payment/{id}` | `200` payment, or `404` |

Request: `card_number`, `expiry_month`, `expiry_year`, `currency`, `amount`, `cvv`.
Response: `id`, `status`, `cardNumberLastFour`, `expiryMonth`, `expiryYear`, `currency`, `amount`.

### Examples
Captured against the simulator. The last digit of the card number selects the simulator outcome: odd authorizes, even declines, `0` returns `503`.

```bash
curl -i -X POST localhost:8090/payment -H 'Content-Type: application/json' \
  -d '{"card_number": "2222405343248877", "expiry_month": 4, "expiry_year": 2028, "currency": "GBP", "amount": 100, "cvv": "123"}'
```
```
HTTP/1.1 200
{"id":"2b0bd3ce-9bd7-4b7f-95dc-d4c384c620fd","status":"Authorized","cardNumberLastFour":"8877","expiryMonth":4,"expiryYear":2028,"currency":"GBP","amount":100}
```

Card ending `2`, same request otherwise:
```
HTTP/1.1 200
{"id":"05b3721b-305c-40e8-a055-b2da131aa22d","status":"Declined","cardNumberLastFour":"8112","expiryMonth":4,"expiryYear":2028,"currency":"GBP","amount":100}
```

Invalid request, bank not called:
```bash
curl -i -X POST localhost:8090/payment -H 'Content-Type: application/json' \
  -d '{"card_number": "1234", "expiry_month": 13, "expiry_year": 2028, "currency": "JPY", "amount": 0, "cvv": "12"}'
```
```
HTTP/1.1 400
{"status":"Rejected","errors":["amount must be a positive integer in minor currency units","card_number must be 14-19 digits","currency must be one of USD, EUR, GBP","cvv must be 3-4 digits","expiry_month must be between 1 and 12"]}
```

Card ending `0`, simulator returns `503`:
```
HTTP/1.1 502
{"message":"Acquiring bank unavailable, please retry later"}
```

Retrieve:
```bash
curl -i localhost:8090/payment/2b0bd3ce-9bd7-4b7f-95dc-d4c384c620fd
```
```
HTTP/1.1 200
{"id":"2b0bd3ce-9bd7-4b7f-95dc-d4c384c620fd","status":"Authorized","cardNumberLastFour":"8877","expiryMonth":4,"expiryYear":2028,"currency":"GBP","amount":100}
```
Unknown id:
```
HTTP/1.1 404
{"message":"Page not found"}
```
### Idempotency and retries
A `502` means the bank call failed or timed out and the outcome is unknown: the bank may or may not have authorized, so the gateway never retries the bank itself. The usual fix for the merchant-to-gateway leg is an optional `Idempotency-Key` header that returns the stored payment for a repeated request instead of calling the bank again; it is left out because the challenge does not ask for it. The gateway-to-bank leg cannot be closed without the acquirer supporting an idempotency key or a status lookup, which the simulator does not provide.

### Packaging and CI (not implemented)
A multi-stage `Dockerfile` plus a `gateway` service in `docker-compose.yml` would let a reviewer start the gateway and the simulator with one `docker compose up`, and a GitHub Actions workflow running `./gradlew test` on JDK 17 would prove the build on a clean machine. Both were left out because the project is only run locally for this exercise and neither changes the functional behaviour.
