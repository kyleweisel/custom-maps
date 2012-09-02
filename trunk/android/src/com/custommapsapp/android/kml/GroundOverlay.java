/*
 * Copyright 2011 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.custommapsapp.android.kml;

import com.google.android.maps.GeoPoint;

import android.graphics.Matrix;
import android.graphics.Point;
import android.location.Location;
import android.util.FloatMath;

import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * GroundOverlay is a Java representation of the GroundOverlay tag used in KML.
 *
 * @author Marko Teittinen
 */
public class GroundOverlay implements Serializable {
  private static final long serialVersionUID = 1L;

  private KmlInfo kmlInfo;
  private String image;
  private String name;
  private String description;

  private float north;
  private float south;
  private float east;
  private float west;
  private float rotateAngle;

  private float[] northEastCornerLonLat;
  private float[] southEastCornerLonLat;
  private float[] southWestCornerLonLat;
  private float[] northWestCornerLonLat;

  private List<GroundOverlay.Tiepoint> tiepoints;

  private transient Matrix geoToMetric = null;
  private transient float[] metricSize = null;

  public KmlInfo getKmlInfo() {
    return kmlInfo;
  }
  public void setKmlInfo(KmlInfo kmlInfo) {
    this.kmlInfo = kmlInfo;
  }

  public String getImage() {
    return image;
  }
  public void setImage(String image) {
    this.image = image;
  }

  public String getName() {
    return name;
  }
  public void setName(String name) {
    this.name = name;
  }

  public String getDescription() {
    return description;
  }
  public void setDescription(String description) {
    this.description = description;
  }

  @SuppressWarnings("unchecked")
  public Iterable<GroundOverlay.Tiepoint> getTiepoints() {
    return (tiepoints != null ? tiepoints : Collections.EMPTY_LIST);
  }
  public void clearTiepoints() {
    if (tiepoints != null) {
      tiepoints.clear();
    }
  }
  /**
   * Adds a new tiepoint to this GroundOverlay, does not add duplicates.
   *
   * @return {@code true} if tiepoint was added (was not present already)
   */
  public boolean addTiepoint(GroundOverlay.Tiepoint tiepoint) {
    if (tiepoint == null) {
      throw new IllegalArgumentException("Null tiepoints are not allowed");
    }
    if (tiepoints == null) {
      tiepoints = new ArrayList<GroundOverlay.Tiepoint>();
    } else if (tiepoints.contains(tiepoint)) {
      return false;
    }
    return tiepoints.add(tiepoint);
  }
  /**
   * Removes a tiepoint from this GroundOverlay
   *
   * @return {@code true} if tiepoint was removed (existed in tiepoints)
   */
  public boolean removeTiepoint(GroundOverlay.Tiepoint tiepoint) {
    if (tiepoints == null) {
      return false;
    }
    return tiepoints.remove(tiepoint);
  }

  /**
   * @return approximate area covered by this map in km^2
   */
  public float computeAreaKm2() {
    float[] size = getMapImageSize();
    return (size[0] / 1000f) * (size[1] / 1000f);
  }

  public boolean hasCornerTiePoints() {
    return (northEastCornerLonLat != null && southEastCornerLonLat != null &&
        southWestCornerLonLat != null && northWestCornerLonLat != null);
  }

  public float getNorth() {
    return north;
  }
  public void setNorth(float north) {
    this.north = north;
    geoToMetric = null;
  }

  public float getSouth() {
    return south;
  }
  public void setSouth(float south) {
    this.south = south;
    geoToMetric = null;
  }

  public float getEast() {
    return east;
  }
  public void setEast(float east) {
    this.east = east;
    geoToMetric = null;
  }

  public float getWest() {
    return west;
  }
  public void setWest(float west) {
    this.west = west;
    geoToMetric = null;
  }

  public float getRotateAngle() {
    return rotateAngle;
  }
  public void setRotateAngle(float rotateAngle) {
    this.rotateAngle = rotateAngle;
    geoToMetric = null;
  }

  public float[] getNorthEastCornerLocation() {
    return northEastCornerLonLat;
  }
  public void setNorthEastCornerLocation(float longitude, float latitude) {
    northEastCornerLonLat = new float[] { longitude, latitude };
    geoToMetric = null;
  }

  public float[] getSouthEastCornerLocation() {
    return southEastCornerLonLat;
  }
  public void setSouthEastCornerLocation(float longitude, float latitude) {
    southEastCornerLonLat = new float[] { longitude, latitude };
    geoToMetric = null;
  }

  public float[] getSouthWestCornerLocation() {
    return southWestCornerLonLat;
  }
  public void setSouthWestCornerLocation(float longitude, float latitude) {
    southWestCornerLonLat = new float[] { longitude, latitude };
    geoToMetric = null;
  }

