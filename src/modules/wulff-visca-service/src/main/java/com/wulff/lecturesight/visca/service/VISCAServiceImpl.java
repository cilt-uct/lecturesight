package com.wulff.lecturesight.visca.service;

import com.wulff.lecturesight.visca.api.CameraPosition;
import com.wulff.lecturesight.visca.protocol.*;
import com.wulff.lecturesight.visca.api.VISCAService;
import com.wulff.lecturesight.visca.protocol.VISCA.MessageType;
import cv.lecturesight.ptz.api.PTZCamera;
import cv.lecturesight.util.Log;
import cv.lecturesight.util.conf.Configuration;
import gnu.io.CommPort;
import gnu.io.CommPortIdentifier;
import gnu.io.SerialPort;
import gnu.io.SerialPortEvent;
import gnu.io.SerialPortEventListener;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.osgi.service.component.ComponentContext;

@Component(name = "com.bwulff.lecturesight.visca", immediate = true)
@Service
public class VISCAServiceImpl implements VISCAService, SerialPortEventListener {

  public static Log log = new Log("VISCA Service");

  ComponentContext cc;

  boolean debug = false;

  @Reference
  Configuration config;   // service configuration

  // Camera profiles
  Properties defaultProfile;
  Map<String, Properties> cameraProfiles = new HashMap<String, Properties>();

  SerialPort port;        // serial port handle object
  InputStream commIn;     // serial input stream
  OutputStream commOut;   // serial output stream

  private final byte[] buffer = new byte[1024];

  VISCACameraImpl[] cameras = {null, null, null, null, null, null, null}; // max 7 cameras, null == un-registered

  int updateInterval = 200;   // min number of millisec. between state updates on a camera
  int senderInterval = 20;
  private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(2);
  ScheduledFuture updaterHandle;
  ScheduledFuture senderHandle;

  /**
   * OSGI service activation method.
   *
   * @param cc
   */
  protected void activate(ComponentContext cc) {
    this.cc = cc;

    // load device profiles
    loadProfiles(cc);

    // read serial port configuration
    String devicename = config.get(Constants.PROPKEY_PORT_DEVCICE);
    int speed = config.getInt(Constants.PROPKEY_PORT_SPEED);
    int databits = config.getInt(Constants.PROPKEY_PORT_DATABITS);
    String stopbits = config.get(Constants.PROPKEY_PORT_STOPBITS);
    String parity = config.get(Constants.PROPKEY_PORT_PARITY);
    updateInterval = config.getInt(Constants.PROPKEY_UPDATER_INTERVAL);
    debug = config.getBoolean(Constants.PROPKEY_DEBUG);

    // open serial port
    initPort(devicename, speed, databits, stopbits, parity);
    log.info("Opened port " + devicename);

    // attach port listener
    try {
      port.addEventListener(this);
      port.notifyOnDataAvailable(true);
    } catch (Exception e) {
      throw new IllegalStateException("Unable to attach listener to serial port. ", e);
    }

    // start with sending the network discovery command
    send_AddressSet();

    // start update thread
    updaterHandle = executor.scheduleAtFixedRate(new CameraStateUpdater(),
            updateInterval, updateInterval, TimeUnit.MILLISECONDS);

    // start sender thread
    senderHandle = executor.scheduleAtFixedRate(new CameraCommandSender(),
            senderInterval, senderInterval, TimeUnit.MILLISECONDS);
  }

  /**
   * OSGI service de-activation method.
   *
   * @param cc
   */
  protected void deactivate(ComponentContext cc) {
    deinitPort();
  }

  /**
   * Initializes the serial port.
   *
   * @param devicename
   * @param speed
   * @param databits
   * @param stopbits
   * @param parity
   */
  void initPort(String devicename, int speed, int databits, String stopbits, String parity) {
    CommPortIdentifier portIdent = null;

    try {
      portIdent = CommPortIdentifier.getPortIdentifier(devicename);
    } catch (Exception e) {
      throw new IllegalStateException("Device not found!", e);
    }

    if (portIdent.isCurrentlyOwned()) {
      throw new IllegalStateException(devicename + " is used by another program.");
    } else {
      try {
        CommPort comm = portIdent.open(this.getClass().getName(), 2000);

        if (comm instanceof SerialPort) {

          port = (SerialPort) comm;
          port.setSerialPortParams(speed,
                  mapDatabitsParam(databits),
                  mapStopbitsParam(stopbits),
                  mapParityParam(parity));

          commIn = port.getInputStream();
          commOut = port.getOutputStream();
        } else {
          throw new IllegalStateException("Device is not a serial port!");
        }
      } catch (Exception e) {
        throw new IllegalStateException("Failed to open serial port. ", e);
      }
    }
  }

