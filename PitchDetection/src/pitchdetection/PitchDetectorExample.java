/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package pitchdetection;

/**
 *
 * @author Chachi.Desuasido
 */
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.util.Random;

import javax.imageio.ImageIO;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.TargetDataLine;
import javax.sound.sampled.UnsupportedAudioFileException;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import be.tarsos.dsp.AudioDispatcher;
import be.tarsos.dsp.AudioEvent;
import be.tarsos.dsp.io.jvm.JVMAudioInputStream;
import be.tarsos.dsp.pitch.PitchDetectionHandler;
import be.tarsos.dsp.pitch.PitchDetectionResult;
import be.tarsos.dsp.pitch.PitchProcessor;
import be.tarsos.dsp.pitch.PitchProcessor.PitchEstimationAlgorithm;

import javax.sound.sampled.AudioSystem;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JTextField;

public class PitchDetectorExample extends JFrame implements PitchDetectionHandler {
	final int PIPE_WIDTH = 29;
	final int PIPE_HEIGHT = 500;
	final int PIPE_OPENING = 250;
	
	JTextField text = new JTextField();
	JLabel img = new JLabel();
	JLabel bg = new JLabel();
	JLabel pipeB = new JLabel();
	JLabel pipeT = new JLabel();

	/**
	 *
	 */

	private static final long serialVersionUID = 3501426880288136245L;

	private AudioDispatcher dispatcher;
	private Mixer currentMixer;

	final float timestep = 1.f / 60.f;

	final float gravityX = 0.f;
	final float gravityY = 9.8f;
	float posX = 0.f;
	float posY = 0.f;

	float velX = 0.f;
	float velY = 0.f;

	float pipeX = 0;
	float pipeY = 0;

	private PitchEstimationAlgorithm algo;

	public void jump() {
		velY = -2.f;
	}
	
	private void Physics_Update(double delta) {
		// Character Update
		velX += gravityX * delta;
		velY += gravityY * delta;
		
		posX += velX;
		posY += velY;
		
		if(posY >= 540.f) {
			velY = 0.f;
			posY = 540.f;
		}

		img.setLocation((int) posX, (int) posY);
		
		// Pipe Update
		pipeX -= 80.f * delta;
		
		if(pipeX < -PIPE_WIDTH) {
			pipeX = 338.f;
			pipeY = 182.f + (float) Math.random() * 200.f;
			System.out.println("Pipe Y: " + String.valueOf(-pipeY));
		}
		
		pipeT.setLocation((int) pipeX, (int) -pipeY);
		pipeB.setLocation((int) pipeX, (int) (600.f - pipeY));
	}
	
	public PitchDetectorExample() {
		super("pitch");
		super.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		super.setLayout(null);
		super.setLocationRelativeTo(null);

		ImageIcon backgroundIcon = new ImageIcon("res/background-simple.png");

		super.add(text);
		text.setSize(60, 24);
		text.setLocation(700, 500);

		super.add(pipeT);
		pipeT.setSize(29, 500);
		pipeT.setLocation(280, 0);
		pipeT.setIcon(new ImageIcon("res/tt.png"));

		super.add(pipeB);
		pipeB.setSize(29, 500);
		pipeB.setLocation(280, 320);
		pipeB.setIcon(new ImageIcon("res/bt.png"));

		super.add(img);
		img.setSize(50, 50);
		img.setLocation(0, 0);
		img.setIcon(new ImageIcon("res/android.png"));

		super.add(bg);
		bg.setIcon(new ImageIcon("res/bg1.png"));
		bg.setSize(338, 600);

		text.setHorizontalAlignment(JTextField.CENTER);

		algo = PitchEstimationAlgorithm.FFT_YIN;
		try {
			for (Mixer.Info info : Shared.getMixerInfo(false, true)) {
				if (info.getName().contains("Microphone")) {
					setNewMixer(AudioSystem.getMixer(info));
					break;
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		new Thread(new Runnable() {
			double last = System.nanoTime() / 1e9;
			
			@Override
			public void run() {
				while(true) {
					double now = System.nanoTime() / 1e9;
					double frameTime = now - last;
					last = now;
					
					while(frameTime > 0.0) {
						double deltaTime = frameTime < timestep ? frameTime : timestep;
						Physics_Update(deltaTime);
						frameTime -= deltaTime;
					}
					
					while(now - last < timestep) {
						Thread.yield();
						
						try {
							Thread.sleep(1);
						} catch(Exception e) {
							e.printStackTrace();
						}
						
						now = System.nanoTime() / 1e9;
					}
				}
			}
		}).start();
	}

	private void setNewMixer(Mixer mixer) throws LineUnavailableException, UnsupportedAudioFileException {

		if (dispatcher != null) {
			dispatcher.stop();
		}
		currentMixer = mixer;

		float sampleRate = 44100;
		int bufferSize = 1024;
		int overlap = 0;

		final AudioFormat format = new AudioFormat(sampleRate, 16, 1, true, true);
		final DataLine.Info dataLineInfo = new DataLine.Info(TargetDataLine.class, format);
		TargetDataLine line;
		line = (TargetDataLine) mixer.getLine(dataLineInfo);
		final int numberOfSamples = bufferSize;
		line.open(format, numberOfSamples);
		line.start();
		final AudioInputStream stream = new AudioInputStream(line);

		JVMAudioInputStream audioStream = new JVMAudioInputStream(stream);
		// create a new dispatcher
		dispatcher = new AudioDispatcher(audioStream, bufferSize, overlap);

		// add a processor
		dispatcher.addAudioProcessor(new PitchProcessor(algo, sampleRate, bufferSize, this));

		new Thread(dispatcher, "Audio dispatching").start();
	}

	public static void main(String... strings) throws InterruptedException {
		JFrame frame = new PitchDetectorExample();
		frame.setSize(338, 600);
		frame.setVisible(true);
	}

	@Override
	public void handlePitch(PitchDetectionResult pitchDetectionResult, AudioEvent audioEvent) {
		if (pitchDetectionResult.getPitch() == -1.f) return;
		
		double timeStamp = audioEvent.getTimeStamp();
		float pitch = pitchDetectionResult.getPitch();
		float probability = pitchDetectionResult.getProbability();
		double rms = audioEvent.getRMS() * 100;
		System.out.println(String.valueOf(pitch));
		text.setText(String.valueOf(pitchDetectionResult.getPitch()));
		if (pitchDetectionResult.getPitch() > 100.f) {
			//img.setIcon(new ImageIcon("res/android1.png"));
			jump();
		}
	}
}
