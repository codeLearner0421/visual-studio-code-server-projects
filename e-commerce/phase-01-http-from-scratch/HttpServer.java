import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Set;

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
            System.out.println("---- client connected at " + java.time.Instant.now() + " ----");

            // 2b. Wrap the raw byte stream so we can read it as lines of text.
            //     HTTP is UTF-8-safe ASCII in the request line and headers.
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(clientSocket.getInputStream()));

            // 2c. Read line by line. HTTP requests end their header block with
            //     a blank line ("" — an empty string, not null). If we didn't
            //     stop at the blank line we'd hang waiting for a body that may
            //     never come, because the client isn't going to close the
            //     connection until we respond.
            String requestLine = reader.readLine();
            if (requestLine == null || requestLine.isEmpty())
            {
                System.out.println("---- empty or dropped connection, skipping ----");
                continue;
            }

            String[] parts = requestLine.split(" ");
            if (parts.length != 3)
            {
                System.out.println("---- malformed request line: " + requestLine + " ----");
                continue;
            }

            String method = parts[0];
            String path = parts[1];
            String version = parts[2];
            String query = "";
            Set <String> allowed = Set.of("GET", "POST", "PUT", "DELETE");

            int index = path.indexOf('?');
            if (index != -1)
            {
                query = path.substring(index + 1);
                path = path.substring(0, index);
            }

            if (!allowed.contains(method))
            {
                System.out.println("unsupported method: " + method);
                continue;
            }

            System.out.println("method: " + method);
            System.out.println("path: " + path);
            System.out.println("version: " + version);
            if (!query.isEmpty())
            {
                System.out.println("query: " + query);
            }

            String headerLine;
            while ((headerLine = reader.readLine()) != null && !headerLine.isEmpty())
            {
                System.out.println(headerLine);
                if (headerLine.isEmpty())
                {
                    break;
                }
            }

            System.out.println("---- client " + clientSocket.getRemoteSocketAddress() + " disconnected----");
            System.out.println("---- end of headers, not sending response ----");

            // NOTE: We deliberately do NOT close clientSocket here. That would
            // send a TCP FIN and curl would exit with "empty reply from server".
            // We want curl to hang so you can observe the "server never
            // responded" state. In Step 1.4 we'll write a real response.
        }
    }
}
