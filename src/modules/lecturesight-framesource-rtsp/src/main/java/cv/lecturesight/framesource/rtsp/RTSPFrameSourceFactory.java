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
package cv.lecturesight.framesource.rtsp;

import cv.lecturesight.framesource.FrameGrabber;
import cv.lecturesight.framesource.FrameGrabberFactory;
import cv.lecturesight.framesource.FrameSourceException;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Service;
import org.gstreamer.Gst;
import org.osgi.service.component.ComponentContext;
import org.pmw.tinylog.Logger;

/**
 * RTP h.264 Streaming FrameSourceFactory
 *
 */
@Component(name = "lecturesight.framesource.rtsp", immediate = true)
@Service
@Properties({
  @Property(name = "cv.lecturesight.framesource.name", value = "RTSP Streaming"),
  @Property(name = "cv.lecturesight.framesource.type", value = "rtsp")
})
public class RTSPFrameSourceFactory implements FrameGrabberFactory {

  private List<RTSPClientFrameGrabber> children = new LinkedList<RTSPClientFrameGrabber>();

  protected void activate(ComponentContext cc) {
    Gst.init();
    Logger.info("RTSP Streaming FrameSource activated");
  }

  protected void deactivate(ComponentContext cc) {
    for (Iterator<RTSPClientFrameGrabber> it = children.iterator(); it.hasNext();) {
      RTSPClientFrameGrabber child = it.next();
      child.stop();
    }
    Logger.info("RTSP Streaming FrameSource deactivated");
  }

  @Override
  public FrameGrabber createFrameGrabber(String input, Map<String, String> conf) throws FrameSourceException {
    
    // attempt to create the FrameGrabber
    try {
      RTSPClientFrameGrabber grabber = new RTSPClientFrameGrabber(input.trim());
      children.add(grabber);
      Logger.info("Created FrameGrabber " + grabber.toString());
      return grabber;
    } catch (IllegalStateException e) {
      String msg = "Failed to create RTSPClientFrameGrabber for " + input;
      Logger.error(msg);
      throw new FrameSourceException(msg, e);
    }
  }

  @Override
  public void destroyFrameGrabber(FrameGrabber fg) throws FrameSourceException {
    try {
      ((RTSPClientFrameGrabber) fg).stop();
    } catch (Exception e) {
      String msg = "Error while stopping FrameSource.";
      Logger.error(msg);
      throw new FrameSourceException(msg, e);
    }
  }
}