  public float[] getNorthWestCornerLocation() {
    return northWestCornerLonLat;
  }
  public void setNorthWestCornerLocation(float longitude, float latitude) {
    northWestCornerLonLat = new float[] { longitude, latitude };
    geoToMetric = null;
  }

  /**
   * Checks if a location is within GroundOverlay boundaries.
   *
   * @param longitude of the location to check
   * @param latitude of the location to check
   * @return {@code true} if the location is within GroundOverlays boundaries
   */
  public boolean contains(float longitude, float latitude) {
    // TODO: This method fails when the points span 180 longitude (or a pole)
    // First check the simplest (and possibly most common) case when map is not rotated at all
    if (!hasCornerTiePoints() && rotateAngle == 0.0f) {
      return (getWest() < longitude && longitude < getEast() &&
              getSouth() < latitude && latitude < getNorth());
    }
    // Rotated rectangle or one defined by tie points, compute matrix to map space
    if (geoToMetric == null) {
      initializeGeoToMetricMatrix();
    }
    // Map the given location to image space and check if it fits within the image area
    float[] location = { longitude, latitude };
    geoToMetric.mapPoints(location);
    return (0 < location[0] && location[0] < metricSize[0] &&
            0 < location[1] && location[1] < metricSize[1]);
  }

  /**
   * Finds the shortest distance from the given point to the map area. If the
   * given point is within the map boundaries, returns 0. The returned distance
   * is in meters.
   *
   * @param longitude of the location to check
   * @param latitude of the location to check
   * @return distance in meters from the point to the edge of the map, or 0 if
   *         the point is within the map boundaries
   */
  public float getDistanceFrom(float longitude, float latitude) {
    // Return 0 if user is within map area
    if (this.contains(longitude, latitude)) {
      return 0f;
    }
    // TODO: This initial implementation doesn't consider 180 longitude
    if (geoToMetric == null) {
      initializeGeoToMetricMatrix();
    }
    final int X = 0;
    final int Y = 1;
    float[] location = { longitude, latitude };
    geoToMetric.mapPoints(location);
    // Find minimum distance to the map rectangle
    if (location[X] < 0) {
      // User is past left edge of map
      if (location[Y] < 0) {
        // above and left: closest map point is (0, 0)
        return getGeometricDistance(location[X], location[Y]);
      } else if (location[Y] > metricSize[Y]) {
        // below and left: closest map point is (0, metricSize[Y])
        return getGeometricDistance(location[X], location[Y] - metricSize[Y]);
      } else {
        // straight left: straight distance to left edge
        return -location[X];
      }
    }
    if (metricSize[X] < location[X]) {
      // User is past right edge of map
      if (location[Y] < 0) {
        // above and right: closest map point is (metricSize[X], 0)
        return getGeometricDistance(location[X] - metricSize[X], location[Y]);
      } else if (location[Y] > metricSize[Y]) {
        // below and right: closest map point is (metricSize[X], metricSize[Y])
        return getGeometricDistance(location[X] - metricSize[X], location[Y] - metricSize[Y]);
      } else {
        // straight right: straight distance to right edge
        return location[X] - metricSize[X];
      }
    }
    // location[X] is within map area, get straight distance to closer edge
    if (location[Y] < 0) {
      // User is directly above map area
      return -location[Y];
    } else {
      // User is directly below map area
      return location[Y] - metricSize[Y];
    }
  }

  // Finds shortest distance from point (x, y) to line connecting points (x0, y0) and (x1, y1)
  public float getGeometricDistance(float xDiff, float yDiff) {
    return FloatMath.sqrt(xDiff * xDiff + yDiff * yDiff);
  }

  private void initializeGeoToMetricMatrix() {
    metricSize = getMapImageSize();
    float metricWidth = metricSize[0];
    float metricHeight = metricSize[1];
    float[] imageCorners = {
        0, 0,   metricWidth, 0,   metricWidth, metricHeight,   0, metricHeight
    };
    float[] geoPoints;
    if (hasCornerTiePoints()) {
      geoPoints = new float[] {
          northWestCornerLonLat[0], northWestCornerLonLat[1],
          northEastCornerLonLat[0], northEastCornerLonLat[1],
          southEastCornerLonLat[0], southEastCornerLonLat[1],
          southWestCornerLonLat[0], southWestCornerLonLat[1]
      };
    } else {
      geoPoints = new float[] {
          west, north,   east, north,   east, south,   west, south
      };
    }
    Matrix matrix = new Matrix();
    matrix.setPolyToPoly(geoPoints, 0, imageCorners, 0, 4);
    if (!hasCornerTiePoints() && getRotateAngle() != 0) {
      matrix.postRotate(getRotateAngle(), metricWidth / 2, metricHeight / 2);
    }
    geoToMetric = matrix;
  }

