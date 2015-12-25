import java.io.*;
import java.net.*;

import ezprivacy.toolkit.*;
import ezprivacy.service.authsocket.*;
import ezprivacy.secret.*;
import ezprivacy.netty.session.ProtocolException;

public class Server
	implements Runnable
{
	public static void main(String[] args) {
		scan = new Scanner(System.in);

		Server svr = new Server();
		svr.run();
	}

	public void run(){
		System.out.print("port number: ");
		port = scan.nextInt();

		
	}

	private Scanner scan;

	private ServerSocket ss;
	private Socket sock;
	private String clientIP;
	private int port;
}