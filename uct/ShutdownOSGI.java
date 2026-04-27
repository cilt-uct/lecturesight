// Shut down an OSGI instance by using telnet to the Felix remote shell

import java.io.*;
import java.net.Socket;
import java.nio.charset.Charset;

public class ShutdownOSGI {

    public static void main(String[] args) {
	
	if (args.length < 3) {
		System.out.println("Syntax: java ShutdownOSGI <host> <port> <timeout-in-seconds>\n");
		System.exit(1);
	}

	String host = args[0];
	int port;
	int timeout;
	try {
		port = Integer.parseInt(args[1]);
		timeout = Integer.parseInt(args[2]);
	} catch (NumberFormatException e) {
		System.out.println("Error: <port> and <timeout-in-seconds> must be valid integers.");
		System.out.println("Syntax: java ShutdownOSGI <host> <port> <timeout-in-seconds>\n");
		System.exit(1);
		return;
	}

	long start_time = System.currentTimeMillis();
	long timeoutMillis = timeout * 1000L;
	long end_time = start_time + timeoutMillis;

        System.out.println("Shutting down OSGI on " + host + ":" + port + " (timeout " + timeout + "s)");

	String command="felix:stop 0\n";

	try {
	   try (Socket socket=new Socket(host, port)) {

		// Timeout in case we get nothing back
		socket.setSoTimeout((int) Math.min(timeoutMillis, Integer.MAX_VALUE));

		try (OutputStream out = socket.getOutputStream();
		     BufferedReader reader = new BufferedReader(
			     new InputStreamReader(socket.getInputStream(), Charset.defaultCharset()))) {
			out.write(command.getBytes(Charset.defaultCharset()));

			// Read output, but don't keep reading for longer than timeout
			String line = reader.readLine();
			while ((line != null) && (System.currentTimeMillis() < end_time)) {
				System.out.println(line);
				line = reader.readLine();
			}
		}
	   }
	} catch (Exception e) {
		System.out.println("Exception: " + e.toString());
	}
    }
}

