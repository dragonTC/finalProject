import java.io.*;
import java.net.*;
import java.util.Scanner;

import ezprivacy.toolkit.*;
import ezprivacy.service.authsocket.*;
import ezprivacy.secret.*;
import ezprivacy.netty.session.ProtocolException;
import ezprivacy.secret.Signature;
import ezprivacy.service.signature.SignatureClient;

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
			EnhancedProfileManager profile = EZCardLoader.loadEnhancedProfile(new File("pserver.card"), "passwd");
			EnhancedAuthSocketServerAcceptor serverAcceptor = new EnhancedAuthSocketServerAcceptor(profile);
			try{
				serverAcceptor.bind(port);
			}
			catch (Exception e) {
				e.printStackTrace();
				printLog(e.toString());
				break;
			}

			try{
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
				printLog(e.toString());
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
				oin = new ObjectInputStream(sin);
				oout = new ObjectOutputStream(sout);
				oout.flush();
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
						case 4:
							rename();
							break;
						case 5:
							remove();
					}
				}

				printLog("end server service");
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
		throws Exception
	{
		printLog("sending file lists...");
		File serverFileRoot = new File("server file");
		if(!serverFileRoot.exists()){
			sout.writeInt(0);
			return ;
		}
		File[] fileList = serverFileRoot.listFiles();

		sout.writeInt(fileList.length);
		sout.flush();
		for(int i=0 ; i<fileList.length ; ++i){
			printLog("send list: " + fileList[i].getName());
			byte[] tmp = fileList[i].getName().getBytes();
			tmp = CipherUtil.authEncrypt(key, iv, tmp);
			sout.writeInt(tmp.length);
			sout.flush();
			sout.write(tmp, 0, tmp.length);
			sout.flush();
		}
		printLog("done!");
	}

	private void sendFile()
		throws IOException
	{
		printLog("sending file...");

		try{
			//get filename
			int len = sin.readInt();
			byte[] buf = new byte[len];
			sin.readFully(buf, 0, len);
			buf = CipherUtil.authDecrypt(key, iv, buf);
			String fileName = new String(buf);

			//check file
			File target = new File("server file\\" + fileName);
			if(!target.exists()){
				sout.writeInt(-1);
				sout.flush();
				printLog("client attmpt to receive non-exist file : " + fileName);
				return ;
			}

			//send file
			FileInputStream fin = new FileInputStream(target);
			byte[] dat = new byte[16];
			while((len = fin.read(dat, 0, 16)) != -1){
				buf = new byte[len];
				System.arraycopy(dat, 0, buf, 0, len);
				buf = CipherUtil.authEncrypt(key, iv, buf);
				sout.writeInt(buf.length);
				sout.write(buf, 0, buf.length);
			}
			fin.close();
			sout.writeInt(0);

			printLog("success!");
		}catch (Exception e) {
			e.printStackTrace();
			printLog("failed to send file");
		}
	}

	private void receiveFile()
	{
		try{
			//get file
			int len = sin.readInt();
			byte[] buf = new byte[len];
			sin.readFully(buf, 0, len);
			buf = CipherUtil.authDecrypt(key, iv, buf);
			String fileName = new String(buf);
			printLog("receive file " + fileName);

			File serverFileRoot = new File("server file");
			if(!serverFileRoot.exists())	serverFileRoot.mkdir();
			FileOutputStream fout = new FileOutputStream(new File(serverFileRoot, fileName));
	
			while((len = sin.readInt()) != 0){
				buf = new byte[len];
				sin.readFully(buf, 0, len);
				buf = CipherUtil.authDecrypt(key, iv, buf);
	
				fout.write(buf, 0, buf.length);
			}
	
			fout.close();

			//send signature
			printLog("send signature...");
			len = sin.readInt();
			buf = new byte[len];
			sin.readFully(buf, 0, len);
			buf = CipherUtil.authDecrypt(key, iv, buf);

			EnhancedProfileManager sender = EZCardLoader.loadEnhancedProfile(new File("pserver.card"), "passwd");
			Signature sig = new SignatureClient.SignatureCreater()
				.initSignerID(sender.getPrimitiveProfile().getIdentifier())
				.initReceiverID(buf)
				.initMessage(fileName.getBytes())
				.initSignatureKey(sender.getPrimitiveProfile().getSignatureKey())
				.initTimestamp(System.nanoTime()).createSignature();

			oout.writeObject(sig);
			oout.flush();

			printLog("done!");
		}catch (Exception e) {
			e.printStackTrace();
			printLog("failed when receiving file");
		}
	}

	private void rename(){
		//not implemented
	}

	private void remove(){
		try{
			//get filename
			printLog("deleting...");
			int len = sin.readInt();
			byte[] buf = new byte[len];
			sin.readFully(buf, 0, len);
	
			buf = CipherUtil.authDecrypt(key, iv, buf);
			String fileName = new String(buf);
	
			File target = new File("server file\\" + fileName);
			if(target.exists()){	//delete file
				sout.writeInt(1);

				target.delete();
				printLog("file deleted");
	
				len = sin.readInt();
				buf = new byte[len];
				sin.readFully(buf, 0, len);
				buf = CipherUtil.authDecrypt(key, iv, buf);

				//send signature
				EnhancedProfileManager sender = EZCardLoader.loadEnhancedProfile(new File("pserver.card"), "passwd");
				Signature sig = new SignatureClient.SignatureCreater()
					.initSignerID(sender.getPrimitiveProfile().getIdentifier())
					.initReceiverID(buf)
					.initMessage(fileName.getBytes())
					.initSignatureKey(sender.getPrimitiveProfile().getSignatureKey())
					.initTimestamp(System.nanoTime()).createSignature();
	
				oout.writeObject(sig);
				oout.flush();

				printLog("signature sended");
				printLog("done!");
			}else{	//file not found
				sout.writeInt(0);
				printLog("client attempted to delete non-exist file: " + fileName);
				return ;
			}
		}catch (Exception e) {
			e.printStackTrace();
			printLog(e.toString());
		}
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

	private ServerSocket ss;
	private Socket server;
	private byte[] key, iv;

	private DataInputStream sin;
	private DataOutputStream sout;
	private ObjectInputStream oin;
	private ObjectOutputStream oout;

	private Scanner scan;
	private int port;
}