docker run -d \
  --name core \
  -e PIC_KEY=/keys/key.pub \
  -e PIC_DATA=/data \
  -v /Users/ruslan_lesko/Projects/pichub/data:/data \
  -v /Users/ruslan_lesko/Projects/pichub/keys:/keys \
  -p 8081:8081 pichub-core:latest