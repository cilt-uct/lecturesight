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
package cv.lecturesight.util.geometry;

import org.apache.commons.math3.analysis.interpolation.SplineInterpolator;
import org.apache.commons.math3.analysis.polynomials.PolynomialSplineFunction;

import java.util.Arrays;

/**
 * Convert between normalized co-ordinates and camera pan/tilt co-ordinates
 */
public class CameraPositionModel {

  // scene limits for normalization
  private int pan_min;
  private int pan_max;
  private int tilt_min;
  private int tilt_max;

  // target position
  private boolean target_set = false;

  // camera position in camera coordinates
  private Position camera_pos = new Position(0, 0);

  // target position in camera coordinates
  private Position target_pos = new Position(0, 0);

  // camera position in normalized coordinates
  private NormalizedPosition target_posn = new NormalizedPosition(0.0f, 0.0f);

  // target position in normalized coordinates
  private NormalizedPosition camera_posn = new NormalizedPosition(0.0f, 0.0f);

  // Interpolation functions
  private PolynomialSplineFunction normCameraX;
  private PolynomialSplineFunction normCameraY;
  private PolynomialSplineFunction cameraNormX;
  private PolynomialSplineFunction cameraNormY;

  // Ranges over which the interpolation functions are valid
  private float minNormX = 1;
  private float maxNormX = -1;
  private float minNormY = 1;
  private float maxNormY = -1;

  private int minCameraX = Integer.MAX_VALUE;
  private int maxCameraX = Integer.MIN_VALUE;
  private int minCameraY = Integer.MAX_VALUE;
  private int maxCameraY = Integer.MIN_VALUE;

  /**
   * Initialize camera position model using scene limits
   * @param pan_min
   * @param pan_max
   * @param tilt_min
   * @param tilt_max
   */
  public CameraPositionModel(int pan_min, int pan_max, int tilt_min, int tilt_max) {
    update(pan_min, pan_max, tilt_min, tilt_max);
  }

  /**
   * Update camera model from scene limits
   */
  public void update(int pan_min, int pan_max, int tilt_min, int tilt_max) {

    if (pan_min != this.pan_min || pan_max != this.pan_max || tilt_min != this.tilt_min || tilt_max != this.tilt_max) {
      // Invalidate the spline functions if the limits have changed
      normCameraX = null;
      normCameraY = null;
      cameraNormX = null;
      cameraNormY = null;
    }

    this.pan_min = pan_min;
    this.pan_max = pan_max;
    this.tilt_min = tilt_min;
    this.tilt_max = tilt_max;
  }

  /**
   * Update the scene model from a set of matching presets.
   * The point sceneMarker[n] on the overview image matches the camera position cameraPreset[n].
   *
   * @param sceneMarker  Array of scene marker co-ordinates
   * @param cameraPreset Array of camera preset co-ordinates matching the scene markers
   * @return true if the model has been updated based on the preset co-ordinates.
   */
  public boolean update(NormalizedPosition[] sceneMarker, Position[] cameraPreset) {

    int points = sceneMarker.length;

    if ((points < 3) || (points != cameraPreset.length)) {
      return false;
    }

    // X Values
    double[] xNorm = new double[points];
    double[] xCamera = new double[points];

    // Y values
    double[] yNorm = new double[points];
    double[] yCamera = new double[points];

    for (int i = 0; i < points; i++) {
      xNorm[i] = (double) sceneMarker[i].getX();
      xCamera[i] = (int) cameraPreset[i].getX();
      yNorm[i] = (double) sceneMarker[i].getY();
      yCamera[i] = (int) cameraPreset[i].getY();
    }

    Arrays.sort(xNorm);
    Arrays.sort(xCamera);
    Arrays.sort(yNorm);
    Arrays.sort(yCamera);

    minNormX = (float) xNorm[0];
    maxNormX = (float) xNorm[points-1];
    minNormY = (float) yNorm[0];
    maxNormY = (float) yNorm[points-1];

    minCameraX = (int) xCamera[0];
    maxCameraX = (int) xCamera[points-1];
    minCameraY = (int) yCamera[0];
    maxCameraY = (int) yCamera[points-1];

    cameraNormX = new SplineInterpolator().interpolate(xCamera, xNorm);
    normCameraX = new SplineInterpolator().interpolate(xNorm, xCamera);
    normCameraY = new SplineInterpolator().interpolate(yNorm, yCamera);
    cameraNormY = new SplineInterpolator().interpolate(yCamera, yNorm);

    return true;
  }

