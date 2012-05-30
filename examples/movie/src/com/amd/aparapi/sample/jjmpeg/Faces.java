package com.amd.aparapi.sample.jjmpeg;

import java.awt.BorderLayout;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;

import au.notzed.jjmpeg.io.JJMediaReader;
import au.notzed.jjmpeg.io.JJMediaReader.JJReaderVideo;
import detection.AparapiDetector2;
import detection.Detector;
import detection.HaarCascade;

/**
 * Code based on Demo of JJVideoScanner class
 *
 * @author notzed
 */
public class Faces{

   public static void main(final String[] args) {
      SwingUtilities.invokeLater(new Runnable(){
         public void run() {
            JFrame frame = new JFrame("Video Frames");
            final JLabel label = new JLabel();
            frame.getContentPane().setLayout(new BorderLayout());
            frame.getContentPane().add(label, BorderLayout.CENTER);
            try {
               String name = "C:\\Users\\gfrost\\Downloads\\Lumber jack song.mp4";
               name = "C:\\Users\\gfrost\\Downloads\\Pink Floyd - Arnold Layne.mp4";
               //   name = "C:\\Users\\gfrost\\Downloads\\Faces in the Crowd.mp4";
               //   name = "C:\\Users\\gfrost\\Downloads\\Godley and Creme - Cry.mp4";
               //   name = "C:\\Users\\gfrost\\Downloads\\The Matrix Red Dress.mp4";
               final JJMediaReader reader = new JJMediaReader(name);
               final JJReaderVideo vs = reader.openFirstVideoStream();
               final BufferedImage image = vs.createImage();
               label.setIcon(new ImageIcon(image));

               HaarCascade haarCascade = HaarCascade.create("..\\jviolajones\\haarcascade_frontalface_alt2.xml");
               final Detector detector = new AparapiDetector2(haarCascade, 1f, 2f, 0.1f, false);
               //    final Detector detector = new MultiThreadedDetector(haarCascade, 1f, 2f, 0.1f, false);
               frame.pack();
               frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
               frame.setVisible(true);
               new Thread(new Runnable(){
                  public void run() {
                     try {
                        int total = 0;
                        int count = 0;
                        while (true) {
                           JJMediaReader.JJReaderStream rs = reader.readFrame();
                           if (rs != null) {
                              vs.getOutputFrame(image);
                              long start = System.currentTimeMillis();
                              List<Rectangle> rects = detector.getFeatures(image);
                              Graphics2D gc = image.createGraphics();
                              for (Rectangle rect : rects) {
                                 gc.draw(rect);
                              }

                              total += (System.currentTimeMillis() - start);
                              count++;
                              gc.drawString("" + (total / count), 20, 20);

                              // System.out.println("elapsed  =" + (System.currentTimeMillis() - start));

                              //System.out.println(kernel.getExecutionTime());
                              label.repaint();
                           } else {
                              System.out.println("end of file, restart");
                              reader.dispose();
                              System.exit(1);
                           }
                           Thread.sleep(1);
                        }
                     } catch (Exception ex) {
                        ex.printStackTrace();
                        Logger.getLogger(Faces.class.getName()).log(Level.SEVERE, null, ex);
                     }
                  }
               }).start();
            } catch (Exception ex) {
               Logger.getLogger(Faces.class.getName()).log(Level.SEVERE, null, ex);
            }
         }
      });
   }
}
