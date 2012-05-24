package detection;

/**
This project is based on the open source jviolajones project created by Simon
Houllier and is used with his permission. Simon's jviolajones project offers 
a pure Java implementation of the Viola-Jones algorithm.

http://en.wikipedia.org/wiki/Viola%E2%80%93Jones_object_detection_framework

The original Java source code for jviolajones can be found here
http://code.google.com/p/jviolajones/ and is subject to the
gnu lesser public license  http://www.gnu.org/licenses/lgpl.html

Many thanks to Simon for his excellent project and for permission to use it 
as the basis of an Aparapi example.
**/

import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.imageio.ImageIO;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.jdom.Element;
import org.jdom.input.SAXBuilder;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;
import org.xml.sax.SAXException;

public class DetectorNoJDOM{

   /** The list of classifiers that the test image should pass to be considered as an image.*/
   Stage[] stages;

   Point size;

   /**Factory method. Builds a detector from an XML file.
    * @param filename The XML file (generated by OpenCV) describing the Haar Cascade.
    * @return The corresponding detector.
    */
   public static DetectorNoJDOM create(String filename) {
      boolean useJdom = true;
      if (useJdom) {
         org.jdom.Document document = null;
         SAXBuilder sxb = new SAXBuilder();
         try {
            document = sxb.build(new File(filename));
         } catch (Exception e) {
            e.printStackTrace();
         }

         return new DetectorNoJDOM(document);
      } else {
         DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
         try {
            DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
            try {
               Document document = documentBuilder.parse(new File(filename));
               return (new DetectorNoJDOM(document));
            } catch (SAXException e) {
               // TODO Auto-generated catch block
               e.printStackTrace();
            } catch (IOException e) {
               // TODO Auto-generated catch block
               e.printStackTrace();
            }
         } catch (ParserConfigurationException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
         }
      }
      return (null);
   }

   /** Detector constructor.
    * Builds, from a XML document (i.e. the result of parsing an XML file, the corresponding Haar cascade.
    * @param document The XML document (parsing of file generated by OpenCV) describing the Haar cascade.
    * 
    * http://code.google.com/p/jjil/wiki/ImplementingHaarCascade
    */

   public static <T extends org.w3c.dom.Node> T getNode(org.w3c.dom.Node rootNode, Class<T> clazz, String xpathString) {

      if (rootNode != null && xpathString != null && !xpathString.equals("")) {
         XPath xpath = XPathFactory.newInstance().newXPath();
         try {
            org.w3c.dom.Node node = (org.w3c.dom.Node) xpath.evaluate(xpathString, rootNode, XPathConstants.NODE);
            if (node != null) {
               node.getParentNode().removeChild(node);
            }
            return ((T) node);

         } catch (XPathExpressionException xpathException) {
            System.out.println("Exception " + xpathException + " with xpath '" + xpathString + "'");
         }
      }

      return (null);
   }

   public static <T extends org.w3c.dom.Node> Collection<T> getNodes(org.w3c.dom.Node rootNode, Class<T> clazz, String xpathString) {
      List<T> nodes = new ArrayList<T>();
      if (rootNode != null && xpathString != null && !xpathString.equals("")) {
         XPath xpath = XPathFactory.newInstance().newXPath();
         try {

            //  StopWatch sw = new StopWatch();
            //  sw.start();
            NodeList nodeList = (NodeList) xpath.evaluate(xpathString, rootNode, XPathConstants.NODESET);
            for (int i = 0; i < nodeList.getLength(); i++) {
               org.w3c.dom.Node node = nodeList.item(i);
               nodes.add((T) node);
               node.getParentNode().removeChild(node);
            }
            //  sw.print("time "+xpathString);

         } catch (XPathExpressionException xpathException) {
            System.out.println("Exception " + xpathException + " with xpath '" + xpathString + "'");
         }
      }

      return (nodes);
   }

