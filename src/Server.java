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
			printLog("server started");

			/***do handshake***/
			try{
				EnhancedProfileManager profile = EZCardLoader.loadEnhancedProfile(new File("pserver.card"), "passwd");
				EnhancedAuthSocketServerAcceptor serverAcceptor = new EnhancedAuthSocketServerAcceptor(profile);
				serverAcceptor.bind(port);
				AuthSocketServer local = serverAcceptor.accept();
				printLog("waiting for connection...");

				printLog("client attempted to connect: " + local.getRemoteAddress().toString());
				local.waitUntilAuthenticated();

				EZCardLoader.saveEnhancedProfile(profile, new File("pserver.card"), "passwd");

				byte[] tmp = local.getSessionKey().getKeyValue();
				key = CipherUtil.copy(tmp, 0, CipherUtil.KEY_LENGTH);
				iv = CipherUtil.copy(tmp, CipherUtil.KEY_LENGTH, CipherUtil.BLOCK_LENGTH);

				local.close();
				serverAcceptor.close();
			}catch(Exception e){
				e.printStackTrace();
				printLog(e.getMessage());
				continue;
			}

			printLog("authenticate success!");
			/***end handshake***/

			/***do connection***/
			printLog("connecting...");

			try{
				ss = new ServerSocket(port);
				server = ss.accept();

				sin = new DataInputStream(server.getInputStream());
				sout = new DataOutputStream(server.getOutputStream());
			}catch (Exception e) {
				e.printStackTrace();
				printLog("failed to build connection");
				continue;
			}

			printLog("connect success!");
			/***end connection***/

			/***main server service***/
			try{
				while(true){

					int mode = sin.readInt();
					printLog("receive request: " + Integer.toString(mode));

					if(mode == 0){	//disconnect
						closeServer();
						printLog("connection closed");
						break;
					}
					switch(mode){
						case 1:
							sendServerFileList();
							break;
						case 2:
							receiveFile();
							break;
						case 3:
							sendFile();
							break;
						case 4:	//rename
						case 5:	//remove
					}
				}
	
			}catch(Exception e){
				closeServer();
				e.printStackTrace();
				printLog(e.getMessage());
			}
			/***end mainserver service***/
		}
	}

	private void closeServer(){
		try{
			sin.close(); sout.close();
			server.close();
			ss.close();
		}catch (Exception e) {
			e.printStackTrace();
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
			printLog("send list: " + fileList[i].getName());
			byte[] tmp = fileList[i].getName().getBytes();
			sout.writeInt(tmp.length);
			sout.flush();
			sout.write(tmp, 0, tmp.length);
			sout.flush();
		}
	}

	private void sendFile()
		throws IOException
	{
		printLog("sending file...");

		try{
			int len = sin.readInt();
			byte[] buf = new byte[2048];
			sin.readFully(buf, 0, len);
			String fileName = new String(buf, 0, len);
	
			File target = new File("server file\\" + fileName);
			if(!target.exists()){
				sout.writeInt(0);
				printLog("client attmpt to receive non-exist file : " + fileName);
				return ;
			}

			FileInputStream fin = new FileInputStream(target);
			while((len = fin.read(buf, 0, 48)) != -1){
				sout.writeInt(len);
				sout.write(buf, 0, len);
			}
			fin.close();
			sout.writeInt(0);

			printLog("success!");
		}catch (Exception e) {
			e.printStackTrace();
			printLog("failed to send file");
		}
	}

	private void receiveFile(){

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

	ServerSocket ss;
	Socket server;
	byte[] key, iv;

	DataInputStream sin;
	DataOutputStream sout;

	private Scanner scan;
	private int port;
}