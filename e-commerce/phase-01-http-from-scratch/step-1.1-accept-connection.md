# Step 1.1 — Accept a Connection

> **Goal in one sentence:** write a program that listens on TCP port 8080 and
> prints whatever a client sends it.

---

## What you're building

A tiny server. It doesn't understand HTTP yet — it just:

1. Registers with the operating system: *"I want to hear about anything hitting
   port 8080."*
2. Sits and waits.
3. When something connects, it reads the bytes and prints them.
4. It sends nothing back. `curl` will hang. That's correct for this step.

By the end you will have seen the **raw HTTP request** that browsers, apps, and
`curl` send every day — text you never wrote, produced by the client library.

---

## The concept

### TCP vs HTTP

Two different layers, often confused.

- **TCP** is the transport. A reliable, ordered stream of bytes between two
  computers. TCP doesn't care what those bytes mean.
- **HTTP** is a text protocol *carried inside* TCP. It's just an agreement:
  *"the first line will be `METHOD PATH VERSION`, then headers, then blank
  line, then body."*

This step opens a TCP socket. HTTP is what happens to arrive inside.

### Server vs client

- A **server** waits. It publishes an address (host:port), then blocks until
  someone shows up. It never initiates.
- A **client** initiates. It picks a server's address and connects.

`curl` is a client. Your program is a server. The asymmetry matters — a server
can serve many clients, a client only talks to who it dialed.

### The port

`8080` is a number the OS uses as a mailbox label. Any TCP packet arriving at
your machine with destination port 8080 gets delivered to whichever process
registered to listen there. Ports below 1024 are privileged (need `sudo`), so
we use 8080 for local dev.

### Blocking

`accept()` is a **blocking call**. Your thread stops executing until a client
connects. The OS parks the thread and doesn't wake it until a TCP handshake
completes. This is not a bug — it's the fundamental "wait for work" primitive
that every server is built on.

---

## Java pieces you'll use

| Class / method | What it is | What matters |
|---|---|---|
| `java.net.ServerSocket` | The listener. Owns the port. | Constructor `new ServerSocket(port)` binds. `.accept()` blocks. |
| `java.net.Socket` | One live conversation with one client. | Returned by `accept()`. Has input and output streams. |
| `Socket.getInputStream()` | The bytes the client is sending. | Raw `InputStream`. |
| `java.io.InputStreamReader` | Turns bytes into characters. | Needs an encoding — HTTP defaults to `UTF-8`. |
| `java.io.BufferedReader` | Turns characters into lines. | `.readLine()` returns a `String` up to `\r\n`, or `null` at end of stream. |

The `InputStream → InputStreamReader → BufferedReader` chain is the standard
"I want to read text from a network socket line by line" wrapping. Ugly, but
that's Java IO.

---

## Worked example

This is the full program. Read it, then run it. **Don't skim** — the comments
are the lesson.

```java
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;

public class HttpServer {

    public static void main(String[] args) throws Exception {
        int port = 8080;

        // 1. Open the listener. This tells the OS to route any TCP traffic
        //    arriving at port 8080 to this process. If the port is already in
        //    use, this line throws BindException — that's the OS refusing.
        ServerSocket serverSocket = new ServerSocket(port);
        System.out.println("listening on " + port);

        // 2. Serve forever. Each loop iteration handles one client.
        while (true) {

            // 2a. Wait for a client. This BLOCKS. The thread is parked by
            //     the OS until a TCP handshake completes. The returned Socket
            //     represents that one specific client's connection.
            Socket clientSocket = serverSocket.accept();
            System.out.println("---- client connected from " + clientSocket.getRemoteSocketAddress() + " ----");

            // 2b. Wrap the raw byte stream so we can read it as lines of text.
            //     HTTP is UTF-8-safe ASCII in the request line and headers.
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(clientSocket.getInputStream()));

            // 2c. Read line by line. HTTP requests end their header block with
            //     a blank line ("" — an empty string, not null). If we didn't
            //     stop at the blank line we'd hang waiting for a body that may
            //     never come, because the client isn't going to close the
            //     connection until we respond.
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
                if (line.isEmpty()) {
                    break;
                }
            }

            System.out.println("---- end of headers, not sending response ----");

            // NOTE: We deliberately do NOT close clientSocket here. That would
            // send a TCP FIN and curl would exit with "empty reply from server".
            // We want curl to hang so you can observe the "server never
            // responded" state. In Step 1.4 we'll write a real response.
        }
    }
}
```

---

## Try it

### Terminal 1 — start the server

```bash
cd e-commerce/phase-01-http-from-scratch
javac HttpServer.java
java HttpServer
```

| Keyword | Meaning |
|---|---|
| `cd` | Change directory — move shell into the given folder. |
| `e-commerce/phase-01-http-from-scratch` | Relative path (from wherever your shell is now). |
| `javac` | Java compiler — turns `.java` source into `.class` bytecode. |
| `HttpServer.java` | Source file to compile. Filename must match the public class. |
| `java` | JVM launcher — runs a compiled class. |
| `HttpServer` | Class name to run. No `.java` / `.class` suffix. JVM calls its `main()`. |