   public DetectorNoJDOM(org.w3c.dom.Document document) {
      List<Stage> stageList = new LinkedList<Stage>();

      org.w3c.dom.Element racine = getNode(document.getDocumentElement(), org.w3c.dom.Element.class, "/opencv_storage/*[1]"); // first element under opencv_storage
      //getChild( getChild( document.getDocumentElement(), org.w3c.dom.Element.class, 0), org.w3c.dom.Element.class, 0);

      String sizeStr = getNode(racine, org.w3c.dom.Text.class, "size/text()").getNodeValue();
      Scanner scanner = new Scanner(sizeStr);
      size = new Point(scanner.nextInt(), scanner.nextInt());

      for (org.w3c.dom.Element stage : getNodes(racine, org.w3c.dom.Element.class, "stages/_")) {
         float thres = Float.parseFloat(getNode(stage, org.w3c.dom.Text.class, "stage_threshold/text()").getNodeValue());
         Stage st = new Stage(thres);
         //  System.out.println("create stage "+thres);

         for (org.w3c.dom.Element tree : getNodes(stage, org.w3c.dom.Element.class, "trees/_")) {
            Tree t = new Tree(st);
            for (org.w3c.dom.Element feature : getNodes(tree, org.w3c.dom.Element.class, "_")) {
               float thres2 = Float.parseFloat(getNode(feature, org.w3c.dom.Text.class, "threshold/text()").getNodeValue());
               //  System.out.println(thres2);
               int left_node = -1;
               float left_val = 0;
               boolean has_left_val = false;

               Text leftValNode = getNode(feature, org.w3c.dom.Text.class, "left_val/text()");
               if (leftValNode != null) {
                  left_val = Float.parseFloat(leftValNode.getNodeValue());
                  has_left_val = true;
               } else {
                  left_node = Integer.parseInt(getNode(feature, org.w3c.dom.Text.class, "left_node/text()").getNodeValue());
                  has_left_val = false;
               }
               int right_node = -1;
               float right_val = 0;
               boolean has_right_val = false;
               Text rightValNode = getNode(feature, org.w3c.dom.Text.class, "right_val/text()");
               if (rightValNode != null) {
                  right_val = Float.parseFloat(rightValNode.getNodeValue());
                  has_right_val = true;
               } else {
                  right_node = Integer.parseInt(getNode(feature, org.w3c.dom.Text.class, "right_node/text()").getNodeValue());
                  has_right_val = false;
               }
               Feature f = new Feature(t, thres2, left_val, left_node, has_left_val, right_val, right_node, has_right_val, size);
               for (org.w3c.dom.Text txt : getNodes(feature, org.w3c.dom.Text.class, "feature/rects/_/text()")) {
                  String s = txt.getNodeValue().trim();
                  //System.out.println(s);
                  Rect r = Rect.fromString(s);
                  f.add(r);
               }
               t.addFeature(f);
            }
            st.addTree(t);
            // System.out.println("Number of nodes in tree " + t.features.size());
         }
         //  System.out.println("Number of trees : " + st.trees.size());
         stageList.add(st);
         //   System.out.println("Stages : " + stageList.size());
      }

      stages = stageList.toArray(new Stage[0]);

      //System.out.println(stages.length);

   }

