# Step 1.3 — Parse Headers and Body

> **Goal in one sentence:** read all headers into a case-insensitive map,
> then read exactly the number of body bytes the client claimed to send.

---

## Where we left off

Step 1.2 parses the request line and prints headers one by one. Two things
are still missing:

1. **Headers are not structured.** They stream past as `System.out.println`
   lines. You can't look one up.
2. **The body is never read.** A `POST` with a JSON payload — the payload
   sits in the socket buffer, ignored.

This step fixes both.

---

## What you're building

After this step, server output for
`curl -X POST http://localhost:8080/products -H 'Content-Type: application/json' -d '{"name":"Mouse"}'`
looks like:

```
method: POST
path: /products
version: HTTP/1.1
headers:
  host: localhost:8080
  user-agent: curl/8.7.1
  accept: */*
  content-type: application/json
  content-length: 16
body: {"name":"Mouse"}
```

Headers stored in a map you can look up. Body read as exactly the declared
number of bytes.

---

## The concept

### Header block termination — the blank line

HTTP headers end with **one empty line** (just `\r\n` on its own). That's
the signal to stop reading headers and start reading the body (if any).

```
Content-Type: application/json\r\n
Content-Length: 16\r\n
\r\n                             ← blank line, end of headers
{"name":"Mouse"}                 ← body starts here
```

You already handle this — `if (headerLine.isEmpty()) break;`. Same rule.

### Header grammar — `Name: Value`

Each header line is `Name` + `:` + optional space + `Value`. Split on the
**first** `:` only — some header values contain `:` themselves
(`Host: localhost:8080`).

```java
"Host: localhost:8080".split(":", 2)
// ["Host", " localhost:8080"]   ← two-arg split limits to 2 pieces
```

Then `.trim()` the value to drop the leading space.

### Header names are case-insensitive

Per HTTP spec: `Content-Type`, `content-type`, `CONTENT-TYPE` all mean the
same thing. Server must not care which spelling the client used.

Java's `HashMap<String, String>` is case-**sensitive**. Use
`TreeMap<>(String.CASE_INSENSITIVE_ORDER)` — a map where lookups ignore
case:

```java
Map<String, String> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
headers.put("Content-Type", "application/json");
headers.get("content-type");   // returns "application/json"
headers.get("CONTENT-TYPE");   // also returns "application/json"
```

### You cannot just "read until end of stream" for the body

The client keeps the TCP connection open after sending — it wants to reuse
it for the next request (keep-alive) and wait for your response. If the
server naively calls `read()` until it returns -1, it waits forever, because
the client never closes.

So HTTP needs a **length signal**. Two mechanisms:

1. **`Content-Length: 16`** — client tells server how many bytes to read.
   The common case.
2. **`Transfer-Encoding: chunked`** — body arrives in size-prefixed chunks.
   Used when the total length isn't known upfront. Skipped in this course.

Rule: read exactly `Content-Length` characters from the reader, no more, no
less.

### Body is not line-oriented — don't use `readLine()`

`readLine()` splits on `\n`. Bodies may contain newlines mid-content (a JSON
string with `\n` inside). Read a fixed number of characters with `read()`.

---

## Java pieces you'll use

| Thing | What it does |
|---|---|
| `java.util.Map` | Interface for key-value collection. |
| `java.util.TreeMap<>(String.CASE_INSENSITIVE_ORDER)` | Map with case-insensitive keys. |
| `String.split(":", 2)` | Split on `:`, but limit to 2 pieces — value keeps its `:` chars. |
| `String.trim()` | Remove leading/trailing whitespace. |
| `Integer.parseInt(str)` | Parse a decimal string into `int`. |
| `Reader.read(char[], int, int)` | Read up to N chars into a char array, returns how many actually read. |

---

## The change

Working in the same `HttpServer.java`. Replace the current header-printing
loop and add body reading below it.

**Find this block:**

```java
String headerLine;
while ((headerLine = reader.readLine()) != null && !headerLine.isEmpty()) {
    System.out.println(headerLine);
}
```

**Replace with:**

```java
// Read headers into a case-insensitive map.
Map<String, String> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
String headerLine;
while ((headerLine = reader.readLine()) != null && !headerLine.isEmpty()) {
    String[] kv = headerLine.split(":", 2);
    if (kv.length != 2) {
        System.out.println("---- malformed header, skipping: " + headerLine + " ----");
        continue;
    }
    String name  = kv[0].trim();
    String value = kv[1].trim();
    headers.put(name, value);
}

System.out.println("headers:");
for (Map.Entry<String, String> entry : headers.entrySet()) {
    System.out.println("  " + entry.getKey() + ": " + entry.getValue());
}

// Read the body, if the client sent Content-Length.
String contentLengthHeader = headers.get("Content-Length");   // case-insensitive lookup!
if (contentLengthHeader != null) {
    int length = Integer.parseInt(contentLengthHeader);
    char[] bodyChars = new char[length];
    int totalRead = 0;
    while (totalRead < length) {
        int n = reader.read(bodyChars, totalRead, length - totalRead);
        if (n == -1) break;   // client closed early
        totalRead += n;
    }
    String body = new String(bodyChars, 0, totalRead);
    System.out.println("body: " + body);
} else {
    System.out.println("body: (none — no Content-Length header)");
}
```

