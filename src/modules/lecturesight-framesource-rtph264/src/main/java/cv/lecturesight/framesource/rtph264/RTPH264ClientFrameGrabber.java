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
package cv.lecturesight.framesource.rtph264;

import cv.lecturesight.framesource.FrameGrabber;
import cv.lecturesight.framesource.FrameSourceException;

import org.freedesktop.gstreamer.Buffer;
import org.freedesktop.gstreamer.Caps;
import org.freedesktop.gstreamer.Element;
import org.freedesktop.gstreamer.ElementFactory;
import org.freedesktop.gstreamer.Pipeline;
import org.freedesktop.gstreamer.Sample;
import org.freedesktop.gstreamer.State;
import org.freedesktop.gstreamer.Structure;
import org.freedesktop.gstreamer.elements.AppSink;
import org.pmw.tinylog.Logger;

import java.nio.ByteBuffer;

public class RTPH264ClientFrameGrabber implements FrameGrabber {

  private String host;
  private int port;
  private final Pipeline pipeline;
  private int width;
  private int height;
  private ByteBuffer lastFrame;
  private Buffer lastBuf;
  private Sample lastSam;
  private AppSink appsink;

  public RTPH264ClientFrameGrabber(String host, int port) throws FrameSourceException {
    this.host = host;
    this.port = port;
    pipeline = createPipeline(host, port);
    start();
    getVideoFrameSize();
  }

  private Pipeline createPipeline(String host, int port) throws IllegalStateException {
    Pipeline pipeline = new Pipeline("RTP h264 Client");

    Element tcpclientsrc = createElement("tcpclientsrc", "tcpclientsrc");
    tcpclientsrc.set("host", host);
    tcpclientsrc.set("port", port);
    addToPipeline(pipeline, tcpclientsrc);

    Element gdpdepay = createElement("gdpdepay", "gdpdepay");
    addToPipeline(pipeline, gdpdepay);
    linkElements(tcpclientsrc, gdpdepay);

    Element rtph264depay = createElement("rtph264depay", "rtph264depay");
    addToPipeline(pipeline, rtph264depay);
    linkElements(gdpdepay, rtph264depay);

    Element avdec_h264 = createElement("avdec_h264", "avdec_h264");
    addToPipeline(pipeline, avdec_h264);
    linkElements(rtph264depay, avdec_h264);

    Element videoconvert = createElement("videoconvert", "videoconvert");
    addToPipeline(pipeline, videoconvert);
    linkElements(avdec_h264, videoconvert);

    Caps caps = Caps.fromString("video/x-raw,format=RGB");

    Element capsfilter = createElement("capsfilter", "capsfilter");
    capsfilter.set("caps", caps);
    addToPipeline(pipeline, capsfilter);
    linkElements(videoconvert, capsfilter);

    appsink = (AppSink) createElement("appsink", "appsink");
    appsink.setCaps(caps);
    appsink.set("async", "true");
    appsink.set("sync", "false");
    appsink.set("drop", "true");
    appsink.set("max-buffers", "5");

    addToPipeline(pipeline, appsink);
    linkElements(capsfilter, appsink);

    return pipeline;
  }

  private static Element createElement(String fname, String ename) throws IllegalStateException {
    Element elm = ElementFactory.make(fname, ename);
    if (elm == null) {
      throw new IllegalStateException("Failed to create element: " + fname);
    }
    return elm;
  }

  private static void addToPipeline(Pipeline pipeline, Element elm) throws IllegalStateException {
    if (!pipeline.add(elm)) {
      throw new IllegalStateException("Failed to add " + elm.getName() + " to pipeline!");
    }
  }

  private static void linkElements(Element elm, Element toLink) throws IllegalStateException {
    if (!elm.link(toLink)) {
      throw new IllegalStateException("Failed to link " + toLink.getName() + " to " + elm.getName());
    }
  }

  void start() {
    pipeline.play();
  }

  void stop() {
    pipeline.setState(State.NULL);
  }

  private void getVideoFrameSize() throws FrameSourceException {
    try {
      Structure str = appsink.pullPreroll().getCaps().getStructure(0);
      width = str.getInteger("width");
      height = str.getInteger("height");
    } catch (Exception e) {
      throw new FrameSourceException("Could not determine frame dimensions. ", e);
    }
  }

  @Override
  public ByteBuffer captureFrame() throws FrameSourceException {
    if (!appsink.isEOS()) {
      Sample sam = appsink.pullSample();
      Buffer buf = sam.getBuffer();
      if (buf != null) {
        lastFrame = buf.map(false);
        if (lastBuf != null) {
           // Free memory allocated for the previous buffer so we don't leak memory
           lastBuf.unmap();
           lastSam.dispose();
        }
        lastBuf = buf;
        lastSam = sam;
      } else {
        Logger.warn("Buffer is NULL!!");
      }
    }

    if (lastFrame == null) {
      throw new FrameSourceException("Stream is EOS and no previously captured frame available.");
    }

    return lastFrame;
  }

  @Override
  public int getWidth() {
    return width;
  }

  @Override
  public int getHeight() {
    return height;
  }

  @Override
  public PixelFormat getPixelFormat() {
    return PixelFormat.RGB_8BIT;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("(").append("host=").append(host).append(" port=").append(port)
            .append(" format=").append(getPixelFormat().name())
            .append(" size=").append(width).append("x").append(height).append(")");
    return sb.toString();
  }
}
