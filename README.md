![PalermoPG logo](https://github.com/ruslanlesko/palermopg/raw/master/logo/main.png)
# PalermoPG - picture gallery server
Created by [Ruslan Lesko](https://leskor.com)

![badge](https://action-badges.now.sh/ruslanlesko/palermopg)

PalermoPG provides a solution for a self-hosted picture gallery using JWT as an authentication mechanism. Please note that the front-end (and authentication service) for this API is not present. PalermoPG is designed to be a building block for custom picture management systems.

## API
Port number: 8081

### Picture operations
* GET `/pic/{userId}/{pictureId}` returns picture under provided id for specific user in JPEG format (optional fullSize=true parameter can be provided for non-compressed image)
* GET `/pic/{userId}/{pictureId}?downloadCode=123xyz` returns downloadable picture in maximum resolution
* POST `/pic/{userId}?albumId={albumdId}` uploads picture in JPEG format under provided album id, returns newly created picture id
* POST `/pic/{userId}/{pictureId}/rotate` rotates picture
* DELETE `/pic/{userId}/{pictureId}` deletes picture, returns deleted picture id on success

### Album operations
* GET `/album/{userId}` returns list of albums for user
* GET `/album/{userId}/{albumId}` returns list of pictures contained in album
* GET `/album/{userId}/{albumId}/download?code={downloadCode}` downloads album as a zip archive
* POST `/album/{userId}` creates album for user, returns newly created album id
* PATCH `/album/{userId}/{albumId}` updates album (either rename or change ordering)
* POST `/album/{userId}/{albumId}/share` shares album with provided list of users
* DELETE `/album/{userId}/{albumId}` deletes album, returns deleted album id on success

### Storage operations
* GET `/storage/{userId}` returns consumed by user storage in bytes

#### Payload of album list
```
[
    {
        "id": 42,
        "userId": 69,
        "name": "Vacation",
        "sharedUsers": [25, 27],
        "downloadCode": "sav98d98f2ff",
        "isChronologicalOrder": false
    },
    { ... }
]
```

#### Payload of picutres contained in album
```
[
    {
        "userId": 42,
        "pictureId": 25,
        "downloadCode": "123xyz"
    },
    {
        "userId": 69,
        "pictureId": 27,
        "downloadCode": "9873abc"
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

#### Payload of album update body
```
{
    "name": "2020 NY Party",
    "isChronologicalOrder": true
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
* `PIC_KEY` - path to RSA public key in PEM format
* `PIC_DATA` - path directory which will be used as a storage for pictures
* `PIC_DB` - URL to Mongo DB (mongodb://username:password@localhost/db)
* `PIC_DB_NAME` - database name
* `PIC_ADMIN_ID` - admin's user ID

## RSA Key Generation On Linux
1. Generate a private key `openssl genrsa -out private.pem 2048`
2. Export public key `openssl rsa -in private.pem -outform PEM -pubout -out public.pem`