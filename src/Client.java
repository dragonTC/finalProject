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

			serverFileListElement.addElement(new String(buf, 0, len));
		}
	}

	class connectListener
		implements ActionListener
	{
		public void actionPerformed(ActionEvent ev){
			if(connectionStatus == false){
				printMsg("connecting server...");
				serverIP = ipTextField.getText();
				serverPort = Integer.parseInt(portTextField.getText());

				connectB.setEnabled(false);
				ipTextField.setEnabled(false);
				portTextField.setEnabled(false);

				EnhancedProfileManager profile = EZCardLoader.loadEnhancedProfile(new File("pclient.card"), "passwd");

				try{
					local = new EnhancedAuthSocketClient(profile);
					local.connect(serverIP, serverPort);
	
					local.doEnhancedKeyDistribution();
					byte[] tmp = local.getSessionKey().getKeyValue();
					key = CipherUtil.copy(tmp, 0, CipherUtil.KEY_LENGTH);
					iv = CipherUtil.copy(tmp, CipherUtil.KEY_LENGTH, CipherUtil.BLOCK_LENGTH);
	
					local.doRapidAuthentication();
					EZCardLoader.saveEnhancedProfile(profile, new File("pclient.card"), "passwd");

					sin = new DataInputStream(local.getInputStream());
					sout = new DataOutputStream(local.getOutputStream());
	
				}catch(Exception e){
					printMsg("failed to connect server, please try again");
					EZCardLoader.saveEnhancedProfile(profile, new File("pclient.card"), "passwd");
					connectB.setEnabled(true);
					ipTextField.setEnabled(true);
					portTextField.setEnabled(true);
					return ;
				}
	
				printMsg("connection success!");
				connectB.setText("disconnect");
				connectB.setEnabled(true);
				connectionStatus = true;
				uploadB.setEnabled(true);
				downloadB.setEnabled(true);
				serverFileList.setEnabled(true);

				try{
					getServerFileList();
				}catch (IOException e) {
					e.printStackTrace();
				}
				
				
			}
			else{
				printMsg("disconnecting...");
				connectB.setEnabled(false);
				try{
					sout.writeInt(0);
					sout.flush();
					sout.close(); sin.close();
				}catch (Exception e) {
					e.printStackTrace();
				}

				serverIP = ""; serverPort = -1;
				connectionStatus = false;
				key = iv = null;

				connectB.setText("connect");
				connectB.setEnabled(true);
				ipTextField.setEnabled(true);
				portTextField.setEnabled(true);
				uploadB.setEnabled(false);
				downloadB.setEnabled(false);
				serverFileList.setEnabled(false);
				serverFileListElement.clear();
				serverFileListElement.addElement("disconnected");

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
		//
		uploadB.setEnabled(false);
		cons.gridx = 5;		cons.gridy = 3;
		cons.gridwidth = 1;	cons.gridheight = 1;
		cons.weightx = 0;	cons.weighty = 1.0;
		cons.fill = GridBagConstraints.BOTH;
		cons.anchor = GridBagConstraints.CENTER;
		frame.add(uploadB, cons);

		downloadB = new JButton("Download");
		//
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
	
	private JButton connectB, browseB, uploadB, downloadB, renameB, deleteB;
	private JFrame frame;
	private JLabel info;
	private JTextField ipTextField, portTextField, selectedFileTextField;
	DefaultListModel<String> serverFileListElement;
	private JList<String> serverFileList;

	private ServerSocket localServe;
	private String serverIP;
	private int serverPort;
	private boolean connectionStatus;
	private byte[] key, iv;
	private DataInputStream sin;
	private DataOutputStream sout;

	EnhancedAuthSocketClient local;
}