/* Copyright (C) 2017 University of Cape Town
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
package cv.lecturesight.util.status;

import cv.lecturesight.util.conf.Configuration;
import cv.lecturesight.util.conf.ConfigurationListener;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.osgi.service.component.ComponentContext;
import org.pmw.tinylog.Logger;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component(name="lecturesight.util.status", immediate=true)
@Service
@Properties({
@Property(name = "osgi.command.scope", value = "status"),
@Property(name = "osgi.command.function", value = {"show"})
})

public class StatusServiceImpl implements StatusService, ConfigurationListener {

  @Reference
  private Configuration config;

  public static final String PROPKEY_ENABLE = "status.enable";
  public static final String PROPKEY_URL = "status.url";
  public static final String PROPKEY_INTERVAL = "status.interval";

  // Enabled by default
  private boolean enable = true;

  // Reporting URL
  private String url = null;

  // Shutting down
  private boolean shutdown = false;

  /* Reporting interval */
  private int interval = 30;

  private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);
  private StatusExecutor statusExecutor = new StatusExecutor();

  protected void activate(ComponentContext cc) {

    setConfiguration();

    if (enable) {
      Logger.info("Status Reporting Enabled (URL={}, interval={})", url, interval);
    } else {
      Logger.info("Status Reporting Disabled");
    }

    if (enable) {
      startReporting();
    }

    Logger.debug("Activated");
  }

  protected void deactivate(ComponentContext cc) {
    stopReporting();
    shutdown = true;
    Logger.debug("Deactivated");
  }

  @Override
  public void configurationChanged() {
    if (updateConfiguration() && !shutdown) {
      Logger.debug("Configuration updated");
      stopReporting();
      startReporting();
    }
  }

  private void setConfiguration() {
    enable = config.getBoolean(PROPKEY_ENABLE);
    interval = config.getInt(PROPKEY_INTERVAL);
    url = config.get(PROPKEY_URL);
  }

  private boolean updateConfiguration() {

    // Ideally we would only get configurationChanged() events for this service, but
    // as it's called for any config change, we need to see what's changed.

    boolean changed = false;

    if (enable != config.getBoolean(PROPKEY_ENABLE)) {
      enable = !enable;
      changed = true;
    }

    if (interval != config.getInt(PROPKEY_INTERVAL)) {
      interval = config.getInt(PROPKEY_INTERVAL);
      changed = true;
    }

    if (url == null || !url.equals(config.get(PROPKEY_URL))) {
      url = config.get(PROPKEY_URL);
      changed = true;
    }

    return changed;
  }

  private void startReporting() {

    if (!enable) return;

    // activate the status executor
    executor.scheduleAtFixedRate(statusExecutor, interval, interval, TimeUnit.SECONDS);

  }

  private void stopReporting() {
    executor.shutdownNow();     // shut down the status executor
  }


  /**
   * Periodically called <code>Runnable</code> that is responsible for status reporting.
   */
  class StatusExecutor implements Runnable {

    @Override
    public void run() {

      // Post to URL
      Logger.debug("Sending status update to {}", url);

    }
  }

}
