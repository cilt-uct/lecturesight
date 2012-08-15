package cv.lecturesight.videoanalysis.foreground.impl;

import com.nativelibs4java.opencl.CLFloatBuffer;
import com.nativelibs4java.opencl.CLImage2D;
import com.nativelibs4java.opencl.CLIntBuffer;
import com.nativelibs4java.opencl.CLKernel;
import com.nativelibs4java.opencl.CLMem.Usage;
import com.nativelibs4java.opencl.CLQueue;
import cv.lecturesight.videoanalysis.backgroundmodel.BackgroundModel;
import cv.lecturesight.cca.ConnectedComponentLabeler;
import cv.lecturesight.cca.ConnectedComponentService;
import cv.lecturesight.videoanalysis.change.ChangeDetector;
import cv.lecturesight.videoanalysis.foreground.ForegroundService;
import cv.lecturesight.opencl.CLImageDoubleBuffer;
import cv.lecturesight.opencl.OpenCLService;
import cv.lecturesight.opencl.OpenCLService.Format;
import cv.lecturesight.opencl.api.ComputationRun;
import cv.lecturesight.opencl.api.OCLSignal;
import cv.lecturesight.opencl.api.OCLSignalBarrier;
import cv.lecturesight.ui.DisplayService;
import java.awt.image.BufferedImage;
import org.osgi.service.component.ComponentContext;
import cv.lecturesight.util.Log;
import cv.lecturesight.util.conf.Configuration;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.EnumMap;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;

/** Foreground Service Implementation
 * 
 * This foreground service constructs the foreground map by combining results
 * from the background subtraction and the inter-frame change detection. For
 * each pixel that changed since the last frame it is decided if it became fore-
 * ground or background, the corresponding pixel is added to or removed from the
 * foreground map accordingly. Foreground regions are labeled, small regions 
 * are removed. The number of pixels that changed in each blob is counted, if 
 * this number is below a threshold the blob is 'aged'. This way inactive blobs
 * disappear after a certain period of time.
 *
 */
@Component(name="lecturesight.foreground", immediate=true)
@Service()
@Properties({
@Property(name="osgi.command.scope", value="fg"),
@Property(name="osgi.command.function", value={"reset"})  
})
public class ForegroundServiceImpl implements ForegroundService {

  // collection of this services signals
  private EnumMap<ForegroundService.Signal, OCLSignal> signals =
          new EnumMap<ForegroundService.Signal, OCLSignal>(ForegroundService.Signal.class);
  
  private Log log = new Log("Foreground Service");
  
  @Reference
  private Configuration config;             // this services configuration
  
  @Reference
  private OpenCLService ocl;                // OpenCL service
  
  @Reference
  private DisplayService dsps;              // display service
  
  @Reference
  private BackgroundModel bgmodel;          // background model service
  
  @Reference
  private ChangeDetector changedetect;      // change detection service
  
  @Reference
  private ConnectedComponentService ccs;    // connected component analysis service
  
  private CLImage2D change;                 // output of change detection
  private CLImage2D bgdiff;                 // output of background subtraction
  private CLImage2D updateMap;              // working buffer indicating pixels to add/remove
  private CLImage2D fgUpdated;              // foreground map output buffer
  private CLImageDoubleBuffer fgBuffer;     // foreground map working buffer
  private CLIntBuffer activity;             // buffer for activity count of blobs
  private int[] activities;
  private BufferedImage fgMapHost;
  private CLFloatBuffer activity_ratio;

  private int[] workDim;                    // dimensions of buffers
  private ConnectedComponentLabeler ccl;    // connected component analyzer
  private OCLSignal ccl_START, ccl_DONE;    // connected component analyzer signals
  