  /**
   * Translates camera coordinates to normalized coordinates (-1 to 1)
   *
   * @param pos camera coordinates
   * @return normalized coordinates
   */
  public NormalizedPosition toNormalizedCoordinates(Position pos) {

    NormalizedPosition out = new NormalizedPosition(0.0f, 0.0f);
    float x = pos.getX();
    float y = pos.getY();

    if ((cameraNormX != null) && (cameraNormY != null)) {

      if ((pos.getX() >= minCameraX) && (pos.getX() <= maxCameraX)) {
        // Spline interpolation
        out.setX((float) cameraNormX.value((double) pos.getX()));
      } else {
        // TODO Linear
      }

      if ((pos.getY() >= minCameraY) && (pos.getX() <= maxCameraY)) {
        // Spline interpolation
        out.setY((float) cameraNormY.value((double) pos.getY()));
      } else {
        // TODO Linear
      }
    } else {
      // Linear mapping
      out.setX((x - pan_min) / (pan_max - pan_min) * 2 - 1);
      out.setY((y - tilt_min) / (tilt_max - tilt_min) * 2 - 1);
    }

    return out;
  }

  /**
   * Translates normalized coordinates (-1 to 1) to camera coordinates.
   *
   * @param pos normalized coordinates
   * @return camera coordinates
   */
  public Position toCameraCoordinates(NormalizedPosition pos) {

    Position out = new Position(0, 0);
    float x = pos.getX();
    float y = pos.getY();

    if ((normCameraX != null) && (normCameraY != null)) {
      if ((pos.getX() >= minNormX) && (pos.getX() <= maxNormX)) {
        // Spline interpolation
        out.setX((int) normCameraX.value((double) pos.getX()));
      } else {
        // TODO Linear
      }

      if ((pos.getY() >= minNormY) && (pos.getY() <= maxNormY)) {
        out.setY((int) normCameraY.value((double) pos.getY()));
      } else {
        // TODO Linear
      }

    } else {
      // Linear mapping
      out.setX((int) ((x + 1) * (pan_max - pan_min) * 0.5 + pan_min));
      out.setY((int) ((y + 1) * (tilt_max - tilt_min) * 0.5 + tilt_min));
    }

    return out;
  }

  public synchronized void setCameraPositionNorm(NormalizedPosition posn) {
    camera_posn = posn;
    camera_pos = toCameraCoordinates(posn);
  }

  public synchronized void setCameraPosition(Position pos) {
    camera_posn = toNormalizedCoordinates(pos);
    camera_pos = pos;
  }

  public synchronized void setTargetPositionNorm(NormalizedPosition posn) {
    target_set = true;
    target_posn = posn;
    target_pos = toCameraCoordinates(posn);
  }

  public synchronized void setTargetPosition(Position pos) {
    target_set = true;
    target_posn = toNormalizedCoordinates(pos);
    target_pos = pos;
  }

  public synchronized Position getCameraPosition() {
    return camera_pos.clone();
  }

  public synchronized NormalizedPosition getCameraPositionNorm() {
    return camera_posn.clone();
  }

  public synchronized Position getTargetPosition() {
    return target_pos.clone();
  }

  public synchronized NormalizedPosition getTargetPositionNorm() {
    return target_posn.clone();
  }

  public boolean isTargetSet() {
    return target_set;
  }

}
