# IUA Simulation Client

Simulates the CHI:IUA Client Actor with a Get Access Token [ITI-71] transaction for the client credential flow the
and authorization code flow as specified in the [Swiss extension of the IUA profile](http://build.fhir.org/ig/ehealthsuisse/ch-epr-fhir/).

In the authorization code flow variant, the client simulator uses a mocked IdP assertion compliant with the specification
in Annex 8 of the Swiss ordinances in the OpenId Connect ID token. The ID token is a signed JWT (usually signed with RS256). 
The header contains alg and kid and the signature verifies the issuers public key (JWKS). 

The attributes in the payload are:
- iss : issuer
- sub : subject (user id)
- aud : client_id (audience)
- exp : expiry (epoch)
- iat : issued at
- nonce : nonce from auth request

The IdP assertion may look like: 
````
{
  "iss": "http://client-simulator.org",
  "sub": "Bearer",
  "aud": "my-client-id",
  "exp": 1785409984,
  "iat": 1785409384,
  "nonce": "n-0S6_WzA2Mj",
  "name": "Martina Mustermann"
}
````

The client simulator provides an inspection endpoint for the OIDC token response and the id_token. Please check the
[bruno 4.0.0](https://www.usebruno.com/downloads) collection of http transactions provided with this project. 
See https://www.jwt.io for decoding the id_token.

## Testing locally
For testing locally you may run this application from the terminal or in a docker container together with the
[iua-ru-mock](https://github.com/msmock/iua-ru-mock) application which mocks the reporting and registry interfaces
of the Gazelle Test Environment and the IUA Authorization server simulator [iua-sim-serv](https://github.com/msmock/iua-sim-serv). 

The project also contains a [bruno 4.0.0](https://www.usebruno.com/downloads) collection of http transactions to 
simulate the api calls for test setup and resume and the simulation sequences.


## Running the application in dev mode
You can run your application in dev mode that enables live coding using:

```shell script
./mvnw quarkus:dev
```

> **_NOTE:_**  Quarkus ships with a Dev UI, which is available in dev mode only at <http://localhost:8080/q/dev/>.

## Run docker image in bridge network

create network (if not exists):
```
docker network create my_network
```

Compile: 
```
./mvnw clean package -Dmaven.javadoc.skip=true
```

Build the image iua-sim-client:
```
docker build -f src/main/docker/Dockerfile.jvm -t iua-sim-client .
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
Localhost is always the container the request originates, not the another one on your machine.