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

## Creating a native executable

You can run the native executable build in a container using:

```shell script
./mvnw package -Dnative -Dquarkus.native.container-build=true -Dmaven.javadoc.skip=true
```

You can then execute your native executable with: `./target/quarkus-test-1.0.0-SNAPSHOT-runner`

## Create docker image

```shell script
docker build -t iua-sim-client .
```

## Run docker image in bridge network (Failed)

create network:
```
docker network create my_network
```

run containers:
```
docker run -d --name iua-ru-mock -p 9090:9090 --network my_network iua-ru-mock
docker run -d --name iua-sim-serv -p 9000:9000 --network my_network iua-sim-serv 
docker run -d --name iua-sim-client -p 8080:8080 --network my_network iua-sim-client
``` 

inspect the network:
```
docker network inspect my_network -> 
[
    {
        "Name": "my_network",
        "Id": "04da3a0b4a26b3bc4d7944f112e2d286a37f7cfc2503fddeace6252917b67fe3",
        "Created": "2026-07-21T19:19:26.881554129Z",
        "Scope": "local",
        "Driver": "bridge",
        "EnableIPv4": true,
        "EnableIPv6": false,
        "IPAM": {
            "Driver": "default",
            "Options": {},
            "Config": [
                {
                    "Subnet": "172.21.0.0/16",
                    "Gateway": "172.21.0.1"
                }
            ]
        },
        "Internal": false,
        "Attachable": false,
        "Ingress": false,
        "ConfigFrom": {
            "Network": ""
        },
        "ConfigOnly": false,
        "Options": {
            "com.docker.network.enable_ipv4": "true",
            "com.docker.network.enable_ipv6": "false"
        },
        "Labels": {},
        "Containers": {
            "7adc9042c51b23881cf48249048a77b35b8831ac29eb301d02107db3d54eb92a": {
                "Name": "iua-sim-client",
                "EndpointID": "f747f96bee7efd425d34e434787e77c5636ea7d9714fd8d4d55539f00f6f2b7f",
                "MacAddress": "ee:51:9d:8c:e8:bf",
                "IPv4Address": "172.21.0.2/16",
                "IPv6Address": ""
            },
            "ae8623cd9d7deb45e83d99504df68e226337d8efe781e2efa0e29788a8a790ce": {
                "Name": "iua-sim-serv",
                "EndpointID": "63a3f7ffb359d23c14b3100487d1876442a6f3ef10f420cd7dec615f36528c35",
                "MacAddress": "5e:bc:05:9c:ed:dd",
                "IPv4Address": "172.21.0.4/16",
                "IPv6Address": ""
            },
            "ee1bc5c42846c2936bb011fc40d0254d35116903bfd9690b5c36184ac94a0221": {
                "Name": "iua-ru-mock",
                "EndpointID": "17dfe64e29e1cebbe7697db879e457cdf27c4ffc99e225c64e67e68ada73bad9",
                "MacAddress": "be:41:39:28:00:42",
                "IPv4Address": "172.21.0.3/16",
                "IPv6Address": ""
            }
        },
        "Status": {
            "IPAM": {
                "Subnets": {
                    "172.21.0.0/16": {
                        "IPsInUse": 6,
                        "DynamicIPsAvailable": 65530
                    }
                }
            }
        }
    }
]
```

Most “containers on the same custom bridge network can’t talk over HTTP” issues in Docker come down to one of these:

1) You’re calling the service using the wrong port (host vs container port)

On a user-defined bridge network, containers should reach each other using:

the container’s internal port (the port the process listens on inside the container), and
the other container’s name as the hostname (e.g., iua-ru-mock, iua-sim-serv).
The -p 9090:9090 / -p 9000:9000 / -p 8080:8080 parts are for host ↔ container traffic, not container ↔ container.

So if inside iua-sim-client you configured something like:

http://localhost:9090 → that will hit the client container itself, not iua-ru-mock
http://iua-ru-mock:9090 is fine only if iua-ru-mock actually listens on 9090 inside its container

Check what ports the apps actually listen on (often via container logs or by checking the process config).

2) The app inside the container is only listening on 127.0.0.1

Even if containers can resolve each other, HTTP won’t work if the server binds only to loopback.

You need the server (in iua-ru-mock, etc.) to listen on:

0.0.0.0 (all interfaces) or the container’s network interface, not just 127.0.0.1.
If the app listens only on localhost, then other containers can’t reach it.

3) You’re using the wrong hostname inside the network

Within my_network, you typically use the container name as DNS:

http://iua-ru-mock:<internalPort>/...
`http://iua-sim-serv:/...
localhost is always “this container”, not the other one.