Add the imports at the top:

```java
import java.util.Map;
import java.util.TreeMap;
```

Two things to notice:

1. **`reader.read()` is called in a loop.** One call may return fewer chars
   than requested (network is chunky). Loop until you have all `length`
   chars, or the stream ends.
2. **`headers.get("Content-Length")` works even if the client sent
   `content-length: 16`.** That's the case-insensitive map earning its keep.

---

## Try it

### Terminal 1 — recompile + run

```bash
javac HttpServer.java
java HttpServer
```

### Terminal 2 — three test shapes

**GET with no body:**
```bash
curl -v http://localhost:8080/hello
```

**POST with JSON body:**
```bash
curl -v -X POST "http://localhost:8080/products" \
    -H 'Content-Type: application/json' \
    -d '{"name":"Mouse","price":19.99}'
```

| Keyword | Meaning |
|---|---|
| `-H 'Header: value'` | Add a request header. Repeatable. |
| `-d '...'` | Request body. Implies `POST` (curl auto-adds `Content-Length` and `Content-Type: application/x-www-form-urlencoded` if you don't override). |
| `\` at end of line | Line continuation in shell. Same as one long line. |

**POST with different header casing** (proves case-insensitive lookup):
```bash
curl -v -X POST "http://localhost:8080/products" \
    -H 'content-type: application/json' \
    -H 'CONTENT-LENGTH: 15' \
    -d '{"name":"Mouse"}'
```

---

## What you should see

For the GET:

```
method: GET
path: /hello
version: HTTP/1.1
headers:
  accept: */*
  host: localhost:8080
  user-agent: curl/8.7.1
body: (none — no Content-Length header)
```

For the POST with JSON:

```
method: POST
path: /products
version: HTTP/1.1
headers:
  accept: */*
  content-length: 30
  content-type: application/json
  host: localhost:8080
  user-agent: curl/8.7.1
body: {"name":"Mouse","price":19.99}
```

Two things to notice:

- **Header keys printed lowercase** even though curl sent them capitalized.
  That's the `TreeMap` sorting them — case-insensitive **comparison** but
  the map still keeps whatever casing was inserted. Sorting by
  case-insensitive order sometimes surfaces the lowercase version first.
- **Body prints exactly** what you sent — no missing/extra characters.

For the GET, the `else` branch fires (`body: (none — no Content-Length
header)`) instead of hanging trying to read a body that isn't there.

---

## Your turn

### 1. Print body length actually read

Print `body length: N` alongside the body. Send a JSON body and confirm
`N == content-length`.

### 2. Reject requests with body > 1 MB

If `Content-Length > 1_048_576`, print `body too large: N bytes` and
`continue`. Test:

```bash
# Generate a 5 MB body of 'a' characters
yes a | head -c 5242880 | curl -v -X POST "http://localhost:8080/x" \
    -H 'Content-Type: text/plain' --data-binary @-
```

| Keyword | Meaning |
|---|---|
| `yes a` | Print `a` forever. |
| `head -c 5242880` | Take first 5 MB, then close pipe. |
| `--data-binary @-` | Read body from stdin, no character mangling. |

### 3. Handle `Content-Length` that lies

Send a header claiming 100 bytes but only actually send 5.

```bash
printf 'POST /x HTTP/1.1\r\nHost: localhost\r\nContent-Length: 100\r\n\r\nhello' | nc localhost 8080
```

What happens? Your server hangs waiting for 95 more bytes. Time it —
netcat should exit after printf's stdin closes, but your server keeps
reading. Ctrl+C the server when you've seen the behavior.

**Real fix:** set a read timeout on the socket. Not required for this
step, but that's why `Socket.setSoTimeout(millis)` exists.

---

## Acceptance checks

- [ ] All headers print as key/value pairs (not raw lines).
- [ ] Header lookup is case-insensitive — `Content-Type` and
      `content-type` both retrieve the same value.
- [ ] `curl -X POST localhost:8080/products -H 'Content-Type: application/json' -d '{"name":"Mouse"}'`
      prints the body **exactly**, no missing or extra characters.
- [ ] A `GET` with no body does **not** hang waiting for one — logs
      `body: (none — no Content-Length header)` and moves on.

---

## Questions worth asking

- Why does reading until EOF deadlock on a keep-alive connection?
- What is `Transfer-Encoding: chunked` and why does it exist?
- What happens if `Content-Length` lies? (You just saw one case.)
- Why is the body read with `read()` and not `readLine()`?
- What byte-vs-character issues bite here? (`café` has 4 chars but 5 UTF-8
  bytes — does your code count right?)
- How does Spring's `HttpMessageConverter` turn a body byte stream into a
  `@RequestBody Product` object?

---

## Files touched

- `phase-01-http-from-scratch/HttpServer.java` — headers into map, body read

## Next step

**[Step 1.4 — Write a valid response](step-1.4-write-response.md)**
(created when you're ready)
