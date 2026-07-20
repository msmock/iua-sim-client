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

## Packaging and running the application

The application can be packaged using:

```shell script
./mvnw package
```

It produces the `quarkus-run.jar` file in the `target/quarkus-app/` directory.
Be aware that it’s not an _über-jar_ as the dependencies are copied into the `target/quarkus-app/lib/` directory.

The application is now runnable using `java -jar target/quarkus-app/quarkus-run.jar`.

If you want to build an _über-jar_, execute the following command:

```shell script
./mvnw package -Dquarkus.package.jar.type=uber-jar
```

The application, packaged as an _über-jar_, is now runnable using `java -jar target/*-runner.jar`.

## Creating a native executable

You can create a native executable using:

```shell script
./mvnw package -Dnative
```

Or, if you don't have GraalVM installed, you can run the native executable build in a container using:

```shell script
./mvnw package -Dnative -Dquarkus.native.container-build=true
```

You can then execute your native executable with: `./target/quarkus-test-1.0.0-SNAPSHOT-runner`

If you want to learn more about building native executables, please consult <https://quarkus.io/guides/maven-tooling>.

