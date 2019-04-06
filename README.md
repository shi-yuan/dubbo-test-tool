# dubbo-test-tool
Simple dubbo api test tool by http

```bash
curl -X POST "localhost:9999/dubbo/test" -H 'Content-Type: application/json' -d'
{
	"dependency": "com.mycompany:dubbo-test-api:1.0.0",
	"url": "dubbo://127.0.0.1:20889",
	"method": "com.mycompany.test.sayHello",
	"args": ["shi-yuan"]
}
'

{
    "success": true,
    "code": 0,
    "data": "hello, shi-yuan !"
}
```
