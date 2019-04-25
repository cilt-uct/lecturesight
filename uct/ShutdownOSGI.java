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
	int port = Integer.parseInt(args[1]);
	int timeout = Integer.parseInt(args[2]);

	long start_time = System.currentTimeMillis();
	long end_time = start_time + timeout * 1000;

        System.out.println("Shutting down OSGI on " + host + ":" + port + " (timeout " + timeout + "s)");

	String command="felix:stop 0\n";

	try {
	   try (Socket socket=new Socket(host, port)) {

		// Timeout in case we get nothing back
		socket.setSoTimeout(timeout * 1000);

		OutputStream out = socket.getOutputStream();
		InputStream in = socket.getInputStream();
		out.write(command.getBytes(Charset.defaultCharset()));

		// Read output, but don't keep reading for longer than timeout
		BufferedReader reader = new BufferedReader(new InputStreamReader(in, Charset.defaultCharset()));
		String line = reader.readLine();
		while ((line != null) && (System.currentTimeMillis() < end_time)) {
			System.out.println(line);
			line = reader.readLine();
		}
	   }
	} catch (Exception e) {
		System.out.println("Exception: " + e.getMessage());
	}
    }
}

