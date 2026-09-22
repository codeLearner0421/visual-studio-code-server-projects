# Step 1.2 — Parse the Request Line

> **Goal in one sentence:** extract **method**, **path**, and **HTTP version**
> from the first line of the request and print them as structured fields.

---

## Where we left off

Step 1.1 prints raw text. Every line is just a `String`. The server does not
understand what any of it means.

```
GET /hello HTTP/1.1
Host: localhost:8080
```

This step teaches the server to look at the **first line** and split it into
its three parts, which is the entry point for every real router.

---

## The concept

### The request line has a fixed grammar

```
METHOD SP PATH SP VERSION CRLF
```

- **`METHOD`** — one uppercase token: `GET`, `POST`, `PUT`, `PATCH`, `DELETE`,
  `HEAD`, `OPTIONS`.
- **`SP`** — one ASCII space (`0x20`). Exactly one. Two spaces is malformed.
- **`PATH`** — starts with `/`, may include a query string:
  `/products?category=books&sort=price`.
- **`VERSION`** — `HTTP/1.0`, `HTTP/1.1`, or `HTTP/2.0`.
- **`CRLF`** — carriage return + line feed (`\r\n`, bytes `0x0D 0x0A`).

Three parts, separated by a single space. That's it — HTTP's simplicity is why
tools like `curl` and `netcat` can drive it.

### Why `\r\n` and not just `\n`

Old teletype machines. `\r` returned the print head to column 0; `\n`
advanced the paper by one line. Two separate mechanical operations. HTTP
inherited the convention through email (RFC 822) and never dropped it.

`BufferedReader.readLine()` hides `\r\n` — it strips both and returns you the
line without terminators. Convenient, but hides a real detail: some servers
that hand-roll parsing crash on `\n`-only lines because they expect `\r\n`.

### The query string is part of the path (for now)

`GET /products?category=books HTTP/1.1` — the path field is literally
`/products?category=books`. HTTP itself does not split the `?`. The server
decides whether to treat query params separately (Step 1.5 does).

---

## Java pieces you'll use

| Thing | What it does |
|---|---|
| `String.split(" ")` | Cuts a string at every space, returns `String[]`. |
| `String[]` indexing | `parts[0]`, `parts[1]`, `parts[2]`. |
| `parts.length` | Number of elements — defensive check for malformed lines. |
| `String.equals("GET")` | Compare strings by value, not by `==` (which compares references). |

---

## The change

You're modifying `HttpServer.java` from Step 1.1. Only one change — replace
the inner `while` loop with a version that parses the **first** line
differently from subsequent lines.

**Find this block:**

```java
String line;
while ((line = reader.readLine()) != null) {
    System.out.println(line);
    if (line.isEmpty()) {
        break;
    }
}
```

**Replace with:**

```java
// Read the request line — the very first line — and split it into fields.
String requestLine = reader.readLine();
if (requestLine == null || requestLine.isEmpty()) {
    System.out.println("---- empty or dropped connection, skipping ----");
    continue;   // go back to accept() for the next client
}

String[] parts = requestLine.split(" ");
if (parts.length != 3) {
    System.out.println("---- malformed request line: " + requestLine + " ----");
    continue;
}

String method  = parts[0];
String path    = parts[1];
String version = parts[2];
System.out.println("method="  + method);
System.out.println("path="    + path);
System.out.println("version=" + version);

// Read the headers — every subsequent line, until the blank line.
String headerLine;
while ((headerLine = reader.readLine()) != null) {
    System.out.println(headerLine);
    if (headerLine.isEmpty()) {
        break;
    }
}
```

Two things to notice:

1. **The first line is treated differently from the rest.** That's the whole
   point of "parsing" — you distinguish request line from headers.
2. **`continue`** jumps back to the top of the outer `while` (which contains
   `accept()`). Bad connection = drop this client, wait for the next.

---

## Try it

### Terminal 1 — recompile + run

```bash
cd e-commerce/phase-01-http-from-scratch
javac HttpServer.java
java HttpServer
```

| Keyword | Meaning |
|---|---|
| Everything from Step 1.1 | Same — `cd`, `javac`, `java`, `HttpServer` |

### Terminal 2 — try several request shapes

```bash
curl -v http://localhost:8080/products
curl -v -X POST http://localhost:8080/products
curl -v "http://localhost:8080/products?category=books&sort=price"
```

| Keyword | Meaning |
|---|---|
| `-X POST` | Change the HTTP **method** from default `GET` to `POST`. |
| `"..."` around URL | Shell quoting — protects `?` and `&` from being interpreted by zsh. |

---

## What you should see

For `curl -v http://localhost:8080/products`:

```
---- client connected from /... ----
method=GET
path=/products
version=HTTP/1.1
Host: localhost:8080
User-Agent: curl/8.7.1
Accept: */*

---- end of headers, not sending response ----
```

For `curl -X POST http://localhost:8080/products`:

```
method=POST
path=/products
...
```

For the URL with query string:

```
method=GET
path=/products?category=books&sort=price
version=HTTP/1.1
```

Notice `path` includes the `?` and everything after. HTTP does not split it.
Your server would if you asked it to.

---

## Your turn

### 1. Split path and query string

Right now `path=/products?category=books` is one string. Split it — print
`path=/products` and `query=category=books&sort=price` separately.

Hint: `String.indexOf('?')` returns the index or `-1`. `String.substring(start, end)`.

Try `curl` with **no** query string. Does your code still work? What does
`indexOf` return? Handle that case.

### 2. Reject unknown methods

Print `unsupported method: PATCH` and `continue` if method isn't one of
`GET`, `POST`, `PUT`, `DELETE`.

Test with `curl -X PATCH http://localhost:8080/x`. Test with
`curl -X GET http://localhost:8080/x` — still works?

### 3. Send garbage on purpose

Use `nc` (netcat) to send raw junk:

```bash
printf 'garbage\r\n\r\n' | nc localhost 8080
```

| Keyword | Meaning |
|---|---|
| `printf 'garbage\r\n\r\n'` | Emit exactly `garbage`, CRLF, CRLF (empty line). |
| `\|` | Pipe — take stdout of the left side, feed it as stdin of the right side. |
| `nc localhost 8080` | Netcat — raw TCP client. No HTTP smarts. Sends whatever you feed it. |

Server should print `malformed request line: garbage` and **not crash**. Then
`curl` from terminal 2 again — server still accepts new connections?

---

## Acceptance checks

Tick these off:

- [x] `GET /products HTTP/1.1` prints `method=GET path=/products version=HTTP/1.1`.
- [x] `curl -X POST localhost:8080/products` prints `method=POST`.
- [x] `curl "localhost:8080/products?page=2&size=10"` prints something. You
      can **state** what your `path` holds — the query string or a stripped path.
      Either is fine; know which.
- [x] Sending garbage (`printf 'nonsense\r\n\r\n' | nc localhost 8080`) does
      not crash the server. It logs and moves on.
- [x] Headers still print after the parsed request line (from Step 1.1).

---

## Questions worth asking

- Why is the line terminator `\r\n` and not `\n`? What breaks if you send `\n` only?
- What if the path contains a space (e.g., `%20` decoded)? Does `split(" ")`
  still work?
- Why compare strings with `.equals()` and not `==` in Java?
- What is the maximum request line length? Why do real servers cap it?
- How does Spring's `HandlerMapping` do the same job for you?

---

## Files touched

- `phase-01-http-from-scratch/HttpServer.java` — modified inner loop

## Next step

**[Step 1.3 — Parse headers and body](step-1.3-parse-headers-and-body.md)**
(created when you're ready)