  /**
   * Shuts down serial communication.
   *
   */
  void deinitPort() {
    // TODO destroy camera objects
    port.close();
  }

  /**
   * Load the camera model profiles from the bundle resources into memory.
   *
   * @param context
   */
  void loadProfiles(ComponentContext context) {
    // load device profiles
    Enumeration entryURLs = context.getBundleContext().getBundle().findEntries("profiles", "*.properties", false);
    while (entryURLs.hasMoreElements()) {
      URL url = (URL) entryURLs.nextElement();
      try {
        Properties props = new Properties();
        props.load(url.openStream());
        if (props.containsKey(Constants.PROFKEY_MODEL_ID)) {
          String idStr = props.getProperty(Constants.PROFKEY_MODEL_ID);
          if (idStr.equals("DEFAULT")) {
            defaultProfile = props;
            log.info("Registered default camera profile");
          } else {
            cameraProfiles.put(idStr, props);
            log.info("Registered camera profile for " + props.getProperty(Constants.PROFKEY_VENDOR_NAME) + " " + props.getProperty(Constants.PROFKEY_MODEL_NAME));
          }
        } else {
          log.warn("Camera profile " + url.toString() + " does not contain model ID!");
        }
      } catch (IOException e) {
        log.warn("Failed to load device profile from " + url.toString() + " : " + e.getMessage());
      }
    }
  }

  /**
   * Sends the content of <code>a</code> over the serial port. Method is
   * synchronized so that competing calls are enqueued.
   *
   * @param a
   */
  synchronized void send(byte[] b) {
    if (debug) {
      log.debug(" >>" + ByteUtils.byteArrayToHex(b, -1));
    }
    try {
      commOut.write(b);
      commOut.flush();
    } catch (Exception e) {
      log.error("Error while sending data: " + ByteUtils.byteArrayToHex(b, -1), e);
    }
  }

  /**
   * Returns the receiver address from VISCA message <code>msg</code>.
   *
   * @param msg
   * @return receiver address
   */
  int getAddress(byte[] msg) {
    return ByteUtils.high(msg[0]) - 8;
  }

  /**
   * Sends the AddressSet broadcast message requesting all cameras to reply with
   * their address.
   *
   */
  void send_AddressSet() {
    log.info("Send network discovery broadcast message.");
    send(VISCA.NET_ADDRESS_SET.getBytes());
  }

  /**
   * Sends the CAM_VersionInq command to the specified camera.
   *
   * @param adr address of the camera to be queried
   */
  void send_CamInfo(int adr) {
    log.info("Send camera info inquiry command to #" + adr);
    Message msg = VISCA.INQ_CAM_VERSION.clone();
    msg.getBytes()[0] += adr;
    send(msg.getBytes());
  }

  void cancelMovement(int cidx) {
    
    // TODO remove movement commands from pendingMsg!
    // TODO what to do with commands in issuedMsg (that did not receive ACK yet)

    log.debug("Cancel movement");
    
    VISCACameraImpl camera;
    if ((camera = cameras[cidx-1]) != null) {
      for (int i=1; i < camera.sockets.length; i++) {   // start with index 1 since 0 is a pseudo-socket (for inquiries)
        Message m = camera.sockets[i];
        if (m != null && m.getMessageType() == MessageType.MOVEMENT) {
          Message cancel = VISCA.NET_COMMAND_CANCEL.clone();
          byte[] pkg = cancel.getBytes();
          pkg[0] += camera.address;
          pkg[1] += i;
          send(pkg);
        }
      }
    }
  }
  
  void clearInterface(int adr) {
    log.debug("Clear interface");
    Message msg = VISCA.NET_IF_CLEAR.clone();
    msg.getBytes()[0] += adr;
    send(msg.getBytes());
  }
  
