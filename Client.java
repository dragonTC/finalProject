import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import java.io.*;
import java.net.*;

public class Client{
	public static void main(String[] args) {
		Client cln = new Client();
		cln.run();
	}

	public void run(){
		setupUI();
	}

	private void setupUI(){
		frame = new JFrame();
		frame.setSize(600, 400);
		frame.setLayout(new GridBagLayout());
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

		GridBagConstraints cons = new GridBagConstraints();
		cons.insets = new Insets(2, 2, 2, 2);

		JLabel ipL = new JLabel("server IP: ");
		cons.gridx = 0;		cons.gridy = 0;
        cons.gridwidth = 1;	cons.gridheight = 1;
        cons.weightx = 0;	cons.weighty = 0;
        cons.fill = GridBagConstraints.NONE;
        cons.anchor = GridBagConstraints.CENTER;
        frame.add(ipL, cons);

        ipT = new JTextField("", 15);
        ipT.setMinimumSize(ipT.getPreferredSize());
		cons.gridx = 1;		cons.gridy = 0;
        cons.gridwidth = 2;	cons.gridheight = 1;
        cons.weightx = 0;	cons.weighty = 0;
        cons.fill = GridBagConstraints.NONE;
        cons.anchor = GridBagConstraints.WEST;
        frame.add(ipT, cons);

        JLabel portL = new JLabel("port: ");
		cons.gridx = 3;		cons.gridy = 0;
        cons.gridwidth = 1;	cons.gridheight = 1;
        cons.weightx = 0;	cons.weighty = 0;
        cons.fill = GridBagConstraints.NONE;
        cons.anchor = GridBagConstraints.CENTER;
        frame.add(portL, cons);

        portT = new JTextField("", 6);
		cons.gridx = 4;		cons.gridy = 0;
        cons.gridwidth = 1;	cons.gridheight = 1;
        cons.weightx = 0;	cons.weighty = 0;
        cons.fill = GridBagConstraints.NONE;
        cons.anchor = GridBagConstraints.WEST;
        frame.add(portT, cons);

        JButton connectB = new JButton("connect");
        //connectB.addActionListener(new connectListener());
		cons.gridx = 5;		cons.gridy = 0;
        cons.gridwidth = 1;	cons.gridheight = 1;
        cons.weightx = 0;	cons.weighty = 0;
        cons.fill = GridBagConstraints.CENTER;
        cons.anchor = GridBagConstraints.CENTER;
        frame.add(connectB, cons);

        info = new JLabel("welcome!");
		cons.gridx = 0;		cons.gridy = 1;
        cons.gridwidth = 9;	cons.gridheight = 1;
        cons.weightx = 0;	cons.weighty = 0;
        cons.fill = GridBagConstraints.HORIZONTAL;
        cons.anchor = GridBagConstraints.WEST;
        frame.add(info, cons);

        ////

        JButton uploadB = new JButton("upload>");
        //
        cons.gridx = 2;		cons.gridy = 2;
        cons.gridwidth = 1;	cons.gridheight = 1;
        cons.weightx = 0;	cons.weighty = 0;
        cons.fill = GridBagConstraints.BOTH;
        cons.anchor = GridBagConstraints.CENTER;
        frame.add(uploadB, cons);

        frame.setVisible(true);
	}

	private JFrame frame;
	private JLabel info;
	private JTextField ipT, portT;
}