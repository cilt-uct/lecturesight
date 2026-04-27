package cv.lecturesight.vapix.service;

/**
 * Range of Float values
 */
public class FloatRange {

  protected float min;
  protected float max;

  public FloatRange() {
    min = 0f;
    max = 0f;
  }

  public FloatRange(float val) {
    min = val;
    max = val;
  }

  public FloatRange(float _min, float _max) {
    min = Math.min(_min, _max);
    max = Math.max(_min, _max);
  }

  /**
   * Return min value
   *
   */
  public float getMin() {
    return min;
  }

  /**
   * Set the min value
   *
   */
  public void setMin(float value) {
    this.min = value;
  }

  /**
   * Return max
   *
   */
  public float getMax() {
    return max;
  }

  /**
   * Set the max value
   *
   */
  public void setMax(float value) {
    if (value < this.min) {
      throw new IllegalArgumentException("max must be >= min");
    }
    this.max = value;
  }

  public String toString() {
    return new StringBuilder()
        .append("[")
        .append(min)
        .append(", ")
        .append(max)
        .append("]")
        .toString();
  }
}
