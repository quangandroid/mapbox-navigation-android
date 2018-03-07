package com.mapbox.services.android.navigation.v5.location;

import android.location.Location;
import android.support.annotation.NonNull;

import com.mapbox.geojson.Point;
import com.mapbox.turf.TurfConstants;
import com.mapbox.turf.TurfMeasurement;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

public class LocationValidator {

  private static final int TIME_THRESHOLD = 5000; // 5 seconds
  private static final int ACCURACY_THRESHOLD = 10; // percent
  private static final int VELOCITY_THRESHOLD = 200; // milliseconds

  private static LocationValidator instance;
  private Location lastValidLocation;

  private final CopyOnWriteArrayList<OnLocationLoggedListener> onLocationLoggedListeners
    = new CopyOnWriteArrayList<>();

  /**
   * Primary access method (using singleton pattern)
   *
   * @return LocationValidator
   */
  public static synchronized LocationValidator getInstance() {
    if (instance == null) {
      instance = new LocationValidator();
    }

    return instance;
  }

  private LocationValidator() {
  }

  public boolean isValidUpdate(@NonNull Location location) {
    // First update
    if (lastValidLocation == null) {
      lastValidLocation = location;
      return true;
    }

    LoggingLocation loggingLocation = new LoggingLocation();

    float currentAccuracy = location.getAccuracy();
    float previousAccuracy = lastValidLocation.getAccuracy();
    float accuracyDifference = Math.abs(previousAccuracy - currentAccuracy);

    loggingLocation.setAccuracy(String.format("Accuracy - Current: %s, Previous: %s, Difference: %s",
      currentAccuracy, previousAccuracy, accuracyDifference));

    boolean currentAccuracyWorse = currentAccuracy > previousAccuracy;
    boolean hasSameProvider = lastValidLocation.getProvider().equals(location.getProvider());
    boolean lessThanPercentThreshold = (accuracyDifference <= (previousAccuracy / ACCURACY_THRESHOLD));

    // New location update is acceptable, even with worse accuracy, if it is from
    // the same provider and is no more than 10% worse
    boolean worseAccuracyAcceptable = currentAccuracyWorse && hasSameProvider && lessThanPercentThreshold;

    loggingLocation.setWorseAccuracyAcceptable(String.format("Worse accuracy acceptable: %s", worseAccuracyAcceptable));

    // Calculate the velocity between these two points
    Point currentPoint = Point.fromLngLat(location.getLongitude(), location.getLatitude());
    Point previousValidPoint = Point.fromLngLat(lastValidLocation.getLongitude(), lastValidLocation.getLatitude());
    double distanceInMeters = TurfMeasurement.distance(previousValidPoint, currentPoint, TurfConstants.UNIT_METERS);

    loggingLocation.setDistanceBetweenUpdates(String.format("Distance between updates: %s", distanceInMeters));

    // Average velocity = distance traveled over time
    long timeSinceLastValidUpdate = location.getTime() - lastValidLocation.getTime();
    double velocity = distanceInMeters / timeSinceLastValidUpdate / 1000;

    loggingLocation.setTimeSinceLastUpdate(
      String.format("Time since last update: %s seconds", TimeUnit.MILLISECONDS.toSeconds(timeSinceLastValidUpdate))
    );
    loggingLocation.setVelocity(String.format("Velocity %s", velocity));

    boolean validVelocity = velocity <= VELOCITY_THRESHOLD;

    loggingLocation.setValidVelocity(String.format("Valid velocity: %s", validVelocity));

    // Location update is valid if it has better accuracy, has been over 5 seconds since the last update,
    // or the new update has worse accuracy but it is still acceptable
    boolean validLocation = currentAccuracy <= previousAccuracy
      || timeSinceLastValidUpdate > TIME_THRESHOLD
      || worseAccuracyAcceptable;

    loggingLocation.setValidLocation(String.format("Valid location: %s", validLocation));
    sendLoggedLocationUpdate(loggingLocation);

    // Valid velocity and location, set last valid location
    if (validVelocity && validLocation) {
      lastValidLocation = location;
      return true;
    }

    // Not a valid update
    return false;
  }

  public void addOnLocationLoggedListener(OnLocationLoggedListener listener) {
    onLocationLoggedListeners.add(listener);
  }

  public void removeOnLocationLoggedListener(OnLocationLoggedListener listener) {
    onLocationLoggedListeners.remove(listener);
  }

  public interface OnLocationLoggedListener {
    void onLocationLogged(LoggingLocation loggedLocation);
  }

  private void sendLoggedLocationUpdate(LoggingLocation loggingLocation) {
    for (OnLocationLoggedListener onLocationLoggedListener : onLocationLoggedListeners) {
      onLocationLoggedListener.onLocationLogged(loggingLocation);
    }
  }
}
