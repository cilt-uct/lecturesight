package cv.lecturesight.object;

/** Service API
 * 
 */
public interface ObjectService {
  
  enum Signal {
    DONE_COMPUTE_OVERLAP,
    DONE_CORRELATION,
    DONE_VISUAL
  }
  
  boolean isTracked(TrackerObject obj);
  TrackerObject getObject(int id);
  TrackerObject[] getAllObjects();
  TrackerObject[] getAllTrackedObjects();
  
}
