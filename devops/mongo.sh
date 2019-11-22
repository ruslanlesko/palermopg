docker run -d \
  --name mongo \
  -e AUTH=yes \
  -e MONGODB_ADMIN_USER=pcadmin \
  -e MONGODB_ADMIN_PASS=pcadminpass \
  -e MONGODB_APPLICATION_DATABASE=pichubdb \
  -e MONGODB_APPLICATION_USER=pcusr \
  -e MONGODB_APPLICATION_PASS=pcpwd \
  -v /Users/ruslan_lesko/Projects/pichub/data/db:/data/db \
  -p 27017:27017 aashreys/mongo-auth:latest