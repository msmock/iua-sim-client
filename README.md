# IUA Simulation Client

Simulates the CHI:IUA Client Actor with a Get Access Token [ITI-71] with the client credential flow.

## OpenID Connect Mock

````
{
"access_token":"SlAV32hkKG",
"token_type":"Bearer",
"expires_in":3600,
"refresh_token":"8xLOxBtZp8",
"id_token":"eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
}
````

ID token is a signed JWT (usually signed with RS256). The header contains alg and kid and the signature
verifies the issuers public key (JWKS).

- iss : issuer
- sub : subject (user id)
- aud : client_id (audience)
- exp : expiry (epoch)
- iat : issued at
- nonce : nonce from auth request

````
{
  "iss": "https://auth.example.com",
  "sub": "248289761001",
  "aud": "s6BhdRkqt3",
  "exp": 4784121600,
  "iat": 1715654400,
  "nonce": "n-0S6_WzA2Mj",
  "email": "janedoe@example.com",
  "name": "Jane Doe"
}
````

The client simulator provides an endpoint for the OIDC token response and the id_token. For debugging purposes, see https://www.jwt.io. 

## Running the application in dev mode

You can run your application in dev mode that enables live coding using:

```shell script
./mvnw quarkus:dev
```

> **_NOTE:_**  Quarkus ships with a Dev UI, which is available in dev mode only at <http://localhost:8080/q/dev/>.

## Create docker image

```shell script
docker build -t iua-sim-client .
```

## Run docker image in bridge network

create network:
```
docker network create my_network
```

Compile: 
```
./mvnw clean package -Dmaven.javadoc.skip=true
```

Build the image iua-sim-client:
```
# docker build -f src/main/docker/Dockerfile.jvm -t iua-sim-client .
```

Then run the container:
```
docker run -d --name iua-sim-client -p 8080:8080 --network my_network iua-sim-client
```


## Docker hints:

On a user-defined bridge network, containers should reach each other using
- the container’s internal port (the port the process listens on inside the container), and
- the other container’s name as the hostname (e.g., iua-ru-mock, iua-sim-serv).
- The -p 9090:9090 / -p 9000:9000 / -p 8080:8080 parts are for host ↔ container traffic, not container ↔ container.

So if inside iua-sim-client you configured something like:
- ```http://localhost:9090``` that will hit the client container itself, not the service in other container
- ```http://{$other-container-name}:9090``` is fine only if the conatiner named {$other-container-name} actually listens on 9090 inside it's container

Even if containers can resolve each other, HTTP won’t work if the server binds only to loopback address.
You need the server to listen on:
- 0.0.0.0 (all interfaces) or the container’s network interface, not just 127.0.0.1.
- If the app listens only on localhost, then other containers can’t reach it.

Within the bridge network (e.g., my_network), you must use the container name as DNS:
```
http://{$container-name}:<internalPort>/...
```

Localhost is always 'this container' not the other one.