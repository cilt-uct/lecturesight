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
package cv.lecturesight.cameraoperator.simple;

import cv.lecturesight.framesource.FrameSource;
import cv.lecturesight.framesource.FrameSourceProvider;
import cv.lecturesight.objecttracker.ObjectTracker;
import cv.lecturesight.objecttracker.TrackerObject;
import cv.lecturesight.operator.CameraOperator;
import cv.lecturesight.ptz.steering.api.CameraSteeringWorker;
import cv.lecturesight.util.Log;
import cv.lecturesight.util.conf.Configuration;
import cv.lecturesight.util.geometry.CoordinatesNormalization;
import cv.lecturesight.util.geometry.NormalizedPosition;
import cv.lecturesight.util.geometry.Position;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.osgi.service.component.ComponentContext;

@Component(name = "lecturesight.cameraoperator.panonly", immediate = true)
@Service
public class SimpleCameraOperator implements CameraOperator {

  Log log = new Log("Pan-Only Camera Operator");
  @Reference
  Configuration config;
  
  @Reference
  ObjectTracker tracker;
  
  @Reference
  CameraSteeringWorker steerer;
  
  @Reference
  FrameSourceProvider fsp;
  FrameSource fsrc;
  
  int interval = 200;
  int timeout;
  int idle_preset = -1;
  CoordinatesNormalization normalizer;
  ScheduledExecutorService executor;
  CameraOperatorWorker worker;
 
  float start_zoom = 0;
  float start_tilt = 0;
  float frame_width = 0;

  protected void activate(ComponentContext cc) throws Exception {
    timeout = config.getInt(Constants.PROPKEY_TIMEOUT);
    idle_preset = config.getInt(Constants.PROPKEY_IDLE_PRESET);
    start_zoom = config.getFloat(Constants.PROPKEY_ZOOM);
    start_tilt = config.getFloat(Constants.PROPKEY_TILT);
    frame_width = config.getFloat(Constants.PROPKEY_FRAME_WIDTH);

    fsrc = fsp.getFrameSource();
    normalizer = new CoordinatesNormalization(fsrc.getWidth(), fsrc.getHeight());
    start();    
    log.info("Activated. Timeout is " + timeout + " ms, zoom is " + start_zoom + ", tilt is " + start_tilt);
  }

  protected void deactivate(ComponentContext cc) {
    log.info("Deactivated");
  }

  @Override
  public void start() {
    if (executor == null) {
      executor = Executors.newScheduledThreadPool(1);
      worker = new CameraOperatorWorker();

      setInitialTrackingPosition();
      steerer.setSteering(true);

      executor.scheduleAtFixedRate(worker, 0, interval, TimeUnit.MILLISECONDS);
      log.info("Started");
    }
  }

  @Override
  public void stop() {
    if (executor != null) {
      executor.shutdownNow();
      executor = null;
      log.info("Stopped");
    } else {
      log.warn("Nothing to stop");
    }

    steerer.setSteering(false);
    setIdlePosition();
  }

  @Override
  public void reset() {
      log.debug("Reset");
      setInitialTrackingPosition();
  }

  /* 
   * Move the camera to the initial pan/tilt/zoom position for start of tracking
   */
  private void setInitialTrackingPosition() {
      steerer.setZoom(start_zoom);  
      NormalizedPosition neutral = new NormalizedPosition(0.0f, start_tilt);
      steerer.setTargetPosition(neutral);
  }

  /* 
   * Move the camera to the idle position (not tracking)
   */
  private void setIdlePosition() {
    if (idle_preset >= 0) {
       steerer.movePreset(idle_preset);
    } else {
       steerer.moveHome();
    }
  }

  private class CameraOperatorWorker implements Runnable {

    TrackerObject target = null;

    @Override
    public void run() {
      if (target == null) {
        List<TrackerObject> objs = tracker.getCurrentlyTracked();
        if (objs.size() > 0) {
          target = findBiggestTrackedObject(objs);
        }
      } else {

        if (System.currentTimeMillis() - target.lastSeen() < timeout) {

	  boolean move = true;

          // Target position
	  Position obj_pos = (Position) target.getProperty(ObjectTracker.OBJ_PROPKEY_CENTROID);
	  NormalizedPosition obj_posN = normalizer.toNormalized(obj_pos);
	  NormalizedPosition target_pos = new NormalizedPosition(obj_posN.getX(), config.getFloat(Constants.PROPKEY_TILT));

 	  // Actual position
          NormalizedPosition actual_pos = steerer.getActualPosition();

          log.debug("Tracking object " + target + " currently at position " + target_pos);

          // Reduce pan activity - only start moving camera if the target is approaching the frame boundaries
          if ((frame_width > 0) && !steerer.isMoving()) {

		  double trigger_left  = actual_pos.getX() - (frame_width/2);
		  double trigger_right = actual_pos.getX() + (frame_width/2);

		  if ((target_pos.getX() < trigger_right) && (target_pos.getX() > trigger_left)) {
		       move = false;
		       log.debug("Not moving: camera=" + actual_pos + " target=" + target_pos + 
				 " position is inside frame trigger limits " + String.format("%.4f to %.4f", trigger_left, trigger_right));
		  }
          }

          if (move) {
		  log.debug("Moving steerer for object " + target + " to position " + target_pos);
		  steerer.setTargetPosition(target_pos);
          }

        } else {
          target = null;
        }
      }
    }

    private TrackerObject findBiggestTrackedObject(List<TrackerObject> objects) {
      return objects.get(0);
//      TrackerObject out = null;
//      int maxWeight = 0;
//      for (TrackerObject obj : objects) {
//        Integer weight = (Integer) obj.getProperty(ObjectTracker.OBJ_PROPKEY_WEIGHT);
//        if (weight > maxWeight) {
//          maxWeight = weight;
//          out = obj;
//        }
//      }
//      return out;
    }
  }
}