  /** Activation method of this service. Sets up data structures, components
   *  and signals.
   * 
   * @param ComponentContext
   * @throws Exception 
   */
  protected void activate(ComponentContext cc) throws Exception {
    // create signals
    signals.put(Signal.DONE_ADDSUB, ocl.getSignal(Constants.SIGNAME_DONE_UPDATE));
    signals.put(Signal.DONE_CLEANING, ocl.getSignal(Constants.SIGNAME_DONE_CLEANING));

    // get input data pointers
    change = changedetect.getChangeMapDilated();
    bgdiff = bgmodel.getDifferenceMap();
    
    // input image dimensions
    workDim = new int[]{(int) change.getWidth(), (int) change.getHeight()};       

    // allocate working buffers
    updateMap = ocl.context().createImage2D(Usage.InputOutput, Format.BGRA_UINT8.getCLImageFormat(), workDim[0], workDim[1]);
    fgUpdated = ocl.context().createImage2D(Usage.InputOutput, Format.INTENSITY_UINT8.getCLImageFormat(), workDim[0], workDim[1]);
    fgBuffer = new CLImageDoubleBuffer(
            ocl.context().createImage2D(Usage.InputOutput, Format.INTENSITY_UINT8.getCLImageFormat(), workDim[0], workDim[1]),
            ocl.context().createImage2D(Usage.InputOutput, Format.INTENSITY_UINT8.getCLImageFormat(), workDim[0], workDim[1]));
    activity = ocl.context().createIntBuffer(Usage.InputOutput, config.getInt(Constants.PROPKEY_CCL_MAXBLOBS)+1);
    activity_ratio = ocl.context().createFloatBuffer(Usage.InputOutput, config.getInt(Constants.PROPKEY_CCL_MAXBLOBS)+1);
    activities = new int[config.getInt(Constants.PROPKEY_CCL_MAXBLOBS)+1];

    reset();    // initialize working buffers
    
    // create connected component analyzer on foreground map
    ccl = ccs.createLabeler(fgUpdated, config.getInt(Constants.PROPKEY_CCL_MAXBLOBS), 
            config.getInt(Constants.PROPKEY_CCL_MINSIZE), config.getInt(Constants.PROPKEY_CCL_MAXSIZE));
    ccl_START = ccl.getSignal(ConnectedComponentLabeler.Signal.START);
    ccl_DONE  = ccl.getSignal(ConnectedComponentLabeler.Signal.DONE);

    registerDisplays();

    // register UpdateRun to be launched when bgmodel and changedetect are done
    OCLSignalBarrier startBarrier = ocl.createSignalBarrier(new OCLSignal[]{
              changedetect.getSignal(ChangeDetector.Signal.DONE_DETECTION),
              bgmodel.getSignal(BackgroundModel.Signal.DONE_DIFF)
            });
    ocl.registerLaunch(startBarrier.getSignal(), new UpdateRun());
    ocl.registerLaunch(ccl_DONE, new MaskCleanRun());               // register cleaning run to be launched when CCA is done

    log.info("Activated");
  }

  //<editor-fold defaultstate="collapsed" desc="Display Registration">
  /** Register displays if configured 
   * 
   */
  private void registerDisplays() {
    // register update map display if configured
    if (config.getBoolean(Constants.PROPKEY_DISPLAY_UPDATEMAP)) {
      dsps.registerDisplay(Constants.WINDOWNAME_UPDATEMAP, "foreground update map",
              updateMap, signals.get(Signal.DONE_ADDSUB));
    }
    // register foreground map display if configured
    if (config.getBoolean(Constants.PROPKEY_DISPLAY_FOREGROUNDMAP)) {
      dsps.registerDisplay(Constants.WINDOWNAME_FOREGROUNDMAP, "foreground",
              fgUpdated, signals.get(Signal.DONE_CLEANING));
    }
  }
  //</editor-fold>

  //<editor-fold defaultstate="collapsed" desc="Getters and Setters">
  @Override
  public OCLSignal getSignal(Signal signal) {
    return signals.get(signal);
  }

  @Override
  public CLImage2D getUpdateMap() {
    return updateMap;
  }

  @Override
  public CLImage2D getForegroundMap() {
    return fgUpdated;
  }
  
  @Override
  public CLImageDoubleBuffer getForegroundWorkingBuffer() {
    return fgBuffer;
  }

  @Override
  public ConnectedComponentLabeler getLabeler() {
    return ccl;
  }
  
  @Override
  public BufferedImage getForegroundMapHost() {
    return fgMapHost;
  }
  
  public int getActivity(int id) {
    return activities[id];              // FIXME id+1 ???
  }
  //</editor-fold>
          
  /** Resets all working buffers of this service.
   * 
   */
  public void reset() {
    ocl.utils().setValues(0, 0, workDim[0], workDim[1], updateMap, 0);           
    ocl.utils().setValues(0, 0, workDim[0], workDim[1], fgUpdated, 0);
    ocl.utils().setValues(0, 0, workDim[0], workDim[1], (CLImage2D) fgBuffer.current(), 0);
    ocl.utils().setValues(0, 0, workDim[0], workDim[1], (CLImage2D) fgBuffer.last(), 0);
    log.info("Working buffers initialized");
  }

  /** Run that computes the update map and performs the update of the foreground
   *  map. This Run is launched by the signal barrier listening to the change
   *  detection and background subtraction. Casts signal DONE_ADDSUB on completion. 
   * 
   */
  private class UpdateRun implements ComputationRun {