   /** Detector constructor.
    * Builds, from a XML document (i.e. the result of parsing an XML file, the corresponding Haar cascade.
    * @param document The XML document (parsing of file generated by OpenCV) describing the Haar cascade.
    * 
    * http://code.google.com/p/jjil/wiki/ImplementingHaarCascade
    */
   public DetectorNoJDOM(org.jdom.Document document) {

      int rectCount = 0, featureCount = 0, nodeCount = 0, treeCount = 0, stageCount = 0;
      List<Stage> stageList = new LinkedList<Stage>();
      Element racine = (Element) document.getRootElement().getChildren().get(0);
      Scanner scanner = new Scanner(racine.getChild("size").getText());
      size = new Point(scanner.nextInt(), scanner.nextInt());
      Iterator it = racine.getChild("stages").getChildren("_").iterator();
      while (it.hasNext()) {
         Element stage = (Element) it.next();
         float thres = Float.parseFloat(stage.getChild("stage_threshold").getText());
         //System.out.println(thres);
         Iterator it2 = stage.getChild("trees").getChildren("_").iterator();
         Stage st = new Stage(thres);

         System.out.println("create stage " + thres);
         while (it2.hasNext()) {
            Element tree = ((Element) it2.next());
            Tree t = new Tree(st);
            Iterator it4 = tree.getChildren("_").iterator();
            while (it4.hasNext()) {
               Element feature = (Element) it4.next();
               float thres2 = Float.parseFloat(feature.getChild("threshold").getText());
               int left_node = -1;
               float left_val = 0;
               boolean has_left_val = false;
               int right_node = -1;
               float right_val = 0;
               boolean has_right_val = false;
               Element e;
               if ((e = feature.getChild("left_val")) != null) {
                  left_val = Float.parseFloat(e.getText());
                  has_left_val = true;
               } else {
                  left_node = Integer.parseInt(feature.getChild("left_node").getText());
                  has_left_val = false;
               }

               if ((e = feature.getChild("right_val")) != null) {
                  right_val = Float.parseFloat(e.getText());
                  has_right_val = true;
               } else {
                  right_node = Integer.parseInt(feature.getChild("right_node").getText());
                  has_right_val = false;
               }
               Feature f = new Feature(t, thres2, left_val, left_node, has_left_val, right_val, right_node, has_right_val, size);
               Iterator it3 = feature.getChild("feature").getChild("rects").getChildren("_").iterator();
               while (it3.hasNext()) {
                  String s = ((Element) it3.next()).getText().trim();
                  //System.out.println(s);
                  Rect r = Rect.fromString(s);
                  f.add(r);
                  rectCount++;
               }

               t.addFeature(f);
               featureCount++;
            }
            st.addTree(t);
            treeCount++;
            // System.out.println("Number of nodes in tree " + t.features.size());
         }
         // System.out.println("Number of trees : " + st.trees.size());
         stageList.add(st);
         stageCount++;
      }
      stages = stageList.toArray(new Stage[0]);
      Rect.flatten();
      Feature.flatten();
      Tree.flatten();
      Stage.flatten();

   }

   /** Returns the list of detected objects in an image applying the Viola-Jones algorithm.
    * 
    * The algorithm tests, from sliding windows on the image, of variable size, which regions should be considered as searched objects.
    * Please see Wikipedia for a description of the algorithm.
    * @param file The image file to scan.
    * @param baseScale The initial ratio between the window size and the Haar classifier size (default 2).
    * @param scale_inc The scale increment of the window size, at each step (default 1.25).
    * @param increment The shift of the window at each sub-step, in terms of percentage of the window size.
    * @return the list of rectangles containing searched objects, expressed in pixels.
    */
   public List<java.awt.Rectangle> getFaces(String file, float baseScale, float scale_inc, float increment, int min_neighbors,
         boolean doCannyPruning) {

      try {
         BufferedImage image = ImageIO.read(new File(file));

         return getFaces(image, baseScale, scale_inc, increment, min_neighbors, doCannyPruning);
      } catch (IOException e) {
         // TODO Auto-generated catch block
         e.printStackTrace();
      }

      return null;

   }

