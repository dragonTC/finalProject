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
			profile = EZCardLoader.loadEnhancedProfile(new File("pserver.card"), "passwd");
			serverAcceptor = new EnhancedAuthSocketServerAcceptor(profile);
			printLog("server started, waiting for connection");

			try{
				serverAcceptor.bind(port);
				server = serverAcceptor.accept();
			}catch(Exception e){
				e.printStackTrace();
				printLog(e.getMessage());
				abandon();
				continue;
			}

			printLog("client attempted to connect: " + server.getRemoteAddress().toString());

			try{
				server.waitUntilAuthenticated();
			}catch (Exception e) {
				e.printStackTrace();
				printLog(e.getMessage());
				abandon();
				continue;
			}

			EZCardLoader.saveEnhancedProfile(profile, new File("pserver.card"), "passwd");
			byte[] tmp = server.getSessionKey().getKeyValue();
			byte[] key = CipherUtil.copy(tmp, 0, CipherUtil.KEY_LENGTH);
			byte[] iv = CipherUtil.copy(tmp, CipherUtil.KEY_LENGTH, CipherUtil.BLOCK_LENGTH);

			sin = new DataInputStream(server.getInputStream());
			sout = new DataOutputStream(server.getOutputStream());

			printLog("connect success!");

			try{
				/***server service***/
				while(true){

					int mode = sin.readInt();
					printLog("receive request: " + Integer.toString(mode));

					if(mode == 0){	//disconnect
						server.close();
						serverAcceptor.close();
						printLog("connection closed");
						break;
					}
					switch(mode){
						case 1:	sendServerFileList();
						case 2:	//upload
						case 3:	//download
						case 4:	//rename
						case 5:	//remove
					}
				}
				/***end server service***/
	
			}catch(Exception e){
				abandon();
				printLog(e.getMessage());
				e.printStackTrace();
			}
		}
	}

	private void sendServerFileList()
		throws IOException
	{
		printLog("sending file lists...");
		File serverFileRoot = new File("server file");
		File[] fileList = serverFileRoot.listFiles();

		sout.writeInt(fileList.length);
		for(int i=0 ; i<fileList.length ; ++i){
			byte[] tmp = fileList[i].getName().getBytes();
			sout.writeInt(tmp.length);
			sout.flush();
			sout.write(tmp, 0, tmp.length);
			sout.flush();
		}
	}

	private void abandon(){
		EZCardLoader.saveEnhancedProfile(profile, new File("pserver.card"), "passwd");
		profile = null;
		server = null;
		serverAcceptor = null;
		sin = null;
		sout = null;
	}

	void printLog(String str){
		try{
			FileOutputStream fout = new FileOutputStream(new File("serverLog.txt"), true);
			fout.write(str.getBytes());
			fout.write(System.getProperty("line.separator").getBytes());
			fout.close();
			System.out.println("[server] " + str);
		}catch(IOException e){
			e.printStackTrace();
		}
	}

	private EnhancedProfileManager profile;
	private EnhancedAuthSocketServerAcceptor serverAcceptor;
	AuthSocketServer server;

	DataInputStream sin;
	DataOutputStream sout;

	private Scanner scan;
	private int port;
}