You should see:

```
listening on 8080
```

...and it does not exit. Good.

### Terminal 2 — send a request

```bash
curl -v http://localhost:8080/hello
```

| Keyword | Meaning |
|---|---|
| `curl` | Command-line HTTP client — sends request, prints response. |
| `-v` | Verbose — also print sent (`>`) and received (`<`) headers. |
| `http://` | Scheme — plain HTTP (no encryption). `https://` adds TLS. |
| `localhost` | Host — this machine. Resolves to `127.0.0.1` (loopback). |
| `:8080` | Port — which listener on the host. Must match your `ServerSocket`. |
| `/hello` | Path — sent as `GET /hello HTTP/1.1`. Server decides what it means. |

Curl flags you'll meet in later steps: `-X POST` (change method), `-H 'K: v'` (add header), `-d '{...}'` (send body), `-s` (silent), `-i` (include response headers).

`curl` will **hang** after printing its request. That's expected — you sent
`-v` so you already see the request went out, and the server never wrote a
response. Press `Ctrl+C` in terminal 2 to give up.

---

## What you should see

Terminal 1 (server output):

```
---- client connected from /127.0.0.1:53142 ----
GET /hello HTTP/1.1
Host: localhost:8080
User-Agent: curl/8.4.0
Accept: */*

---- end of headers, not sending response ----
```

Things worth noticing before you move on:

- **You never typed `Host:` or `User-Agent:`** — `curl` added them because
  HTTP requires `Host` and clients typically identify themselves. Every
  browser does the same.
- **`GET /hello HTTP/1.1`** is one line, split by spaces: method, path,
  protocol version. This is what Spring's `@GetMapping("/hello")` matches
  against later.
- **The port number after `127.0.0.1:`** (`53142` above) is the *client's*
  ephemeral port, picked by the OS for `curl`. Every new `curl` gets a
  different one. Yours is at 8080.
- **`curl` still hangs.** Press `Ctrl+C` in terminal 2 to give up. In
  terminal 1 the server keeps running, ready for the next connection.

Try `curl` again 3-4 times with different paths (`/`, `/foo`, `/x?y=1`) and
watch each request print.

---

## Your turn

Small modifications. Do them in order. Each teaches one thing.

### 1. Change the port

Change `int port = 8080;` to `int port = 9000;`. Recompile, run, curl the new
port. Works.

Now change it back to 8080 but leave the *first* server still running from a
previous window. Try to start a second one. Read the exception carefully.

**What did you learn?** Only one process can bind a port at a time. This is
why `Address already in use` is one of the most common backend errors ever.

### 2. Print the client's address on connect

The example already does this (`getRemoteSocketAddress()`). Modify it to also
print **when** the connection arrived using `java.time.Instant.now()`. Two
concurrent `curl` calls will now show interleaved output — or will they?
Answer that yourself after Step 1.6.

### 3. Print a "disconnected" line

After the inner `while` loop breaks, print `---- client " + address + "
disconnected ----`. Hit `Ctrl+C` on a hung `curl`, watch the server. Does
your line print? Why not?

**Why not:** `curl` was hanging waiting for *your* response. Killing curl
closes the client's side of the TCP connection. On the server side,
`readLine()` will return `null` on the next read — but you're already past
the header loop and doing nothing. To see the disconnect you'd need to
actively read again. Comes back in Step 1.3.

---

## Acceptance checks

Tick these off:

- [x] Program starts, prints `listening on 8080`, does not exit.
- [x] `curl -v http://localhost:8080/hello` causes the raw request text to
      print to the server console.
- [x] Printed text starts with `GET /hello HTTP/1.1`.
- [x] Printed text includes `Host:` and `User-Agent:` headers you never wrote.
- [x] `curl` hangs waiting. You understand *why* — no response was sent.
- [x] You can stop and restart the server without changing the code.

---

## Questions worth asking

Ask me any of these — the whole point of the plan is these get answered as
they come up, not as a quiz:

- Why does `accept()` block? Isn't blocking bad?
- What's the difference between a port being **closed**, **filtered**, and
  **listening**? (Useful for real debugging.)
- Why does `curl` hang instead of erroring immediately?
- What's `127.0.0.1` vs `localhost` vs `0.0.0.0`? When does the difference bite?
- If I close the server socket, does `accept()` throw? What does the OS do
  with the port?
- What actually happens between `curl` pressing enter and my `accept()`
  returning — packet by packet?

---

## Files touched

- `phase-01-http-from-scratch/HttpServer.java` — the runnable example above

## Next step

**[Step 1.2 — Parse the request line](step-1.2-parse-request-line.md)**
(created when you're ready)
