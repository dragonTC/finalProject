import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import java.io.*;
import java.net.*;
import java.util.Scanner;

import ezprivacy.toolkit.*;
import ezprivacy.service.authsocket.*;
import ezprivacy.secret.*;
import ezprivacy.netty.session.ProtocolException;
import ezprivacy.secret.Signature;
import ezprivacy.service.signature.SignatureClient;

public class Client
	implements Runnable
{
	public static void main(String[] args) {
		Client cln = new Client();
		cln.run();
	}

	public void run(){
		setupUI();
	}

	private void getServerFileList(){
		try{
			serverFileListElement.clear();

			sout.writeInt(1);
			sout.flush();

			int cnt = sin.readInt();
			printLog("total file: " + cnt);
			for(int i=0 ; i<cnt ; ++i){
				int len = sin.readInt();

				byte[] buf = new byte[len];
				sin.readFully(buf, 0, len);

				buf = CipherUtil.authDecrypt(key, iv, buf);
				String stmp = new String(buf, 0, buf.length-5);

				printLog("get list: " + stmp);
				serverFileListElement.addElement(stmp);
			}
		}catch (Exception e) {
			printMsg("error: failed when receiving file list");
			printLog(e.toString());
			e.printStackTrace();
			return ;
		}

		printLog("file list received");
	}

	class connectListener
		implements ActionListener
	{
		public void actionPerformed(ActionEvent ev){
			if(connectionStatus == false){
				printMsg("connecting server...");
				connectB.setEnabled(false);
				serverIP = ipTextField.getText();
				port = Integer.parseInt(portTextField.getText());

				/***do hand shake***/
				EnhancedProfileManager profile = EZCardLoader.loadEnhancedProfile(new File("pclient.card"), "passwd");
				try{
					printLog("authenticating server...");
					EnhancedAuthSocketClient local = new EnhancedAuthSocketClient(profile);
					local.connect(serverIP, port);
	
					local.doEnhancedKeyDistribution();
					byte[] tmp = local.getSessionKey().getKeyValue();
					key = CipherUtil.copy(tmp, 0, CipherUtil.KEY_LENGTH);
					iv = CipherUtil.copy(tmp, CipherUtil.KEY_LENGTH, CipherUtil.BLOCK_LENGTH);
	
					local.doRapidAuthentication();
					EZCardLoader.saveEnhancedProfile(profile, new File("pclient.card"), "passwd");

					local.close();

					printMsg("authenticate success!");
				}catch(Exception e){
					printMsg("failed to authenticate server, please try again");
					EZCardLoader.saveEnhancedProfile(profile, new File("pclient.card"), "passwd");
					changeUIStatus(false);
					return ;
				}
				/***end hand shake***/

				/***build connection***/
				try{
					printLog("building connection with server...");

					Thread.sleep(100);
					client = new Socket(serverIP, port);

					sout = new DataOutputStream(client.getOutputStream());
					sin = new DataInputStream(client.getInputStream());
					oout = new ObjectOutputStream(sout);
					oout.flush();
					oin = new ObjectInputStream(sin);

					printMsg("connect success!");
					changeUIStatus(true);
				}catch (Exception e) {
					printMsg("failed to build connection, please try again");
					e.printStackTrace();
					changeUIStatus(false);
					return ;
				}
				/***end build connection***/

				getServerFileList();
			}
			else{
				printMsg("disconnecting...");
				connectB.setEnabled(false);
				try{
					sout.writeInt(0);
					sout.flush();
					sout.close(); sin.close();
					client.close();
				}catch (Exception e) {
					printMsg("error while disconnecting");
					e.printStackTrace();
				}

				serverIP = null;
				port = -1;
				key = iv = null;
				sout = null;
				sin = null;
				client = null;

				changeUIStatus(false);

				printMsg("disconnected");
			}
		}
	}

	//select local file
	class browseListner
		implements ActionListener 
	{
		public void actionPerformed(ActionEvent ev){
			JFileChooser chooser = new JFileChooser();
			int chooseStatus = chooser.showOpenDialog(frame);

			if(chooseStatus == JFileChooser.APPROVE_OPTION){
				File f = chooser.getSelectedFile();
				selectedFileTextField.setText(f.getPath());
			}
		}
	}

	class uploadListener 
		implements ActionListener
	{
		public void actionPerformed(ActionEvent ev){
			try{
				//send task
				File target = new File(selectedFileTextField.getText());
				if(!target.exists()){
					printMsg("error: selected file is not exist!");
					return ;
				}

				File sigFolder = new File("signature");
				if(!sigFolder.exists() || !sigFolder.isDirectory())	sigFolder.mkdir();
				FileOutputStream fout = new FileOutputStream(new File(sigFolder, target.getName()+".sig"));
				ObjectOutputStream foout = new ObjectOutputStream(fout);

				sout.writeInt(2);

				target = encryptFile(target);
	
				byte[] buf = target.getName().getBytes();
				buf = CipherUtil.authEncrypt(key, iv, buf);
				sout.writeInt(buf.length);
				sout.write(buf);
	
				FileInputStream fin = new FileInputStream(target);
				byte[] dat = new byte[16];
				int cnt;
				while((cnt = fin.read(dat, 0, 16)) != -1){
					buf = new byte[cnt];
					System.arraycopy(dat, 0, buf, 0, cnt);
					buf = CipherUtil.authEncrypt(key, iv, buf);
	
					sout.writeInt(buf.length);
					sout.write(buf, 0, buf.length);
				}
				sout.writeInt(0);

				printLog("file uploaded");
	
				fin.close();
				target.delete();

				//receive signature
				EnhancedProfileManager receiver = EZCardLoader.loadEnhancedProfile(new File("pclient.card"), "passwd");
				buf = receiver.getPrimitiveProfile().getIdentifier();
				buf = CipherUtil.authEncrypt(key, iv, buf);
				sout.writeInt(buf.length);
				sout.write(buf);

				Signature sig = (Signature)oin.readObject();
				if(SignatureClient.verifyWithoutArbiter(sig, receiver.getPrimitiveProfile()) == false){
					printMsg("signature is not correct, upload may failed");
				}else{
					printLog("signature received");
					printMsg("upload complete!");
				}
				foout.writeObject(sig);

				foout.close();

			}catch (Exception e) {
				e.printStackTrace();
				printMsg("error: upload failed");
			}

			getServerFileList();
		}
	}

	class downloadListener
		implements ActionListener 
	{
		public void actionPerformed(ActionEvent ev){
			printMsg("downloading...");

			try{
				//send task
				String fileName = serverFileList.getSelectedValue();
				if(fileName == null){
					printMsg("hadn't select a file");
					return ;
				}
				fileName = fileName + ".lock";

				File dest = new File("client file");
				if(!dest.exists() || !dest.isDirectory())	dest.mkdir();

				File target = new File(dest.getPath(), fileName);
				FileOutputStream fout = new FileOutputStream(target);
				byte[] buf = fileName.getBytes();
			
				sout.writeInt(3);

				buf = CipherUtil.authEncrypt(key, iv, buf);
				sout.writeInt(buf.length);
				sout.write(buf, 0, buf.length);

				int len;
				while((len = sin.readInt()) != 0){
					if(len == -1){
						printMsg("error: file isn't exist, download failed");
					}
					buf = new byte[len];
					sin.readFully(buf, 0, len);
					buf = CipherUtil.authDecrypt(key, iv, buf);
					fout.write(buf);
				}

				fout.close();

				decryptFile(target);
				target.delete();
			}catch (Exception e) {
				e.printStackTrace();
				printMsg("execption occured, download failed");
				return ;
			}

			getServerFileList();

			printMsg("download complete!");
		}
	}

	class renameListener
		implements ActionListener 
	{
		public void actionPerformed(ActionEvent ev){
			printMsg("rename function has not been implemented");
		}
	}

	class deleteListener
		implements ActionListener
	{
		public void actionPerformed(ActionEvent ev){
			try{
				//send task
				String fileName = serverFileList.getSelectedValue();
				if(fileName == null){
					printMsg("hadn't select a file");
					return ;
				}
				fileName = fileName + ".lock";

				sout.writeInt(5);

				byte[] buf = fileName.getBytes();
				buf = CipherUtil.authEncrypt(key, iv, buf);
				sout.writeInt(buf.length);
				sout.write(buf);

				int success = sin.readInt();

				if(success == 1){
					//receive signature
					File sigFolder = new File("signature");
					if(!sigFolder.exists() || !sigFolder.isDirectory())	sigFolder.mkdir();
					FileOutputStream fout = new FileOutputStream(new File(sigFolder, fileName+".del.sig"));
					ObjectOutputStream foout = new ObjectOutputStream(fout);
	
					EnhancedProfileManager receiver = EZCardLoader.loadEnhancedProfile(new File("pclient.card"), "passwd");
					buf = receiver.getPrimitiveProfile().getIdentifier();
					buf = CipherUtil.authEncrypt(key, iv, buf);
					sout.writeInt(buf.length);
					sout.write(buf);
	
					Signature sig = (Signature)oin.readObject();
					if(SignatureClient.verifyWithoutArbiter(sig, receiver.getPrimitiveProfile()) == false){
						printMsg("signature is not correct, operation may failed");
					}else{
						printLog("signature received");
						printMsg("delete complete!");
					}
					foout.writeObject(sig);

					foout.close();
				}else{
					printMsg("delete failed: file not exist");
				}

				getServerFileList();
			}catch (Exception e) {
				e.printStackTrace();
				printLog(e.toString());
			}
		}
	}

	//print message to GUI, console, and log file
	private void printMsg(String str){
		info.setText("[client] " + str);
		System.out.println("[client] " + str);
		try{
			FileOutputStream fout = new FileOutputStream(new File("clientLog.txt"), true);
			fout.write(str.getBytes());
			fout.write(System.getProperty("line.separator").getBytes());
			fout.close();
		}catch (IOException e) {
			e.printStackTrace();
		}
	}

	//print message to console and log file
	private void printLog(String str){
		System.out.println("[client] " +str);
		try{
			FileOutputStream fout = new FileOutputStream(new File("clientLog.txt"), true);
			fout.write(str.getBytes());
			fout.write(System.getProperty("line.separator").getBytes());
			fout.close();
		}catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void setupUI(){
		frame = new JFrame();
		frame.setLayout(new GridBagLayout());
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		
		GridBagConstraints cons = new GridBagConstraints();
		cons.insets = new Insets(4, 4, 4, 4);
		
		/*****row 1*****/
		JLabel ipL = new JLabel("server IP: ");
		cons.gridx = 0;		cons.gridy = 0;
		cons.gridwidth = 1;	cons.gridheight = 1;
		cons.weightx = 0;	cons.weighty = 0;
		cons.fill = GridBagConstraints.NONE;
		cons.anchor = GridBagConstraints.CENTER;
		frame.add(ipL, cons);
		
		ipTextField = new JTextField(20);
		cons.gridx = 1;		cons.gridy = 0;
		cons.gridwidth = 2;	cons.gridheight = 1;
		cons.weightx = 1.0;	cons.weighty = 0;
		cons.fill = GridBagConstraints.HORIZONTAL;
		cons.anchor = GridBagConstraints.WEST;
		frame.add(ipTextField, cons);
		
		JLabel portL = new JLabel("port: ");
		cons.gridx = 3;		cons.gridy = 0;
		cons.gridwidth = 1;	cons.gridheight = 1;
		cons.weightx = 0;	cons.weighty = 0;
		cons.fill = GridBagConstraints.NONE;
		cons.anchor = GridBagConstraints.EAST;
		frame.add(portL, cons);
		
		portTextField = new JTextField(6);
		cons.gridx = 4;		cons.gridy = 0;
		cons.gridwidth = 1;	cons.gridheight = 1;
		cons.weightx = 1.0;	cons.weighty = 0;
		cons.fill = GridBagConstraints.HORIZONTAL;
		cons.anchor = GridBagConstraints.WEST;
		frame.add(portTextField, cons);
		
		connectB = new JButton("Connect");
		connectionStatus = false;
		connectB.addActionListener(new connectListener());
		cons.gridx = 5;		cons.gridy = 0;
		cons.gridwidth = 1;	cons.gridheight = 1;
		cons.weightx = 0;	cons.weighty = 0;
		cons.fill = GridBagConstraints.BOTH;
		cons.anchor = GridBagConstraints.CENTER;
		frame.add(connectB, cons);
		

		/*****row 1*****/
		info = new JLabel("welcome!");
		cons.gridx = 0;		cons.gridy = 1;
		cons.gridwidth = 6;	cons.gridheight = 1;
		cons.weightx = 1.0;	cons.weighty = 0;
		cons.fill = GridBagConstraints.HORIZONTAL;
		cons.anchor = GridBagConstraints.WEST;
		frame.add(info, cons);

		/*****row 2*****/
		JLabel fileL = new JLabel("Local file:");
		cons.gridx = 0;		cons.gridy = 2;
		cons.gridwidth = 1;	cons.gridheight = 1;
		cons.weightx = 0;	cons.weighty = 0;
		cons.fill = GridBagConstraints.NONE;
		cons.anchor = GridBagConstraints.CENTER;
		frame.add(fileL, cons);

		selectedFileTextField = new JTextField(30);
		cons.gridx = 1;		cons.gridy = 2;
		cons.gridwidth = 4;	cons.gridheight = 1;
		cons.weightx = 1.0;	cons.weighty = 0;
		cons.fill = GridBagConstraints.HORIZONTAL;
		cons.anchor = GridBagConstraints.CENTER;
		frame.add(selectedFileTextField, cons);

		browseB = new JButton("Browse");
		browseB.addActionListener(new browseListner());
		cons.gridx = 5;		cons.gridy = 2;
		cons.gridwidth = 1;	cons.gridheight = 1;
		cons.weightx = 0;	cons.weighty = 0;
		cons.fill = GridBagConstraints.BOTH;
		cons.anchor = GridBagConstraints.CENTER;
		frame.add(browseB, cons);

		/*****row 3 and below*****/

		/*server file list*/
		serverFileListElement = new DefaultListModel<String>();
		serverFileListElement.addElement("disconnected");

		serverFileList = new JList<String>(serverFileListElement);
		serverFileList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		serverFileList.setEnabled(false);

		JScrollPane pane = new JScrollPane(serverFileList);
		cons.gridx = 0;		cons.gridy = 3;
		cons.gridwidth = 5;	cons.gridheight = 4;
		cons.weightx = 1.0;	cons.weighty = 1.0;
		cons.fill = GridBagConstraints.BOTH;
		cons.anchor = GridBagConstraints.CENTER;
		frame.add(pane, cons);
		/*end server file list*/

		
		uploadB = new JButton("Upload");
		uploadB.addActionListener(new uploadListener());
		uploadB.setEnabled(false);
		cons.gridx = 5;		cons.gridy = 3;
		cons.gridwidth = 1;	cons.gridheight = 1;
		cons.weightx = 0;	cons.weighty = 1.0;
		cons.fill = GridBagConstraints.BOTH;
		cons.anchor = GridBagConstraints.CENTER;
		frame.add(uploadB, cons);

		downloadB = new JButton("Download");
		downloadB.addActionListener(new downloadListener());
		downloadB.setEnabled(false);
		cons.gridx = 5;		cons.gridy = 4;
		cons.gridwidth = 1;	cons.gridheight = 1;
		cons.weightx = 0;	cons.weighty = 1.0;
		cons.fill = GridBagConstraints.BOTH;
		cons.anchor = GridBagConstraints.CENTER;
		frame.add(downloadB, cons);

		renameB = new JButton("Rename");
		renameB.addActionListener(new renameListener());
		renameB.setEnabled(false);
		cons.gridx = 5;		cons.gridy = 5;
		cons.gridwidth = 1;	cons.gridheight = 1;
		cons.weightx = 0;	cons.weighty = 1.0;
		cons.fill = GridBagConstraints.BOTH;
		cons.anchor = GridBagConstraints.CENTER;
		frame.add(renameB, cons);

		deleteB = new JButton("Delete");
		deleteB.addActionListener(new deleteListener());
		deleteB.setEnabled(false);
		cons.gridx = 5;		cons.gridy = 6;
		cons.gridwidth = 1;	cons.gridheight = 1;
		cons.weightx = 0;	cons.weighty = 1.0;
		cons.fill = GridBagConstraints.BOTH;
		cons.anchor = GridBagConstraints.CENTER;
		frame.add(deleteB, cons);

		/***end element setting**/

		/***initialize***/
		fkey = "0123456789ABCDEF".getBytes();
		fiv = "FEDCBA9876543210".getBytes();
		/***end initialize***/

		frame.pack();
		frame.setVisible(true);
	}
	
	//enable or disable buttons 
	private void changeUIStatus(boolean status){
		if(status){
			connectionStatus = true;

			ipTextField.setEnabled(false);
			portTextField.setEnabled(false);
			connectB.setEnabled(true);
			connectB.setText("disconnect");

			downloadB.setEnabled(true);
			uploadB.setEnabled(true);
			renameB.setEnabled(true);
			deleteB.setEnabled(true);

			serverFileList.setEnabled(true);
		}
		else{
			connectionStatus = false;

			ipTextField.setEnabled(true);
			portTextField.setEnabled(true);
			connectB.setEnabled(true);
			connectB.setText("connect");

			downloadB.setEnabled(false);
			uploadB.setEnabled(false);
			renameB.setEnabled(false);
			deleteB.setEnabled(false);

			serverFileList.setEnabled(false);
			serverFileListElement.clear();
			serverFileListElement.addElement("disconnected");
		}
	}

	//encrypt a file by authEncrypt()
	private File encryptFile(File src){
		File ftmp = null;
		try{
			FileInputStream fin = new FileInputStream(src);
			ftmp = new File(src.getPath() + ".lock");
			FileOutputStream fout = new FileOutputStream(ftmp);
	
			byte[] buf = new byte[16];
			int cnt;
			while((cnt = fin.read(buf, 0, 16)) != -1){
				byte[] tmp = new byte[cnt];
				System.arraycopy(buf, 0, tmp, 0, cnt);
				tmp = CipherUtil.authEncrypt(fkey, fiv, tmp);
				fout.write(tmp);
			}
			fout.close(); fin.close();

		}catch (Exception e) {
			e.printStackTrace();
			printLog(e.toString());
		}

		return ftmp;
	}

	//decrypt a file by authDecrypt()
	private void decryptFile(File src){
		try{
			FileInputStream fin = new FileInputStream(src);
			char[] ctmp = src.getPath().toCharArray();
			String stmp = new String(ctmp, 0, ctmp.length-5);
			File target = new File(stmp);
			FileOutputStream fout = new FileOutputStream(target);
	
			byte[] buf = new byte[48];
			int cnt;
			while((cnt = fin.read(buf, 0, 48)) != -1){
				byte[] tmp = new byte[cnt];
				System.arraycopy(buf, 0, tmp, 0, cnt);
				tmp = CipherUtil.authDecrypt(fkey, fiv, tmp);
				fout.write(tmp);
			}
	
			fin.close(); fout.close();

		}catch (Exception e) {
			e.printStackTrace();
			printLog(e.toString());
		}
	}

	private JButton connectB, browseB, uploadB, downloadB, renameB, deleteB;
	private JFrame frame;
	private JLabel info;
	private JTextField ipTextField, portTextField, selectedFileTextField;
	private DefaultListModel<String> serverFileListElement;
	private JList<String> serverFileList;

	private ServerSocket ss;
	private Socket client;

	private DataInputStream sin;
	private DataOutputStream sout;
	private ObjectInputStream oin;
	private ObjectOutputStream oout;

	private String serverIP;
	private int port;
	private boolean connectionStatus;
	private byte[] key, iv, fkey, fiv;

}