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
package cv.lecturesight.regiontracker;

import cv.lecturesight.util.geometry.BoundingBox;
import cv.lecturesight.util.geometry.Position;
import java.util.Set;

public interface Region {
  
  int getLabel();

  long getFirstSeenTime();
  
  long getLastMoveTime();
  
  BoundingBox getBoundingBox();

  Position getCentroid();
  
  int getWeight();
  
  boolean isSplitter();
  
  boolean isGroup();
  
  boolean isGroupMember();
  
  Region getGroup();
        
  Set<Region> getGroupMembers();

  void update(int label, long lastMoveTime, Position new_centroid, BoundingBox bbox1, int weight);

  void setAxis(double dist);

  double getAxis();
}
