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
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
                if (line.isEmpty()) {
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
