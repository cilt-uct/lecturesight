/* Copyright (C) 2012 Benjamin Wulff
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 *
 */
package cv.lecturesight.ptz.steering.gui;

import cv.lecturesight.ptz.steering.api.CameraSteeringWorker;
import cv.lecturesight.ptz.steering.api.UISlave;
import cv.lecturesight.util.geometry.CoordinatesNormalization;
import cv.lecturesight.util.geometry.NormalizedPosition;
import cv.lecturesight.util.geometry.Position;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import javax.swing.JPanel;

public class CameraControlPanel extends JPanel implements UISlave, MouseListener {
  
  private static final int DEFAULT_WIDTH = 400;
  private static final int DEFAULT_HEIGHT = 300;
  
  private CoordinatesNormalization normalizer = new CoordinatesNormalization(DEFAULT_WIDTH, DEFAULT_HEIGHT);
  private CameraSteeringWorker camera;
  
  // gaphics stuff
  private final Color targetColor = Color.yellow;
  private final Color positionColor = Color.cyan;
  private final Font font = new Font("Monospaced", Font.PLAIN, 10);
  
  public CameraControlPanel(CameraSteeringWorker camera) {
    this.camera = camera;
    this.setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
    this.addMouseListener(this);
  }
  
  @Override
  public void paint(Graphics g) {
    updateSize();   // update normalizer in case display size changed
    
    // get center of coordinate system in display
    int rootX = getWidth() / 2;
    int rootY = getHeight() / 2;
    
    g.setFont(font);
    
    // clear panel
    g.setColor(Color.black);
    g.fillRect(0, 0, getWidth(), getHeight());
    
    // status string at bottom of viewport
//    g.setColor(Color.green);
//    int y = getHeight() - 38;
//    for (String ln : model.getStatus().split("\\n")) {
//      g.drawString(ln, 2, y);
//      y += 10;
//    }
    
    // draw movement indicator
    g.drawRect(1, 1, 20, 10);
    g.setColor(camera.isMoving() ? Color.green : Color.black);
    g.fillRect(2, 2, 19, 9);
    g.setColor(camera.isMoving() ? Color.black : Color.green);
    g.drawString(camera.isMoving() ? "MOV" : "STL", 2, 10);
    
    // coordinate cross
    // x axis
    g.setColor(Color.green);
    g.drawLine(1, rootY, getWidth()-2, rootY);
    
    g.drawLine(1, rootY-3, 1, rootY + 3);
    g.drawLine(rootX/2, rootY-3, rootX/2, rootY + 3);
    g.drawString(Integer.toString(camera.getPanMin()), 2, rootY - 2);
    g.drawLine(getWidth()-2, rootY-3, getWidth()-2, rootY + 3);
    g.drawLine(rootX + (rootX/2), rootY-3, rootX + (rootX/2), rootY + 3);
    g.drawString(Integer.toString(camera.getPanMax()), getWidth() - 30, rootY + 11);
    
    // y axis
    g.drawLine(rootX, 1, rootX, getHeight()-2);
    
    g.drawLine(rootX - 3, 1, rootX + 3, 1);
    g.drawLine(rootX - 3, rootY/2, rootX + 3, rootY/2);
    g.drawString(Integer.toString(camera.getTiltMax()), rootX + 2, 12);
    g.drawLine(rootX - 3, getHeight()-2, rootX + 3, getHeight()-2);
    g.drawLine(rootX - 3, rootY + (rootY/2), rootX + 3, rootY + (rootY/2));
    g.drawString(Integer.toString(camera.getTiltMin()), rootX + 2, getHeight() - 3);
    
    // get camera and target position
    NormalizedPosition tposn = camera.getTargetPosition();
    String targetPosStr = camera.toCameraCoordinates(tposn).toString();
    
    tposn.setY(tposn.getY() * -1);                      // compute screen position
    Position tpos = normalizer.fromNormalized(tposn);   
    
    NormalizedPosition aposn = camera.getActualPosition();
    String cameraPosStr = camera.toCameraCoordinates(aposn).toString();
    
    aposn.setY(aposn.getY() * -1);                      // compute screen position
    Position apos = normalizer.fromNormalized(aposn);   
    
    // draw camera and target position
    g.setColor(Color.pink);
    g.drawLine(tpos.getX(), tpos.getY(), apos.getX(), apos.getY());
    
    g.setColor(targetColor);
    drawCursor(g, tpos.getX(), tpos.getY(), targetColor);
    g.drawString(targetPosStr, tpos.getX() + 5, tpos.getY() - 1);
    
    g.setColor(positionColor);
    drawCursor(g, apos.getX(), apos.getY(), positionColor);
    g.drawString(cameraPosStr, apos.getX() + 5, apos.getY() + 10);
  }
  
  private void drawCursor(Graphics g, int x, int y, Color color) {
    Color tmp = g.getColor();
    g.setColor(color);
    g.drawLine(x, y, x, y);
    g.drawLine(x, y-4, x-4, y);
    g.drawLine(x, y-4, x+4, y);
    g.drawLine(x, y+4, x-4, y);
    g.drawLine(x, y+4, x+4, y);
    g.setColor(tmp);
  }

  private void updateSize() {
    normalizer.setMaxX(getWidth());
    normalizer.setMaxY(getHeight());
  }
  
  @Override
  public Dimension getPreferredSize() {
    return new Dimension(400, 300);
  }
  
  @Override
  public void mouseClicked(MouseEvent e) {
    NormalizedPosition tpos = new NormalizedPosition(
            normalizer.normalizeX(e.getX()),
            normalizer.normalizeY(e.getY()));
    tpos.setY(tpos.getY() * -1);
    camera.setTargetPosition(tpos);
    repaint(); 
  }
  
  @Override
  public void refresh() {
    repaint();
  }

  // <editor-fold defaultstate="collapsed">
  @Override
  public void mousePressed(MouseEvent e) {
  }

  @Override
  public void mouseReleased(MouseEvent e) {
  }

  @Override
  public void mouseEntered(MouseEvent e) {
  }

  @Override
  public void mouseExited(MouseEvent e) {
  }
  // </editor-fold>
}
