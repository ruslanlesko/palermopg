# PalermoPG - picture gallery server
Created by [Ruslan Lesko](https://leskor.com)

PalermoPG provides solution for self-hosted picture gallery using JWT as authentication mechanism. Please note that front-end (and authentication service) for this API is not present. PalermoPG is designed to be a building block for custom picture management systems.

## API
Port number: 8081

### Picture operations
* GET `/pic/{userId}/{pictureId}` returns picture under provided id for specific user in JPEG format
* POST `/pic/{userId}?albumId={albumdId}` uploads picture in JPEG format under provided album id, returns newly created picture id
* POST `/pic/{userId}/{pictureId}/rotate` rotates picture
* DELETE `/pic/{userId}/{pictureId}` deletes picture, returns deleted picture id on success

### Album operations
* GET `/album/{userId}` returns list of albums for user
* GET `/album/{userId}/{albumId}` returns list of pictures contained in album
* POST `/album/{userId}` creates album for user, returns newly created album id
* PATCH `/album/{userId}/{albumId}` renames album
* POST `/album/{userId}/{albumId}/share` shares album with provided list of users
* DELETE `/album/{userId}/{albumId}` deletes album, returns deleted album id on success

#### Payload of album list
```
[
    {
        "id": 42,
        "userId": 69,
        "name": "Vacation",
        "sharedUsers": [25, 27]
    },
    { ... }
]
```

#### Payload of picutres contained in album
```
[
    {
        "userId": 42,
        "pictureId": 25
    },
    {
        "userId": 69,
        "pictureId": 27
    },
    { ... }
]
```

#### Payload of create album body
```
{
    "name": "New Year Party"
}
```

#### Payload of album rename body
```
{
    "name": "2020 NY Party"
}
```

#### Payload of share album body
```
{
    "sharedUsers": [42, 25, 27]
}
```

## Test
To run unit test execute `mvn test` in the projec root

## Build and run
Make sure that you have Java 11 or later, MongoDB and RSA public key (described below) on your machine.

Run `mvn package` in the project root to get application jar with dependencies.

### Required Environment Variables
* `PIC_KEY` - path to RSA ppublicrivate key in PEM format
* `PIC_DATA` - path directory which will be used as a storage for pictures
* `PIC_DB` - URL to mongo DB (mongodb://username:password@localhost/db)

## RSA Key Generation On Linux
1. Generate a private key `openssl genrsa -out private.pem 2048`
2. Export public key `openssl rsa -in private.pem -outform PEM -pubout -out public.pem`