  /**
   * Creates a camera object that represents a registered VISCA devices and puts
   * it into the array representing the seven possible addresses.
   *
   * @param adr address of device
   * @param msg VersionInq message from device
   */
  private void createCamera(int adr, byte[] msg) {

    // extract camera information from VISCA message
    String model = "0x" + ByteUtils.byteToHex(msg[4]) + ByteUtils.byteToHex(ByteUtils.low(msg[5]));
    String rom_ver = "0x" + ByteUtils.byteToHex(msg[6]) + ByteUtils.byteToHex(msg[7]);
    int sockets = msg[8];

    // try to find camera profile, if none found use default profile
    Properties profile;
    if (cameraProfiles.containsKey(model)) {
      profile = cameraProfiles.get(model);
    } else {
      profile = defaultProfile;
    }

    String modelStr = profile.getProperty("camera.vendor.name") + " " + profile.getProperty("camera.model.name");
    log.info("Registered " + modelStr + " (model ID " + model + ", ROM version " + rom_ver + ") at address #" + adr);

    // register camera
    VISCACameraImpl camera = new VISCACameraImpl(adr, sockets, rom_ver, profile);
    camera.parent = this;
    cameras[adr - 1] = camera;

    // register camera as OSGI service
    String camName = camera.model_name + " #" + adr;
    Dictionary props = new Hashtable();
    props.put("id", adr);
    props.put("name", camName);
    props.put("type", "VISCA");
    props.put("port", config.get(Constants.PROPKEY_PORT_DEVCICE));

    cc.getBundleContext().registerService(PTZCamera.class.getName(), camera, props);
  }

  /**
   * Called in the event of serial input. Parses the message and acts on the
   * content.
   *
   * @param arg0
   */
  @Override
  public void serialEvent(SerialPortEvent arg0) {
    int data;

    try {
      int len = 0;
      while ((data = commIn.read()) > -1) {
        buffer[len++] = (byte) data;
        if (data == VISCA.TERMINATOR) {
          break;
        }
      }

      // debug output
      if (debug) {
        log.debug(" <<" + ByteUtils.byteArrayToHex(buffer, len));
      }

      // did we receive an error?
      if (ByteUtils.high(buffer[1]) == 6) {
        handleError(buffer);
      } // did we receive ACK/Completion message?
      else if (len == 3) {
        handleACKCompletion(buffer);
      } // did we receive a position update?
      else if (len == 11 && buffer[1] == (byte) 0x50) {
        handlePositionUpdate(buffer);
      } // did we receive an ADDRESS_SET reply?
      else if (buffer[0] == (byte) 0x88 && buffer[1] == (byte) 0x30) {
        handleDiscoveryReply(buffer);
      } // did we receive a CAM_VersionInq reply?
      else if (buffer[1] == (byte) 0x50 && buffer[2] == (byte) 0x00 && len == 10) {
        handleCameraInfoReply(buffer);
      }

    } catch (IOException e) {
      log.error("Error while receiving data. ", e);
    }
  }

  private void handleError(byte[] message) {
    int adr = getAddress(message);
    String msg = "#" + adr + " gave error: ";
    switch (buffer[2]) {
      case 0x02:
        msg += "Syntax Error";
        break;
      case 0x03:
        msg += "Command Buffer Full";
        break;
      case 0x04:
        int socket = ByteUtils.low(buffer[1]);
        cameras[adr-1].setSocket(socket, null);
        msg += "Command Canceled (socket: " + socket + ")";
        break;
      case 0x05:
        msg += "No Socket (socket: " + ByteUtils.low(buffer[1]) + ")";
        break;
      case 0x41:
        msg += "Command Not Executable (socket: " + ByteUtils.low(buffer[1]) + ")";
        break;
      default:
        msg += "Unknown error code " + ByteUtils.byteToHex(buffer[2]);
    }
    log.warn(msg);
  }

  private void handleACKCompletion(byte[] buffer) {
    String msg = "";
    int adr = getAddress(buffer);
    VISCACameraImpl cam = cameras[adr - 1];
    int socket = ByteUtils.low(buffer[1]);
    if (ByteUtils.high(buffer[1]) == 4) {

      // set socket of camera used
      try {
        Message m;
        if ((m = cam.issuedMsg.poll()) != null) {
          cam.setSocket(socket, m);
        } else {
          throw new IllegalStateException("Received ACK for #" + adr + " but no command to be assigned.");
        }
      } catch (NullPointerException e) {
        log.error("Failed to set socket state on #" + adr + ", camera object not initialized.");
      }

      msg = "ACK";
    } else if (ByteUtils.high(buffer[1]) == 5) {

      // set socket of camera unused
      if (socket > 0) {
        try {
          cam.setSocket(socket, null);
        } catch (NullPointerException e) {
          log.error("Failed to set socket state on #" + adr + ", camera object not initialized.");
        }
      }

      msg = "Completion";
    }
    msg += " from #" + adr + " (socket: " + socket + ")";
    
    if (debug) {
      log.info(msg);    
    }
  }

  int last_pan = -1;
  int last_tilt = -1;