  // Returns the map image size (width, height) in meters
  private float[] getMapImageSize() {
    float width, height;
    if (!hasCornerTiePoints()) {
      Location upperLeft = createLocation(west, north);
      Location upperRight = createLocation(east, north);
      Location lowerLeft = createLocation(west, south);
      width = upperLeft.distanceTo(upperRight);
      height = upperLeft.distanceTo(lowerLeft);
    } else {
      // Map could be skewed (slightly), use average of edges
      Location upperLeft = createLocation(northWestCornerLonLat[0], northWestCornerLonLat[1]);
      Location upperRight = createLocation(northEastCornerLonLat[0], northEastCornerLonLat[1]);
      Location lowerLeft = createLocation(southWestCornerLonLat[0], southWestCornerLonLat[1]);
      Location lowerRight = createLocation(southEastCornerLonLat[0], southEastCornerLonLat[1]);
      width = (upperLeft.distanceTo(upperRight) + lowerLeft.distanceTo(lowerRight)) / 2;
      height = (upperLeft.distanceTo(lowerLeft) + upperRight.distanceTo(lowerRight)) / 2;
    }
    return new float[] { width, height };
  }

  private Location createLocation(float longitude, float latitude) {
    Location loc = new Location("tmp");
    loc.setLongitude(longitude);
    loc.setLatitude(latitude);
    return loc;
  }

  /**
   * GroundOverlays are considered equal if they are stored in the same file,
   * and they use the same image file.
   */
  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof GroundOverlay)) {
      return false;
    }
    GroundOverlay other = (GroundOverlay) obj;
    if (!this.getImage().equals(other.getImage())) {
      return false;
    }
    File file = getKmlInfo().getFile();
    File otherFile = other.getKmlInfo().getFile();
    return file.equals(otherFile);
  }

  @Override
  public String toString() {
    return String.format("GroundOverlay[name='%s', description=%s, image=%s, " +
        "north=%.6g, south=%.6g, east=%.6g, west=%.6g, rotation=%.6g] (%s)",
        name, description, image, north, south, east, west, rotateAngle, kmlInfo.toString());
  }

  // --------------------------------------------------------------------------
  // Tiepoint structure

  /**
   * Original tiepoints associated with tiepoint aligned maps. Ignored by
   * Google Earth.
   */
  public static class Tiepoint implements Serializable {
    private static final long serialVersionUID = 1L;

    private GeoPoint geoPoint;
    private Point imagePoint;

    public Tiepoint(GeoPoint geoPoint, Point imagePoint) {
      if (geoPoint == null || imagePoint == null) {
        throw new IllegalArgumentException("Null points are not allowed in Tiepoint");
      }
      this.geoPoint = geoPoint;
      this.imagePoint = imagePoint;
    }

    public GeoPoint getGeoPoint() {
      return geoPoint;
    }

    public void setGeoPoint(GeoPoint geoPoint) {
      if (geoPoint == null) {
        throw new IllegalArgumentException("Null points are not allowed in Tiepoint");
      }
      this.geoPoint = geoPoint;
    }

    public Point getImagePoint() {
      return imagePoint;
    }

    public void setImagePoint(Point imagePoint) {
      if (imagePoint == null) {
        throw new IllegalArgumentException("Null points are not allowed in Tiepoint");
      }
      this.imagePoint = imagePoint;
    }

    @Override
    public int hashCode() {
      return geoPoint.hashCode() ^ imagePoint.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
      if (!(obj instanceof GroundOverlay.Tiepoint)) {
        return false;
      }
      GroundOverlay.Tiepoint other = (GroundOverlay.Tiepoint) obj;
      return this.geoPoint.equals(other.geoPoint) && this.imagePoint.equals(other.imagePoint);
    }

    // --------------------------------------------------------------------------
    // Serializable implementation for Tiepoint

    private void writeObject(ObjectOutputStream out) throws IOException {
      out.writeInt(geoPoint.getLatitudeE6());
      out.writeInt(geoPoint.getLongitudeE6());
      out.writeInt(imagePoint.x);
      out.writeInt(imagePoint.y);
    }

    private void readObject(ObjectInputStream in) throws IOException {
      int latitudeE6 = in.readInt();
      int longitudeE6 = in.readInt();
      int x = in.readInt();
      int y = in.readInt();
      geoPoint = new GeoPoint(latitudeE6, longitudeE6);
      imagePoint = new Point(x, y);
    }
  }
}
