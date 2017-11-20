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

import org.pmw.tinylog.Logger;
import cv.lecturesight.framesource.FrameSource;
import cv.lecturesight.framesource.FrameSourceProvider;
import cv.lecturesight.objecttracker.ObjectTracker;
import cv.lecturesight.objecttracker.TrackerObject;
import cv.lecturesight.operator.CameraOperator;
import cv.lecturesight.ptz.steering.api.CameraSteeringWorker;
import cv.lecturesight.util.conf.Configuration;
import cv.lecturesight.util.conf.ConfigurationListener;
import cv.lecturesight.util.metrics.MetricsService;
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
public class SimpleCameraOperator implements CameraOperator, ConfigurationListener {

  @Reference
  Configuration config;

  @Reference
  MetricsService metrics;

  @Reference
  ObjectTracker tracker;
  
  @Reference
  CameraSteeringWorker steerer;
  
  @Reference
  FrameSourceProvider fsp;
  FrameSource fsrc;
  
  int interval = 200;
  int target_timeout;
  int tracking_timeout;
  int idle_preset = -1;
  int target_limit = 10;

  CoordinatesNormalization normalizer;
  ScheduledExecutorService executor;
  CameraOperatorWorker worker;
 
  float start_pan = 0;
  float start_tilt = 0;
  float start_zoom = 0;
  float frame_width = 0;

  protected void activate(ComponentContext cc) throws Exception {

    setConfiguration();
    Logger.info("Activated");

    fsrc = fsp.getFrameSource();
    normalizer = new CoordinatesNormalization(fsrc.getWidth(), fsrc.getHeight());
    start();
  }

  protected void deactivate(ComponentContext cc) {
    stop();
    Logger.info("Deactivated");
  }

  /*
   ** Set configuration values
   */
  private void setConfiguration() {
    target_timeout = config.getInt(Constants.PROPKEY_TARGET_TIMEOUT);
    tracking_timeout = config.getInt(Constants.PROPKEY_TRACKING_TIMEOUT);
    idle_preset = config.getInt(Constants.PROPKEY_IDLE_PRESET);
    start_pan = config.getFloat(Constants.PROPKEY_PAN);
    start_tilt = config.getFloat(Constants.PROPKEY_TILT);
    start_zoom = config.getFloat(Constants.PROPKEY_ZOOM);
    frame_width = config.getFloat(Constants.PROPKEY_FRAME_WIDTH);
    target_limit = config.getInt(Constants.PROPKEY_TARGET_LIMIT);

    Logger.debug("Target timeout: " + target_timeout + " ms, tracking timeout: " + tracking_timeout + " ms" +
                 ", idle.preset: " + idle_preset +
                 ", initial pan: " + start_pan + ", tilt: " + start_tilt + ", zoom: " + start_zoom + 
                 ", frame.width: " + frame_width + ", target.limit: " + target_limit);
  }

  @Override
  public void configurationChanged() {
    Logger.debug("Refreshing configuration");
    setConfiguration();
  }

  @Override
  public void start() {
    if (executor == null) {
      executor = Executors.newScheduledThreadPool(1);
      worker = new CameraOperatorWorker();

      setInitialTrackingPosition();
      steerer.setSteering(true);

      executor.scheduleAtFixedRate(worker, 0, interval, TimeUnit.MILLISECONDS);
      Logger.info("Started");
    }
  }

  @Override
  public void stop() {
    if (executor != null) {
      executor.shutdownNow();
      executor = null;
      Logger.debug("Stopped worker thread");
    }

    steerer.setSteering(false);
    setIdlePosition();
    Logger.info("Stopped");
  }

  @Override
  public void reset() {
      Logger.debug("Reset");
      setInitialTrackingPosition();
  }

  @Override
  public boolean isRunning() {
      return steerer.isSteering();
  }

  /*
   * Move the camera to the initial pan/tilt/zoom position for start of tracking
   */
  private void setInitialTrackingPosition() {
      Logger.debug("Set initial tracking position");
      NormalizedPosition neutral = new NormalizedPosition(start_pan, start_tilt);
      steerer.setZoom(start_zoom);  
      steerer.setInitialPosition(neutral);
  }

  /* 
   * Return the camera to the start pan/tilt position
   */
  private void returnInitialTrackingPosition() {
      Logger.debug("Return to initial tracking position");
      NormalizedPosition neutral = new NormalizedPosition(start_pan, start_tilt);
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
    long first_tracked_time;
    long last_tracked_time;

    @Override
    public void run() {

      long now = System.currentTimeMillis();

      if (target == null) {

        // Get the list of available targets
        List<TrackerObject> objs = tracker.getCurrentlyTracked();

        // Find the best target if more than one is available
        if ((objs.size() > 0) && (objs.size() <= target_limit)) {
          // only track one target at a time
          target = findBestTrackedObject(objs);
          if (target != null) {
             metrics.incCounter("camera.operator.target.new");
             first_tracked_time = now;
             Logger.debug("Acquired new tracking target (" + objs.size() + " available) first_tracked_time=" + first_tracked_time);
          }
        }

        // Return to the initial position if no targets have been visible for a while
        if ((objs.isEmpty() || target == null) && (tracking_timeout > 0) && (last_tracked_time > 0) && (now - last_tracked_time > tracking_timeout)) {
	  returnInitialTrackingPosition();
          metrics.incCounter("camera.operator.move.home");
          last_tracked_time = 0;
        }

      } else {

        if (now - target.lastSeen() < target_timeout) {

	  boolean move = true;
          last_tracked_time = now;

          // Target position
	  Position obj_pos = (Position) target.getProperty(ObjectTracker.OBJ_PROPKEY_CENTROID);
	  NormalizedPosition obj_posN = normalizer.toNormalized(obj_pos);
	  NormalizedPosition target_pos = new NormalizedPosition(obj_posN.getX(), config.getFloat(Constants.PROPKEY_TILT));

 	  // Actual position
          NormalizedPosition actual_pos = steerer.getActualPosition();

          Logger.debug("Tracking object " + target + " currently at position " + target_pos);

          // Reduce pan activity - only start moving camera if the target is approaching the frame boundaries
          if ((frame_width > 0) && !steerer.isMoving()) {

		  double trigger_left  = actual_pos.getX() - (frame_width/2);
		  double trigger_right = actual_pos.getX() + (frame_width/2);

		  if ((target_pos.getX() < trigger_right) && (target_pos.getX() > trigger_left)) {
		       move = false;
		       Logger.debug("Not moving: camera=" + actual_pos + " target=" + target_pos + 
				 " position is inside frame trigger limits " + String.format("%.4f to %.4f", trigger_left, trigger_right));
		  }
          }

          if (move) {
		  Logger.debug("Moving steerer for object " + target + " to position " + target_pos);
		  steerer.setTargetPosition(target_pos);
          }

        } else {
          // Target has timed out
          Logger.debug("Target has timed out first_tracked_time=" + first_tracked_time + " lastSeen=" + target.lastSeen() + " age=" + (now-target.lastSeen()));
          target = null;
          metrics.timedEvent("camera.operator.target.tracked", now - first_tracked_time);
        }
      }
    }

    private TrackerObject findBestTrackedObject(List<TrackerObject> objects) {

      // Track the oldest object

      TrackerObject out = null;
      long oldest = System.currentTimeMillis();

      for (TrackerObject obj : objects) {
        if (obj.firstSeen() < oldest) {
          oldest = obj.firstSeen();
          out = obj;
        }
      }
      return out;
    }
  }
}