  private void handlePositionUpdate(byte[] buffer) {

    if (debug) {
    	log.debug("Received camera position update");
    }

    int adr = getAddress(buffer);
    int pan = ((buffer[2] & 0x0f) << 12) + ((buffer[3] & 0x0f) << 8) + ((buffer[4] & 0x0f) << 4) + (buffer[5] & 0x0f);
    int tlt = ((buffer[6] & 0x0f) << 12) + ((buffer[7] & 0x0f) << 8) + ((buffer[8] & 0x0f) << 4) + (buffer[9] & 0x0f);

    // this assumes that the camera uses the lower half of the 16bit value range
    // for right side and upper half for left side
    pan = pan > 0x7fff ? pan - 0xffff : pan;

    // same as above for tilt
    tlt = tlt > 0x7fff ? tlt - 0xffff : tlt;

    try {
      CameraPosition pos = cameras[adr - 1].state.currentPosition();

      if (last_pan != pan || last_tilt != tlt) {
	log.debug("Camera position updated: last_pan=" + last_pan + " last_tilt=" + last_tilt + " new pan=" + pan + " new tilt=" + tlt);
        last_pan = pan;
        last_tilt = tlt;
      }
      pos.set(pan, tlt);
      cameras[adr-1].notifyCameraListeners();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private void handleDiscoveryReply(byte[] msg) {
    int adr = buffer[2] - 1;
    log.info("Camera discovered on address " + adr);
    send_CamInfo(adr);
  }

  private void handleCameraInfoReply(byte[] msg) {
    int adr = getAddress(buffer);
    log.info("Received camera info from #" + adr);
    createCamera(adr, ByteUtils.trimArray(buffer, buffer.length));
  }

  /**
   * Threads that frequently sends inquiries to all registered cameras.
   *
   */
  class CameraStateUpdater implements Runnable {

    Message inq_msg = VISCA.INQ_PAN_TILT_POS.clone();

    @Override
    public void run() {

      try {
	      for (VISCACameraImpl cam : cameras) {
		if (cam != null) {
		  if (debug) {
		  	log.debug("Requesting camera position update");
		  }
		  inq_msg.getBytes()[0] = (byte) (VISCA.ADR_CAMERA_N + cam.address);
		  send(inq_msg.getBytes());
		}
	      }
      } catch (Exception e) {
	 throw new IllegalStateException("Exception running camera state updater", e);
      }
    }
  }

  /**
   * Thread that sends commands to the registered cameras.
   *
   */
  class CameraCommandSender implements Runnable {

    @Override
    public void run() {
      for (VISCACameraImpl cam : cameras) {
        Message msg;
        if (cam != null && cam.interfaceReady() && (msg = cam.pendingMsg.poll()) != null) {
          cam.issuedMsg.add(msg);
          send(msg.getBytes());
        }
      }
    }
  }

  //<editor-fold>
  /**
   * Maps data bit parameter from config value to enum value from gnu.io.
   *
   * @param i
   * @return
   */
  int mapDatabitsParam(int i) {
    switch (i) {
      case 5:
        return SerialPort.DATABITS_5;
      case 6:
        return SerialPort.DATABITS_6;
      case 7:
        return SerialPort.DATABITS_7;
      case 8:
        return SerialPort.DATABITS_8;
      default:
        throw new IllegalArgumentException("Data bist can only be 5, 6, 7, or 8.");
    }
  }

  /**
   * Maps the stop bits parameter from config value to enum value from gnu.io.
   *
   * @param i
   * @return
   */
  int mapStopbitsParam(String i) {
    if (i.equals("1")) {
      return SerialPort.STOPBITS_1;
    } else if (i.equals("1-5")) {
      return SerialPort.STOPBITS_1_5;
    } else if (i.equals("2")) {
      return SerialPort.STOPBITS_2;
    } else {
      throw new IllegalArgumentException("Stop bits can only be: '1', '2', '1-5'.");
    }
  }

  /**
   * Maps the parity parameter from the config value to enum value from gnu.io.
   *
   * @param i
   * @return
   */
  int mapParityParam(String i) {
    if (i.equals("even")) {
      return SerialPort.PARITY_EVEN;
    } else if (i.equals("mark")) {
      return SerialPort.PARITY_MARK;
    } else if (i.equals("none")) {
      return SerialPort.PARITY_NONE;
    } else if (i.equals("odd")) {
      return SerialPort.PARITY_ODD;
    } else if (i.equals("space")) {
      return SerialPort.PARITY_SPACE;
    } else {
      throw new IllegalArgumentException("Failed to parse parity parameter.");
    }
  }
  //</editor-fold>
}