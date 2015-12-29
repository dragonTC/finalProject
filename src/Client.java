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

	private void getServerFileList()
		throws IOException
	{
		serverFileListElement.clear();

		sout.writeInt(1);
		sout.flush();
		byte[] buf = new byte[2048];

		int cnt = sin.readInt();
		printMsg("total file: " + cnt);
		for(int i=0 ; i<cnt ; ++i){
			int len = sin.readInt();
			sin.read(buf, 0, len);

			String stmp = new String(buf, 0, len);
			printMsg("get list: " + stmp);
			serverFileListElement.addElement(stmp);
		}
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
					EnhancedAuthSocketClient local = new EnhancedAuthSocketClient(profile);
					local.connect(serverIP, port);
	
					local.doEnhancedKeyDistribution();
					byte[] tmp = local.getSessionKey().getKeyValue();
					key = CipherUtil.copy(tmp, 0, CipherUtil.KEY_LENGTH);
					iv = CipherUtil.copy(tmp, CipherUtil.KEY_LENGTH, CipherUtil.BLOCK_LENGTH);
	
					local.doRapidAuthentication();
					EZCardLoader.saveEnhancedProfile(profile, new File("pclient.card"), "passwd");

					local.close();
				}catch(Exception e){
					printMsg("failed to authenticate server, please try again");
					EZCardLoader.saveEnhancedProfile(profile, new File("pclient.card"), "passwd");
					changeUIStatus(false);
					return ;
				}

				printMsg("authenticate success!");
				/***end hand shake***/

				try{
					Thread.sleep(100);
					client = new Socket(serverIP, port);
					sin = new DataInputStream(client.getInputStream());
					sout = new DataOutputStream(client.getOutputStream());
					printMsg("connect success!");
					changeUIStatus(true);
				}catch (Exception e) {
					printMsg("failed to build connection, please try again");
					e.printStackTrace();
					changeUIStatus(false);
					return ;
				}
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

		}
	}

	class downloadListener
		implements ActionListener 
	{
		public void actionPerformed(ActionEvent ev){
			printMsg("downloading...");

			try{
				String fileName = serverFileList.getSelectedValue();
				if(fileName == null){
					printMsg("hadn't select a file");
					return ;
				}

				File dest = new File("client file");
				if(!dest.exists() || !dest.isDirectory())	dest.mkdir();

				File target = new File(dest.getPath(), fileName);
				FileOutputStream fout = new FileOutputStream(target);
				byte[] buf = fileName.getBytes();
				int len;
			
				sout.writeInt(3);

				sout.writeInt(buf.length);
				sout.write(buf, 0, buf.length);

				while((len = sin.readInt()) != 0){
					sin.readFully(buf, 0, len);
					fout.write(buf, 0, len);
				}
				fout.close();
			}catch (IOException e) {
				e.printStackTrace();
				printMsg("download failed");
				return ;
			}

			try{
				getServerFileList();
			}catch (Exception e) {
				e.printStackTrace();
				printMsg("failed to receive file list");
			}

			printMsg("download complete!");
		}
	}

	private void printMsg(String str){
		info.setText("[client] " + str);
		System.out.println("[client] " + str);
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
		//
		renameB.setEnabled(false);
		cons.gridx = 5;		cons.gridy = 5;
		cons.gridwidth = 1;	cons.gridheight = 1;
		cons.weightx = 0;	cons.weighty = 1.0;
		cons.fill = GridBagConstraints.BOTH;
		cons.anchor = GridBagConstraints.CENTER;
		frame.add(renameB, cons);

		deleteB = new JButton("Delete");
		//
		deleteB.setEnabled(false);
		cons.gridx = 5;		cons.gridy = 6;
		cons.gridwidth = 1;	cons.gridheight = 1;
		cons.weightx = 0;	cons.weighty = 1.0;
		cons.fill = GridBagConstraints.BOTH;
		cons.anchor = GridBagConstraints.CENTER;
		frame.add(deleteB, cons);

		/***end element setting**/

		frame.pack();
		frame.setVisible(true);
	}
	
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
			try{
				getServerFileList();
			}catch(Exception e){
				printMsg(e.getMessage());
				e.printStackTrace();
			}
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

	private String serverIP;
	private int port;
	private boolean connectionStatus;
	private byte[] key, iv;

}