    OCLSignal SIG_done = signals.get(Signal.DONE_ADDSUB);
    CLKernel computeAddSubMaskK = ocl.programs().getKernel("fg", "compute_add_sub_mask");   // kernel that computes the update map
    CLKernel updateForegroundK = ocl.programs().getKernel("fg", "update_foreground");       // kernel that performs for foreground update

    @Override
    public void launch(CLQueue queue) {
      fgBuffer.swap();                                          // swap working buffer pointers
      computeAddSubMaskK.setArgs(change, bgdiff, updateMap);    // compute update map
      computeAddSubMaskK.enqueueNDRange(queue, workDim);
      updateForegroundK.setArgs(updateMap, fgBuffer.last());    // perform foreground mask update
      updateForegroundK.enqueueNDRange(queue, workDim);
    }

    @Override
    public void land() {
      ccl.setInput((CLImage2D) fgBuffer.last());                // update input pointer for CCA
      ocl.castSignal(SIG_done);                                 // cast completion signal 
      ocl.castSignal(ccl_START);                                // trigger CCA
    }
  }

  /** Run that removes small blobs and ages inactive blobs in the foreground mask.
   *  This Run is launched when the connected component analysis is done. Casts
   *  signal DONE_CLEANING on completion. 
   * 
   */
  private class MaskCleanRun implements ComputationRun {

    OCLSignal SIG_done = signals.get(Signal.DONE_CLEANING);
    OCLSignal SIG_startBGUpdate = bgmodel.getSignal(BackgroundModel.Signal.DO_UPDATE);
    int[] bufferDim = new int[]{(int) activity.getElementCount()-1};                  // dimensions of the activity buffer
    CLKernel resetBuffer = ocl.programs().getKernel("fg", "reset_buffer");          // kernel that resets the activity buffer
    CLKernel fgRemoveSmallK = ocl.programs().getKernel("fg", "remove_smallblobs");  // kernel that removes small blobs
    CLKernel fgGatherActivity = ocl.programs().getKernel("fg", "gather_activity");  // kernel that computes the activity of blobs
    CLKernel fgActRatioK = ocl.programs().getKernel("fg", "compute_activity_ratios");
    CLKernel fgDecayK = ocl.programs().getKernel("fg", "refresh_decay");            // kernel that refrshes/ages blobs
    CLImage2D bgUpdateMask = bgmodel.getUpdateMap();
    IntBuffer activityH;
    FloatBuffer ratiosH;
    
    {
      resetBuffer.setArgs(activity, 0);     // parameters for reset kernel can be set once
    }

    @Override
    public void launch(CLQueue queue) {
      fgRemoveSmallK.setArgs(ccl.getLabelBuffer(), fgBuffer.last(), workDim[0], workDim[1]);              // remove small blobs
      fgRemoveSmallK.enqueueNDRange(queue, workDim);
      resetBuffer.enqueueNDRange(queue, bufferDim);                                                       // reset activity buffer
      fgGatherActivity.setArgs(ccl.getLabelBuffer(), activity, fgBuffer.last(), updateMap, workDim[0]);   // compute blob activity
      fgGatherActivity.enqueueNDRange(queue, workDim);
      fgActRatioK.setArgs(activity, ccl.getSizeBuffer(), activity_ratio);                                         // compute activity activity_ratio
      fgActRatioK.enqueueNDRange(queue, bufferDim);
      fgDecayK.setArgs(fgBuffer.last(), fgBuffer.current(), fgUpdated, ccl.getLabelBuffer(),              // refresh/age blobs
              activity_ratio, config.getFloat(Constants.PROPKEY_DECAY_THRESHRATIO), config.getInt(Constants.PROPKEY_DECAY_ALPHA), workDim[0]);
      fgDecayK.enqueueNDRange(queue, workDim);
      ocl.utils().copyImage(0, 0, workDim[0], workDim[1], fgUpdated, 0, 0, bgUpdateMask);
      fgMapHost = fgUpdated.read(queue);
      activityH = activity.read(queue);
//      ratiosH = activity_ratio.read(queue);
    }

    @Override
    public void land() {
      
//      activityH.get(activities, 0, activities.length);
//      for (int i = 1; i < 20; i++) {
//        System.out.print(activities[i] + " ");
//      }
//      System.out.println();
//      
//      for (int i = 1; i < 20; i++) {
//        System.out.print(ccl.getSize(i) + " ");
//      }
//      System.out.println();
//      
//      float[] ratiosA = new float[20];
//      ratiosH.get(ratiosA, 0, 20);
//      for (int i = 1; i < 20; i++) {
//        System.out.print(ratiosA[i] + " ");
//      }
//      
//      System.out.println("\n----------------------------------");
      
      ocl.castSignal(SIG_startBGUpdate);
      ocl.castSignal(SIG_done);     // cast completion signal
    }
  }
}
