import java.io.*;
import java.net.*;
import java.util.Scanner;

import ezprivacy.toolkit.*;
import ezprivacy.service.authsocket.*;
import ezprivacy.secret.*;
import ezprivacy.netty.session.ProtocolException;

public class Server
	implements Runnable
{
	public static void main(String[] args) {
		Server svr = new Server();
		svr.run();
	}

	public void run(){
		scan = new Scanner(System.in);
		System.out.print("port number: ");
		port = scan.nextInt();

		while(true){	//Server main loop
			EnhancedProfileManager profile = EZCardLoader.loadEnhancedProfile(new File("pserver.card"), "passwd");
			EnhancedAuthSocketServerAcceptor serverAcceptor = new EnhancedAuthSocketServerAcceptor(profile);
			AuthSocketServer server;

			try{
				serverAcceptor.bind(port);
				server = serverAcceptor.accept();

				printLog("client attempted to connect: " + server.getRemoteAddress().toString());

				server.waitUntilAuthenticated();

				EZCardLoader.saveEnhancedProfile(profile, new File("pserver.card"), "passwd");

				byte[] tmp = server.getSessionKey().getKeyValue();
				byte[] key = CipherUtil.copy(tmp, 0, CipherUtil.KEY_LENGTH);
				byte[] iv = CipherUtil.copy(tmp, CipherUtil.KEY_LENGTH, CipherUtil.BLOCK_LENGTH);
	
				DataInputStream sin = new DataInputStream(server.getInputStream());
				DataOutputStream sout = new DataOutputStream(server.getOutputStream());
	
				printLog("connect success!");
	
				/***server service***/
				while(true){
					while(true);/*
					int mode = sin.readInt();
	
					switch(mode){
						case 0:	//disconnect
						case 1:	//ls
						case 2:	//upload
						case 3:	//download
						case 4:	//rename
						case 5:	//remove
					}*/
				}
				/***end server service***/
	
				//sin.close(); sout.close();
			}catch(Exception e){
				e.printStackTrace();
			}

		}
	}

	void printLog(String str){
		try{
			FileOutputStream fout = new FileOutputStream(new File("serverLog.txt"), true);
			fout.write((str + '\n').getBytes());
			fout.close();
			System.out.println(str);
		}catch(IOException e){
			e.printStackTrace();
		}
	}

	private Scanner scan;

	private int port;
}