   public List<java.awt.Rectangle> getFaces(BufferedImage image, float baseScale, float scale_inc, float increment,
         int min_neighbors, boolean doCannyPruning) {

      final List<Rectangle> ret = new ArrayList<Rectangle>();
      final int width = image.getWidth();
      final int height = image.getHeight();
      final float maxScale = (Math.min((width + 0.f) / size.x, (height + 0.0f) / size.y));
      final int[] imagePixels = new int[width * height];
      final int[] grayImage = new int[width * height];
      final int[] img = new int[width * height];
      final int[] squares = new int[width * height];
      final StopWatch timer = new StopWatch();
      System.out.println(image);
      timer.start();
      for (int i = 0; i < width; i++) {
         for (int j = 0; j < height; j++) {
            imagePixels[i + j * width] = image.getRGB(i, j);
         }
      }
      timer.print("imagegrabber");

      timer.start();

      for (int i = 0; i < width; i++) {
         for (int j = 0; j < height; j++) {
            int c = imagePixels[i + j * width];
            int red = (c & 0x00ff0000) >> 16;
            int green = (c & 0x0000ff00) >> 8;
            int blue = c & 0x000000ff;
            int value = (30 * red + 59 * green + 11 * blue) / 100;
            img[i + j * width] = value;
         }
      }
      timer.print("greyscaler");
      timer.start();

      for (int i = 0; i < width; i++) {
         int col = 0;
         int col2 = 0;
         for (int j = 0; j < height; j++) {
            int value = img[i + j * width];
            grayImage[i + j * width] = (i > 0 ? grayImage[i - 1 + j * width] : 0) + col + value;
            squares[i + j * width] = (i > 0 ? squares[i - 1 + j * width] : 0) + col2 + value * value;
            col += value;
            col2 += value * value;
         }
      }

      timer.print("grey and squares");

      int[] canny = null;
      if (doCannyPruning) {
         timer.start();
         canny = getIntegralCanny(img, width, height);
         timer.print("canny pruning");
      }

      boolean simple = true;
      if (simple) {
         StopWatch faceDetectTimer = new StopWatch("face detection");
         faceDetectTimer.start();
         boolean multiThread = true;

         if (multiThread) {
            ExecutorService threadPool = Executors.newFixedThreadPool(16);
            boolean inner = true;
            if (inner) {
               for (float scale = baseScale; scale < maxScale; scale *= scale_inc) {

                  //  int loops = 0;
                  //  timer.start();
                  int step = (int) (scale * size.x * increment);
                  int size = (int) (scale * this.size.x);
                  for (int i = 0; i < width - size; i += step) {
                     for (int j = 0; j < height - size; j += step) {
                        final int i_final = i;
                        final int j_final = j;
                        final float scale_final = scale;
                        final int size_final = size;
                        Runnable r = new Runnable(){
                           public void run() {

                              boolean pass = true;
                              for (Stage s : stages) {
                                 if (!s.pass(grayImage, squares, width, height, i_final, j_final, scale_final)) {
                                    pass = false;
                                    //  System.out.println("Failed at Stage " + k);
                                    break;
                                 }
                              }
                              if (pass) {
                                 System.out.println("found!");
                                 synchronized (ret) {
                                    ret.add(new Rectangle(i_final, j_final, size_final, size_final));
                                 }
                              }
                           }
                        };
                        threadPool.execute(r);
                     }
                  }
                  //  timer.print("scale " + scale + " " + loops + " ");
               }
            } else {
               for (float scale = baseScale; scale < maxScale; scale *= scale_inc) {

                  //  int loops = 0;
                  //  timer.start();
                  final int step = (int) (scale * size.x * increment);
                  final int size = (int) (scale * this.size.x);
                  final float scale_final = scale;

                  for (int i = 0; i < width - size; i += step) {
                     final int i_final = i;
                     Runnable r = new Runnable(){
                        public void run() {
                           for (int j = 0; j < height - size; j += step) {

                              int j_final = j;

                              final int size_final = size;

                              boolean pass = true;
                              for (Stage s : stages) {
                                 if (!s.pass(grayImage, squares, width, height, i_final, j_final, scale_final)) {
                                    pass = false;
                                    //  System.out.println("Failed at Stage " + k);
                                    break;
                                 }
                              }
                              if (pass) {
                                 System.out.println("found!");
                                 synchronized (ret) {
                                    ret.add(new Rectangle(i_final, j_final, size_final, size_final));
                                 }
                              }
                           }

                        }
                     };
                     threadPool.execute(r);
                  }
                  //  timer.print("scale " + scale + " " + loops + " ");

               }
            }
            threadPool.shutdown();
            try {
               threadPool.awaitTermination(60, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
               // TODO Auto-generated catch block
               e.printStackTrace();
            }
         } else {

            for (float scale = baseScale; scale < maxScale; scale *= scale_inc) {
               int loops = 0;
               timer.start();
               int step = (int) (scale * size.x * increment);
               int size = (int) (scale * this.size.x);
               for (int i = 0; i < width - size; i += step) {
                  for (int j = 0; j < height - size; j += step) {

                     boolean pass = true;
                     for (Stage s : stages) {
                        if (!s.pass(grayImage, squares, width, height, i, j, scale)) {
                           pass = false;
                           //  System.out.println("Failed at Stage " + k);
                           break;
                        }
                     }
                     if (pass) {
                        System.out.println("found!");
                        ret.add(new Rectangle(i, j, size, size));
                     }
                  }
               }
               timer.print("scale " + scale + " " + loops + " ");
            }
         }
         faceDetectTimer.stop();
      } else {

         for (float scale = baseScale; scale < maxScale; scale *= scale_inc) {
            int loops = 0;
            timer.start();
            int step = (int) (scale * size.x * increment);
            int size = (int) (scale * this.size.x);
            for (int i = 0; i < width - size; i += step) {
               for (int j = 0; j < height - size; j += step) {
                  if (doCannyPruning) {
                     int edges_density = canny[i + size + (j + size) * width] + canny[i + (j) * width]
                           - canny[i + (j + size) * width] - canny[i + size + (j) * width];
                     int d = edges_density / size / size;
                     if (d < 20 || d > 100)
                        continue;
                  }
                  boolean pass = true;
                  int k = 0;
                  for (Stage s : stages) {
                     if (!s.pass(grayImage, squares, width, height, i, j, scale)) {
                        pass = false;
                        //  System.out.println("Failed at Stage " + k);
                        break;
                     }
                     k++;
                  }
                  if (pass) {

                     System.out.println("found!");
                     ret.add(new Rectangle(i, j, size, size));
                  }
               }
            }
            timer.print("scale " + scale + " " + loops + " ");
         }
      }

      return merge(ret, min_neighbors);
   }

   public int[] getIntegralCanny(int[] grayImage, int width, int height) {

      int[] canny = new int[grayImage.length];
      final StopWatch timer = new StopWatch();
      timer.start();
      for (int i = 2; i < width - 2; i++) {
         for (int j = 2; j < height - 2; j++) {
            int sum = 0;
            sum += 2 * grayImage[i - 2 + (j - 2) * width];
            sum += 4 * grayImage[i - 2 + (j - 1) * width];
            sum += 5 * grayImage[i - 2 + (j + 0) * width];
            sum += 4 * grayImage[i - 2 + (j + 1) * width];
            sum += 2 * grayImage[i - 2 + (j + 2) * width];
            sum += 4 * grayImage[i - 1 + (j - 2) * width];
            sum += 9 * grayImage[i - 1 + (j - 1) * width];
            sum += 12 * grayImage[i - 1 + (j + 0) * width];
            sum += 9 * grayImage[i - 1 + (j + 1) * width];
            sum += 4 * grayImage[i - 1 + (j + 2) * width];
            sum += 5 * grayImage[i + 0 + (j - 2) * width];
            sum += 12 * grayImage[i + 0 + (j - 1) * width];
            sum += 15 * grayImage[i + 0 + (j + 0) * width];
            sum += 12 * grayImage[i + 0 + (j + 1) * width];
            sum += 5 * grayImage[i + 0 + (j + 2) * width];
            sum += 4 * grayImage[i + 1 + (j - 2) * width];
            sum += 9 * grayImage[i + 1 + (j - 1) * width];
            sum += 12 * grayImage[i + 1 + (j + 0) * width];
            sum += 9 * grayImage[i + 1 + (j + 1) * width];
            sum += 4 * grayImage[i + 1 + (j + 2) * width];
            sum += 2 * grayImage[i + 2 + (j - 2) * width];
            sum += 4 * grayImage[i + 2 + (j - 1) * width];
            sum += 5 * grayImage[i + 2 + (j + 0) * width];
            sum += 4 * grayImage[i + 2 + (j + 1) * width];
            sum += 2 * grayImage[i + 2 + (j + 2) * width];

            canny[i + j * width] = sum / 159;
            //System.out.println(canny[i][j]);
         }
      }
      timer.print("canny convolution");
      timer.start();
      int[] grad = new int[grayImage.length];
      for (int i = 1; i < width - 1; i++) {
         for (int j = 1; j < height - 1; j++) {
            int grad_x = -canny[i - 1 + (j - 1) * width] + canny[i + 1 + (j - 1) * width] - 2 * canny[i - 1 + (j) * width] + 2
                  * canny[i + 1 + (j) * width] - canny[i - 1 + (j + 1) * width] + canny[i + 1 + (j + 1) * width];
            int grad_y = canny[i - 1 + (j - 1) * width] + 2 * canny[i + (j - 1) * width] + canny[i + 1 + (j - 1) * width]
                  - canny[i - 1 + (j + 1) * width] - 2 * canny[i + (j + 1) * width] - canny[i + 1 + (j + 1) * width];
            grad[i + j * width] = Math.abs(grad_x) + Math.abs(grad_y);
            //System.out.println(grad[i][j]);
         }
      }
      timer.print("canny convolution 2");
      timer.start();
      //JFrame f = new JFrame();
      //f.setContentPane(new DessinChiffre(grad));
      //f.setVisible(true);
      for (int i = 0; i < width; i++) {
         int col = 0;
         for (int j = 0; j < height; j++) {
            int value = grad[i + j * width];
            canny[i + j * width] = (i > 0 ? canny[i - 1 + j * width] : 0) + col + value;
            col += value;
         }
      }
      timer.print("canny convolution 3");
      return canny;

   }

   public List<java.awt.Rectangle> merge(List<java.awt.Rectangle> rects, int min_neighbors) {

      List<java.awt.Rectangle> retour = new LinkedList<java.awt.Rectangle>();
      int[] ret = new int[rects.size()];
      int nb_classes = 0;
      for (int i = 0; i < rects.size(); i++) {
         boolean found = false;
         for (int j = 0; j < i; j++) {
            if (equals(rects.get(j), rects.get(i))) {
               found = true;
               ret[i] = ret[j];
            }
         }
         if (!found) {
            ret[i] = nb_classes;
            nb_classes++;
         }
      }
      //System.out.println(Arrays.toString(ret));
      int[] neighbors = new int[nb_classes];
      Rectangle[] rect = new Rectangle[nb_classes];
      for (int i = 0; i < nb_classes; i++) {
         neighbors[i] = 0;
         rect[i] = new Rectangle(0, 0, 0, 0);
      }
      for (int i = 0; i < rects.size(); i++) {
         neighbors[ret[i]]++;
         rect[ret[i]].x += rects.get(i).x;
         rect[ret[i]].y += rects.get(i).y;
         rect[ret[i]].height += rects.get(i).height;
         rect[ret[i]].width += rects.get(i).width;
      }
      for (int i = 0; i < nb_classes; i++) {
         int n = neighbors[i];
         if (n >= min_neighbors) {
            Rectangle r = new Rectangle(0, 0, 0, 0);
            r.x = (rect[i].x * 2 + n) / (2 * n);
            r.y = (rect[i].y * 2 + n) / (2 * n);
            r.width = (rect[i].width * 2 + n) / (2 * n);
            r.height = (rect[i].height * 2 + n) / (2 * n);
            retour.add(r);
         }
      }

      return retour;

   }

   public boolean equals(Rectangle r1, Rectangle r2) {

      int distance = (int) (r1.width * 0.2);

      /*return r2.x <= r1.x + distance &&
             r2.x >= r1.x - distance &&
             r2.y <= r1.y + distance &&
             r2.y >= r1.y - distance &&
             r2.width <= (int)( r1.width * 1.2 ) &&
             (int)( r2.width * 1.2 ) >= r1.width;*/
      if (r2.x <= r1.x + distance && r2.x >= r1.x - distance && r2.y <= r1.y + distance && r2.y >= r1.y - distance
            && r2.width <= (int) (r1.width * 1.2) && (int) (r2.width * 1.2) >= r1.width) {

         return true;
      }
      if (r1.x >= r2.x && r1.x + r1.width <= r2.x + r2.width && r1.y >= r2.y && r1.y + r1.height <= r2.y + r2.height) {

         return true;
      }

      return false;

   }
}
