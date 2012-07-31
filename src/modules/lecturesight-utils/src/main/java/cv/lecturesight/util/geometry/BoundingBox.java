package cv.lecturesight.util.geometry;

public class BoundingBox {

  private Position min, max;
  
  public BoundingBox() {
    this.min = new Position();
    this.max = new Position();
  }
  
  public BoundingBox(Position min, Position max) {
    this.min = min;
    this.max = max;
  }
  
  public int getWidth() {
    return max.getX() - min.getX();
  }
  
  public int getHeight() {
    return max.getY() - min.getY();
  }

  public Position getMin() {
    return min;
  }

  public void setMin(Position min) {
    this.min = min;
  }

  public Position getMax() {
    return max;
  }

  public void setMax(Position max) {
    this.max = max;
  }
 
  @Override
  public String toString() {
    return new StringBuilder()
            .append(min)
            .append(":")
            .append(max)
            .toString();
  }
}
